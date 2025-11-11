package com.jetbrains.rider.plugins.sharpfocus.lsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class SharpFocusFocusService(private val project: Project) {

    private val logger = logger<SharpFocusFocusService>()

    companion object {
        fun getInstance(project: Project): SharpFocusFocusService {
            return project.getService(SharpFocusFocusService::class.java)
        }

        private const val FOCUS_MODE_METHOD = "sharpfocus/focusMode"
    }

    fun requestFocusMode(
        documentUri: String,
        line: Int,
        character: Int
    ): CompletableFuture<FocusModeResponse?> {
        val serverManager = SharpFocusLanguageServerManager.getInstance(project)

        if (!serverManager.isServerRunning()) {
            logger.warn("Language server not running, starting it first")
            return serverManager.start().thenCompose {
                performFocusRequest(documentUri, line, character)
            }
        }

        return performFocusRequest(documentUri, line, character)
    }

    private fun performFocusRequest(
        documentUri: String,
        line: Int,
        character: Int
    ): CompletableFuture<FocusModeResponse?> {
        val serverManager = SharpFocusLanguageServerManager.getInstance(project)

        if (!serverManager.isServerRunning()) {
            logger.error("Language server not running")
            return CompletableFuture.completedFuture(null)
        }

        val request = FocusModeRequest(
            textDocument = TextDocumentIdentifier(documentUri),
            position = Position(line, character)
        )

        return try {
            serverManager.focusMode(request)
                .thenApply { response ->
                    response?.also {
                        logger.debug("Focus mode response: ${it.focusedPlace.name}, ${it.relevantRanges.size} ranges")
                    }
                }
                .exceptionally { ex ->
                    logger.error("Focus mode request failed", ex)
                    null
                }
        } catch (e: Exception) {
            logger.error("Error sending focus mode request", e)
            CompletableFuture.completedFuture(null)
        }
    }
}
