package com.jetbrains.rider.plugins.sharpfocus.lsp

import com.intellij.openapi.Disposable
import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Manages the lifecycle of the SharpFocus Language Server.
 * Launches the existing SharpFocus.LanguageServer.dll via dotnet and
 * communicates using the Language Server Protocol.
 */
@Service(Service.Level.PROJECT)
class SharpFocusLanguageServerManager(private val project: Project) : Disposable {

    private val logger = logger<SharpFocusLanguageServerManager>()

    private var process: Process? = null
    private var languageServer: SharpFocusLanguageServerAPI? = null
    private var launcher: Launcher<SharpFocusLanguageServerAPI>? = null

    private val gson = Gson()

    @Volatile
    private var isStarting = false

    @Volatile
    private var isRunning = false

    companion object {
        fun getInstance(project: Project): SharpFocusLanguageServerManager {
            return project.getService(SharpFocusLanguageServerManager::class.java)
        }

        private const val SERVER_DLL_NAME = "SharpFocus.LanguageServer.dll"
        private const val SERVER_EXE_NAME = "SharpFocus.LanguageServer.exe"
    }

    /**
     * Starts the language server if not already running.
     */
    fun start(): CompletableFuture<Void> {
        if (isRunning) {
            logger.info("Language server already running")
            return CompletableFuture.completedFuture(null)
        }

        if (isStarting) {
            logger.warn("Language server is already starting")
            return CompletableFuture.completedFuture(null)
        }

        isStarting = true

        return CompletableFuture.supplyAsync {
            try {
                val serverPath = findServerPath()
                if (serverPath == null) {
                    logger.error("Could not find language server DLL")
                    throw IllegalStateException("Language server not found")
                }

                logger.info("Starting language server at: $serverPath")

                // Determine if we should use exe or dll
                // With self-contained single-file, we prefer the .exe on Windows
                val command = if (serverPath.endsWith(".exe")) {
                    listOf(serverPath)
                } else {
                    listOf("dotnet", serverPath)
                }

                // Launch the language server process
                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(false)

                process = processBuilder.start()

                // Log process output for debugging
                startProcessOutputLogger(process!!)

                // Create LSP launcher with custom server interface using Builder
                val client = SharpFocusLanguageClient()

                // Use the Launcher.Builder to create a launcher with custom server interface
                @Suppress("UNCHECKED_CAST")
                launcher = org.eclipse.lsp4j.jsonrpc.Launcher.createLauncher(
                    client,
                    SharpFocusLanguageServerAPI::class.java,
                    process!!.inputStream,
                    process!!.outputStream
                ) as Launcher<SharpFocusLanguageServerAPI>

                languageServer = launcher!!.remoteProxy

                // Start listening
                launcher!!.startListening()

                // Initialize the server
                val initParams = InitializeParams().apply {
                    processId = ProcessHandle.current().pid().toInt()
                    rootUri = project.basePath?.let { "file://$it" }
                    capabilities = ClientCapabilities().apply {
                        textDocument = TextDocumentClientCapabilities()
                        workspace = WorkspaceClientCapabilities().apply {
                            applyEdit = true
                            workspaceEdit = WorkspaceEditCapabilities().apply {
                                documentChanges = true
                            }
                        }
                    }
                }

                val initResult = languageServer!!.initialize(initParams).get(30, TimeUnit.SECONDS)
                logger.info("Language server initialized: ${initResult.serverInfo?.name}")

                languageServer!!.initialized(InitializedParams())

                isRunning = true
                logger.info("Language server started successfully")

            } catch (e: Exception) {
                logger.error("Failed to start language server", e)
                cleanup()
                throw e
            } finally {
                isStarting = false
            }
            null
        }
    }

    /**
     * Stops the language server.
     */
    fun stop(): CompletableFuture<Void> {
        if (!isRunning) {
            return CompletableFuture.completedFuture(null)
        }

        return CompletableFuture.runAsync {
            try {
                logger.info("Stopping language server")

                languageServer?.shutdown()?.get(5, TimeUnit.SECONDS)
                languageServer?.exit()

                process?.destroy()
                process?.waitFor(5, TimeUnit.SECONDS)

                if (process?.isAlive == true) {
                    logger.warn("Force killing language server process")
                    process?.destroyForcibly()
                }

                logger.info("Language server stopped")
            } catch (e: Exception) {
                logger.error("Error stopping language server", e)
            } finally {
                cleanup()
            }
        }
    }

    /**
     * Restarts the language server.
     */
    fun restart(): CompletableFuture<Void> {
        return stop().thenCompose { start() }
    }

    /**
     * Gets the language server instance.
     */
    fun getServer(): SharpFocusLanguageServerAPI? = languageServer

    /**
     * Gets the launcher instance for custom requests.
     */
    fun getLauncher(): Launcher<SharpFocusLanguageServerAPI>? = launcher

    /**
     * Execute a command on the language server using the standard LSP workspace/executeCommand method.
     * This is the standard way to extend LSP functionality and is properly supported by LSP4J.
     *
     * @param command The command identifier (e.g., "sharpfocus.focusMode")
     * @param arguments The command arguments
     * @return CompletableFuture with the response, or null if server not running
     */
    fun executeCommand(command: String, arguments: List<Any>): CompletableFuture<Any?> {
        if (!isRunning || languageServer == null) {
            logger.warn("Cannot execute command: server not running")
            return CompletableFuture.completedFuture(null)
        }

        try {
            logger.info("Executing command: $command with ${arguments.size} arguments")

            val params = ExecuteCommandParams().apply {
                this.command = command
                this.arguments = arguments
            }

            val future = languageServer!!.workspaceService.executeCommand(params)

            future.thenApply { response ->
                logger.info("Received command response: ${response?.javaClass?.name ?: "null"}")
                if (response != null) {
                    logger.info("Response content: $response")
                }
                response
            }

            return future
        } catch (e: Exception) {
            logger.error("Error executing command", e)
            return CompletableFuture.completedFuture(null)
        }
    }

    /**
     * Sends a custom LSP request using the typed API method.
     *
     * @param request The focus mode request parameters
     * @return CompletableFuture with the response parsed as FocusModeResponse
     */
    fun focusMode(request: FocusModeRequest): CompletableFuture<FocusModeResponse?> {
        if (!isRunning || languageServer == null) {
            logger.warn("Cannot send focus mode request: server not running")
            return CompletableFuture.completedFuture(null)
        }

        return try {
            logger.info(
                "Sending focus mode request for ${request.textDocument.uri} @ ${request.position.line}:${request.position.character}"
            )

            // Use the typed API method directly - LSP4J will handle serialization/deserialization
            languageServer!!.focusMode(request)
                .thenApply { response ->
                    if (response == null) {
                        logger.warn("Focus mode response was null")
                        return@thenApply null
                    }

                    logger.info("=== FOCUS MODE RESPONSE RECEIVED ===")
                    logger.info("Focused place: ${response.focusedPlace.name} (kind: ${response.focusedPlace.kind})")
                    logger.info("  Position: line ${response.focusedPlace.range.start.line}:${response.focusedPlace.range.start.character}")
                    logger.info("Relevant ranges: ${response.relevantRanges.size}")
                    response.relevantRanges.forEachIndexed { index, range ->
                        logger.info("  [$index] line ${range.start.line}:${range.start.character} to ${range.end.line}:${range.end.character}")
                    }
                    logger.info("Container ranges: ${response.containerRanges.size}")

                    if (response.backwardSlice != null) {
                        val bs = response.backwardSlice!!
                        logger.info("Backward slice: ${bs.sliceRanges.size} ranges, ${bs.sliceRangeDetails?.size ?: 0} details")
                        bs.sliceRangeDetails?.forEachIndexed { index, detail ->
                            logger.info("  [$index] ${detail.relation} - ${detail.place.name} at line ${detail.range.start.line}:${detail.range.start.character}")
                        }
                    } else {
                        logger.info("Backward slice: null")
                    }

                    if (response.forwardSlice != null) {
                        val fs = response.forwardSlice!!
                        logger.info("Forward slice: ${fs.sliceRanges.size} ranges, ${fs.sliceRangeDetails?.size ?: 0} details")
                        fs.sliceRangeDetails?.forEachIndexed { index, detail ->
                            logger.info("  [$index] ${detail.relation} - ${detail.place.name} at line ${detail.range.start.line}:${detail.range.start.character}")
                        }
                    } else {
                        logger.info("Forward slice: null")
                    }
                    logger.info("=== END RESPONSE ===")

                    response
                }.exceptionally { ex ->
                    logger.error("Focus mode request failed", ex)
                    null
                }
        } catch (e: Exception) {
            logger.error("Error sending focus mode request", e)
            CompletableFuture.completedFuture(null)
        }
    }

    /**
     * Checks if the server is running.
     */
    fun isServerRunning(): Boolean = isRunning

    /**
     * Starts a background thread to log process output for debugging.
     */
    private fun startProcessOutputLogger(process: Process) {
        // Log stderr in a separate thread
        Thread {
            try {
                process.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            logger.warn("[LS stderr] $line")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.debug("Error reading process stderr: ${e.message}")
            }
        }.apply {
            isDaemon = true
            name = "SharpFocus-LS-stderr"
            start()
        }

        // Also capture stdout for diagnostics (separate from LSP communication)
        // Note: We read from inputStream for LSP, but we can peek at it before that
        logger.info("Language server process started (PID: ${process.pid()})")
    }

    /**
     * Extracts the bundled language server from plugin resources to a temporary directory.
     * The server is bundled as a self-contained single-file executable, so we only need
     * to extract the main executable (.exe on Windows, .dll on other platforms).
     *
     * @param platform The target platform (e.g., "win-x64", "linux-x64")
     * @return Path to the extracted executable, or null if extraction failed
     */
    private fun extractBundledServer(platform: String): String? {
        try {
            // Create temp directory for this extraction
            val tmpDir = Files.createTempDirectory("sharpfocus-server-$platform")
            logger.info("Created extraction directory: $tmpDir")

            // Determine which file to extract based on platform
            // With self-contained single-file publishing:
            // - Windows: SharpFocus.LanguageServer.exe (standalone executable)
            // - Linux/Mac: SharpFocus.LanguageServer (executable) or .dll
            val executableName = when {
                platform.contains("win") -> SERVER_EXE_NAME
                else -> SERVER_DLL_NAME  // On Unix, the self-contained binary might still be .dll
            }

            val resourcePath = "/server/$platform/$executableName"
            logger.info("Looking for bundled server at: $resourcePath")

            // Try to extract the main executable from resources
            val classLoader = this::class.java.classLoader
            val stream = classLoader.getResourceAsStream("server/$platform/$executableName")

            if (stream == null) {
                logger.warn("Bundled server not found at: $resourcePath")
                // Try alternate name for Unix platforms
                if (!platform.contains("win")) {
                    val altStream = classLoader.getResourceAsStream("server/$platform/$SERVER_EXE_NAME")
                    if (altStream != null) {
                        logger.info("Found alternate server executable")
                        val target = tmpDir.resolve(SERVER_EXE_NAME)
                        Files.copy(altStream, target, StandardCopyOption.REPLACE_EXISTING)
                        altStream.close()

                        // Make executable on Unix
                        if (!platform.contains("win")) {
                            target.toFile().setExecutable(true)
                        }

                        logger.info("Extracted bundled server to: $target")
                        return target.toAbsolutePath().toString()
                    }
                }
                return null
            }

            // Extract the executable
            val target = tmpDir.resolve(executableName)
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
            stream.close()

            // Make executable on Unix platforms
            if (!platform.contains("win")) {
                target.toFile().setExecutable(true)
            }

            logger.info("Extracted bundled server to: $target")

            // Verify the file exists and has content
            if (!Files.exists(target) || Files.size(target) == 0L) {
                logger.error("Extracted file is empty or doesn't exist: $target")
                return null
            }

            val sizeMB = Files.size(target) / (1024.0 * 1024.0)
            logger.info("Server executable size: ${String.format("%.2f", sizeMB)} MB")

            return target.toAbsolutePath().toString()

        } catch (e: Exception) {
            logger.error("Failed to extract bundled server", e)
            return null
        }
    }

    /**
     * Recursively extracts files from a directory path to a target directory.
     * @deprecated No longer needed with single-file publishing
     */
    @Deprecated("Single-file publishing no longer requires directory extraction")
    private fun extractDirectory(
        sourcePath: java.nio.file.Path,
        targetDir: java.nio.file.Path,
        classLoader: ClassLoader,
        platform: String
    ): Int {
        var count = 0

        try {
            Files.walk(sourcePath).use { paths ->
                paths.forEach { source ->
                    if (Files.isRegularFile(source)) {
                        val relativePath = sourcePath.relativize(source)
                        val target = targetDir.resolve(relativePath.toString())

                        // Create parent directories if needed
                        Files.createDirectories(target.parent)

                        // Copy file
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                        count++

                        if (count <= 5) {
                            logger.info("  Extracted: ${relativePath.fileName}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error walking directory tree: ${e.message}")

            // Fallback: try to extract known files from resources using classLoader
            val knownFiles = listOf(
                SERVER_DLL_NAME,
                "SharpFocus.Core.dll",
                "SharpFocus.Analysis.dll",
                "Microsoft.CodeAnalysis.dll",
                "Microsoft.CodeAnalysis.CSharp.dll",
                "Microsoft.CodeAnalysis.CSharp.Workspaces.dll",
                "Microsoft.CodeAnalysis.Workspaces.dll",
                "OmniSharp.Extensions.LanguageServer.dll",
                "OmniSharp.Extensions.JsonRpc.dll",
                "OmniSharp.Extensions.LanguageProtocol.dll",
                "Newtonsoft.Json.dll",
                "MediatR.dll",
                "System.Reactive.dll",
                "SharpFocus.LanguageServer.exe",
                "SharpFocus.LanguageServer.deps.json",
                "SharpFocus.LanguageServer.runtimeconfig.json"
            )

            for (fileName in knownFiles) {
                try {
                    val resourcePath = "server/$platform/$fileName"
                    val stream = classLoader.getResourceAsStream(resourcePath)
                    if (stream != null) {
                        val target = targetDir.resolve(fileName)
                        Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
                        stream.close()
                        count++
                        if (count <= 5) {
                            logger.info("  Extracted: $fileName")
                        }
                    }
                } catch (ex: Exception) {
                    // File might not exist, continue
                }
            }
        }

        if (count > 5) {
            logger.info("  ... and ${count - 5} more files")
        }

        return count
    }

    private fun cleanup() {
        isRunning = false
        languageServer = null
        launcher = null
        process = null
    }

    /**
     * Finds the language server DLL path.
     * Searches in multiple locations:
     * 1. Custom path from settings
     * 2. Plugin bundle (for production use)
     * 3. Development paths (for local development)
     */
    private fun findServerPath(): String? {
        val possiblePaths = mutableListOf<String>()

        // 0. Check settings for custom path
        val settings = com.jetbrains.rider.plugins.sharpfocus.settings.SharpFocusSettings.getInstance(project)
        if (settings.serverPath.isNotEmpty()) {
            val customPath = settings.serverPath
            logger.info("Using custom server path from settings: $customPath")
            if (File(customPath).exists()) {
                return customPath
            } else {
                logger.warn("Custom server path does not exist: $customPath")
            }
        }

        // 1. Look in plugin directory (production)
        val pluginPath = System.getProperty("idea.plugins.path")
        if (pluginPath != null) {
            possiblePaths.add(Paths.get(pluginPath, "SharpFocus", "server", SERVER_DLL_NAME).toString())
        }

        // 2. Look relative to project root (development)
        project.basePath?.let { basePath ->
            // Try both net10.0 and net8.0 for compatibility
            possiblePaths.add(Paths.get(basePath, "src", "SharpFocus.LanguageServer", "bin", "Release", "net10.0", SERVER_DLL_NAME).toString())
            possiblePaths.add(Paths.get(basePath, "src", "SharpFocus.LanguageServer", "bin", "Debug", "net10.0", SERVER_DLL_NAME).toString())
            possiblePaths.add(Paths.get(basePath, "src", "SharpFocus.LanguageServer", "bin", "Release", "net8.0", SERVER_DLL_NAME).toString())
            possiblePaths.add(Paths.get(basePath, "src", "SharpFocus.LanguageServer", "bin", "Debug", "net8.0", SERVER_DLL_NAME).toString())

            // Look in parent directory (if Rider opened rider-plugin folder)
            val parentPath = File(basePath).parent
            if (parentPath != null) {
                possiblePaths.add(Paths.get(parentPath, "src", "SharpFocus.LanguageServer", "bin", "Release", "net10.0", SERVER_DLL_NAME).toString())
                possiblePaths.add(Paths.get(parentPath, "src", "SharpFocus.LanguageServer", "bin", "Debug", "net10.0", SERVER_DLL_NAME).toString())
                possiblePaths.add(Paths.get(parentPath, "src", "SharpFocus.LanguageServer", "bin", "Release", "net8.0", SERVER_DLL_NAME).toString())
                possiblePaths.add(Paths.get(parentPath, "src", "SharpFocus.LanguageServer", "bin", "Debug", "net8.0", SERVER_DLL_NAME).toString())
            }
        }

        logger.info("Searching for language server in:")
        for (path in possiblePaths) {
            logger.info("  - $path")
            if (File(path).exists()) {
                logger.info("Found language server at: $path")
                return path
            }
        }

        // If we didn't find a filesystem copy, try to load the bundled server from plugin resources.
        // The server is published as a self-contained single-file executable bundled in plugin resources.
        try {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            val platform = when {
                os.contains("win") -> "win-x64"
                os.contains("mac") || os.contains("darwin") -> if (arch.contains("aarch64") || arch.contains("arm")) "osx-arm64" else "osx-x64"
                else -> "linux-x64"
            }

            logger.info("Looking for bundled language server for platform: $platform")

            // Extract the self-contained executable from resources
            val extractedPath = extractBundledServer(platform)
            if (extractedPath != null) {
                logger.info("Successfully extracted bundled language server to: $extractedPath")
                return extractedPath
            } else {
                logger.warn("Bundled language server resources not found for platform: $platform")
            }
        } catch (e: Exception) {
            logger.warn("Error while attempting to extract bundled language server resource", e)
        }

        logger.error("Language server not found in any of the searched paths")
        return null
    }

    override fun dispose() {
        stop().get(10, TimeUnit.SECONDS)
    }
}
