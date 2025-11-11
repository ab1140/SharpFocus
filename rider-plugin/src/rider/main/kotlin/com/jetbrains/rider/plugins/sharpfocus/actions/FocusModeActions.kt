package com.jetbrains.rider.plugins.sharpfocus.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.jetbrains.rider.plugins.sharpfocus.highlighting.DisplayMode
import com.jetbrains.rider.plugins.sharpfocus.highlighting.SharpFocusHighlighter
import com.jetbrains.rider.plugins.sharpfocus.lsp.SharpFocusFocusService

class FocusModeAction : AnAction("Show Focus Mode") {

    private val logger = logger<FocusModeAction>()
    private val highlighter = SharpFocusHighlighter()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return

        if (file.fileType.name != "C#") {
            return
        }

        val caretModel = editor.caretModel
        val offset = caretModel.offset
        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val column = offset - lineStartOffset

        val documentUri = file.virtualFile.url

        val focusService = SharpFocusFocusService.getInstance(project)
        focusService.requestFocusMode(documentUri, lineNumber, column)
            .thenAccept { response ->
                if (response == null) {
                    logger.warn("No focus mode response received")
                    highlighter.clearHighlights(editor)
                    return@thenAccept
                }

                val displayMode = getDisplayMode(project)
                highlighter.highlightFlows(editor, response, displayMode, project)

                val navigationManager = com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager.getInstance(project)
                navigationManager.initialize(file.virtualFile.path, response)
            }
            .exceptionally { ex ->
                logger.error("Focus mode request failed", ex)
                highlighter.clearHighlights(editor)
                null
            }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.PSI_FILE)

        event.presentation.isEnabledAndVisible =
            project != null &&
            editor != null &&
            file != null &&
            file.fileType.name == "C#"
    }

    private fun getDisplayMode(project: com.intellij.openapi.project.Project): DisplayMode {
        val settings = com.jetbrains.rider.plugins.sharpfocus.settings.SharpFocusSettings.getInstance(project)
        return settings.displayMode
    }
}

class ClearFocusModeAction : AnAction("Clear Focus Mode") {

    private val highlighter = SharpFocusHighlighter()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

        highlighter.clearHighlights(editor)

        val navigationManager = com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager.getInstance(project)
        navigationManager.clear()
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor != null
    }
}
