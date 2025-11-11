package com.jetbrains.rider.plugins.sharpfocus.listeners

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.jetbrains.rider.plugins.sharpfocus.highlighting.DisplayMode
import com.jetbrains.rider.plugins.sharpfocus.highlighting.SharpFocusHighlighter
import com.jetbrains.rider.plugins.sharpfocus.lsp.SharpFocusFocusService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class FocusModeCaretListener(private val project: Project) : CaretListener {

    private val logger = logger<FocusModeCaretListener>()
    private val highlighter = SharpFocusHighlighter()

    @Volatile
    private var pendingRequest: CompletableFuture<*>? = null

    @Volatile
    private var lastAnalysisTime = 0L

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
        private const val MIN_ANALYSIS_INTERVAL_MS = 200L
    }

    override fun caretPositionChanged(event: CaretEvent) {
        val editor = event.editor

        val navigationManager = com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager.getInstance(project)
        if (navigationManager.isNavigationInProgress()) {
            return
        }

        val settings = com.jetbrains.rider.plugins.sharpfocus.settings.SharpFocusSettings.getInstance(project)
        if (settings.analysisMode != com.jetbrains.rider.plugins.sharpfocus.settings.AnalysisMode.FOCUS) {
            return
        }

        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return

        if (file.fileType.name != "C#") {
            return
        }

        pendingRequest?.cancel(false)

        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < MIN_ANALYSIS_INTERVAL_MS) {
            return
        }

        val caretPosition = com.intellij.openapi.application.ReadAction.compute<Triple<Int, Int, String>?, RuntimeException> {
            val caret = event.caret ?: return@compute null
            val document = editor.document
            val offset = caret.offset
            val lineNumber = document.getLineNumber(offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            val column = offset - lineStartOffset
            val documentUri = file.url
            Triple(lineNumber, column, documentUri)
        } ?: return

        val (lineNumber, column, documentUri) = caretPosition

        pendingRequest = CompletableFuture.runAsync({
            Thread.sleep(DEBOUNCE_DELAY_MS)

            lastAnalysisTime = System.currentTimeMillis()

            val focusService = SharpFocusFocusService.getInstance(project)
            focusService.requestFocusMode(documentUri, lineNumber, column)
                .thenAccept { response ->
                    if (response == null) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            if (!editor.isDisposed) {
                                highlighter.clearHighlights(editor)
                            }
                        }
                        return@thenAccept
                    }

                    val displayMode = getDisplayMode()

                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!editor.isDisposed) {
                            highlighter.highlightFlows(editor, response, displayMode, project)

                            val navigationManager = com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager.getInstance(project)
                            navigationManager.initialize(file.path, response)
                        }
                    }
                }
                .exceptionally { ex ->
                    logger.debug("Focus mode request failed: ${ex.message}")
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!editor.isDisposed) {
                            highlighter.clearHighlights(editor)

                            val navigationManager = com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager.getInstance(project)
                            navigationManager.clear()
                        }
                    }
                    null
                }
        }).orTimeout(10, TimeUnit.SECONDS)
            .exceptionally { ex ->
                if (ex !is java.util.concurrent.CancellationException) {
                    logger.warn("Focus mode analysis timeout or error", ex)
                }
                null
            }
    }

    private fun getDisplayMode(): DisplayMode {
        val settings = com.jetbrains.rider.plugins.sharpfocus.settings.SharpFocusSettings.getInstance(project)
        return settings.displayMode
    }
}
