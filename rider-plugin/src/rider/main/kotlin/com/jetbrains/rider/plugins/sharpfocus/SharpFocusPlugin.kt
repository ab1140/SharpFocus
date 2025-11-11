package com.jetbrains.rider.plugins.sharpfocus

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.rider.plugins.sharpfocus.listeners.FocusModeCaretListener
import com.jetbrains.rider.plugins.sharpfocus.lsp.SharpFocusLanguageServerManager
import com.jetbrains.rider.plugins.sharpfocus.settings.AnalysisMode

/**
 * Entry point for the SharpFocus Rider plugin.
 *
 * SharpFocus implements information-flow analysis for C# using program slicing.
 * It helps developers understand data dependencies by highlighting code paths
 * that influence or are influenced by a selected variable.
 *
 * This plugin uses the existing SharpFocus Language Server (LSP) to perform
 * all analysis, making it consistent with the VS Code extension.
 */
@Service(Service.Level.PROJECT)
class SharpFocusPlugin(private val project: Project) {

    private val logger = logger<SharpFocusPlugin>()
    private var caretListener: FocusModeCaretListener? = null

    // Store the original setting state so we can restore it
    private var originalIdentifierHighlightingSetting: Boolean? = null

    companion object {
        const val PLUGIN_ID = "com.rahultr.sharpfocus"

        fun getInstance(project: Project): SharpFocusPlugin {
            return project.getService(SharpFocusPlugin::class.java)
        }
    }

    /**
     * Initialize the plugin - starts the language server and sets up listeners.
     */
    fun initialize() {
        logger.info("Initializing SharpFocus plugin for project: ${project.name}")

        // Start the language server
        val serverManager = SharpFocusLanguageServerManager.getInstance(project)
        serverManager.start().thenAccept {
            logger.info("SharpFocus language server started successfully")

            // Start document synchronization
            val synchronizer = project.getService(com.jetbrains.rider.plugins.sharpfocus.lsp.TextDocumentSynchronizer::class.java)
            synchronizer.start()
            logger.info("Text document synchronizer started")

            // Set up caret listener for automatic focus mode
            setupCaretListener()
        }.exceptionally { ex ->
            logger.error("Failed to start SharpFocus language server", ex)
            null
        }
    }

    /**
     * Sets up the caret listener for automatic focus mode on cursor movement.
     */
    private fun setupCaretListener() {
        caretListener = FocusModeCaretListener(project)
        EditorFactory.getInstance().eventMulticaster.addCaretListener(caretListener!!, project)
        logger.info("Focus mode caret listener registered")

        // Manage identifier highlighting based on focus mode state
        updateIdentifierHighlighting()
    }

    /**
     * Disables or enables Rider's built-in "Highlight usages of element at caret" feature
     * based on whether focus mode is active.
     *
     * TODO: Find the correct API to disable identifier highlighting programmatically
     */
    fun updateIdentifierHighlighting() {
        val settings = com.jetbrains.rider.plugins.sharpfocus.settings.SharpFocusSettings.getInstance(project)

        // TODO: Implement this when we find the correct API
        // For now, users need to manually disable it in Settings > Editor > General > Highlight usages of element at caret

        logger.info("Identifier highlighting management not yet implemented - analysis mode: ${settings.analysisMode}")
    }

    /**
     * Cleanup when plugin is disposed.
     */
    fun dispose() {
        logger.info("SharpFocus plugin disposed")
    }
}
