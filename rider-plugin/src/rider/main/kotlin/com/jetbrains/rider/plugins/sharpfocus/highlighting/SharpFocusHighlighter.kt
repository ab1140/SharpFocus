package com.jetbrains.rider.plugins.sharpfocus.highlighting

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.jetbrains.rider.plugins.sharpfocus.lsp.FocusModeResponse
import com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRangeInfo
import com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRelation
import com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowTreeToolWindowFactory
import java.awt.Color
import java.awt.Font
import javax.swing.Icon

class SharpFocusHighlighter {

    private val logger = logger<SharpFocusHighlighter>()
    private val highlighters = mutableMapOf<Editor, MutableList<RangeHighlighter>>()
    private var onHighlightCallback: ((String, FocusModeResponse) -> Unit)? = null

    fun onHighlight(callback: (String, FocusModeResponse) -> Unit) {
        onHighlightCallback = callback
    }

    companion object {
        private const val LAYER_FADE = HighlighterLayer.SELECTION - 1
        private const val LAYER_FOCUS = HighlighterLayer.SELECTION
        private const val LAYER_SEED = HighlighterLayer.SELECTION + 1

        private val NORMAL_FADE_FOREGROUND = JBColor(Color(128, 128, 128), Color(128, 128, 128))

        private val SEED_BG_COLOR = JBColor(Color(255, 215, 0, 56), Color(255, 215, 0, 46))
        private val SEED_BORDER_COLOR = JBColor(Color(255, 215, 0, 230), Color(255, 215, 0, 230))

        private val SOURCE_BG_COLOR = JBColor(Color(255, 140, 0, 38), Color(255, 140, 0, 31))
        private val SOURCE_BORDER_COLOR = JBColor(Color(255, 140, 0, 179), Color(255, 140, 0, 179))

        private val TRANSFORM_BG_COLOR = JBColor(Color(200, 162, 255, 38), Color(200, 162, 255, 31))
        private val TRANSFORM_BORDER_COLOR = JBColor(Color(200, 162, 255, 179), Color(200, 162, 255, 179))

        private val SINK_BG_COLOR = JBColor(Color(64, 224, 208, 38), Color(64, 224, 208, 31))
        private val SINK_BORDER_COLOR = JBColor(Color(0, 206, 209, 179), Color(0, 206, 209, 179))

        private val RELATED_BG_COLOR = JBColor(Color(0, 0, 0, 13), Color(255, 255, 255, 20))
        private val RELATED_BORDER_COLOR = JBColor(Color(128, 128, 128, 102), Color(128, 128, 128, 102))

        private val SEED_ICON: Icon by lazy { IconLoader.getIcon("/icons/seed-icon.svg", SharpFocusHighlighter::class.java) }
        private val SOURCE_ICON: Icon by lazy { IconLoader.getIcon("/icons/source-icon.svg", SharpFocusHighlighter::class.java) }
        private val TRANSFORM_ICON: Icon by lazy { IconLoader.getIcon("/icons/transform-icon.svg", SharpFocusHighlighter::class.java) }
        private val SINK_ICON: Icon by lazy { IconLoader.getIcon("/icons/sink-icon.svg", SharpFocusHighlighter::class.java) }
    }

    private class FlowGutterIconRenderer(private val icon: Icon, private val tooltip: String) : GutterIconRenderer() {
        override fun getIcon(): Icon = icon
        override fun getTooltipText(): String = tooltip
        override fun equals(other: Any?): Boolean = other is FlowGutterIconRenderer && other.icon == icon
        override fun hashCode(): Int = icon.hashCode()
    }

    fun highlightFlows(
        editor: Editor,
        response: FocusModeResponse,
        displayMode: DisplayMode,
        project: Project
    ) {
        clearHighlights(editor)

        when (displayMode) {
            DisplayMode.NORMAL -> applyNormalMode(editor, response)
            DisplayMode.ADVANCED -> applyAdvancedMode(editor, response)
        }

        val virtualFile = editor.virtualFile
        if (virtualFile != null) {
            val filePath = virtualFile.path
            com.jetbrains.rider.plugins.sharpfocus.codevision.SharpFocusCodeVisionProvider.updateResponse(filePath, response)

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val codeVisionHost = project.getService(com.intellij.codeInsight.codeVision.CodeVisionHost::class.java)
                codeVisionHost?.invalidateProvider(
                    com.intellij.codeInsight.codeVision.CodeVisionHost.LensInvalidateSignal(editor)
                )
            }

            // Notify the tool window to update
            project.getUserData(FlowTreeToolWindowFactory.FLOW_TREE_VIEW_KEY)?.update(filePath, response)
            onHighlightCallback?.invoke(filePath, response)
        }
    }

    private fun applyNormalMode(editor: Editor, response: FocusModeResponse) {
        val document = editor.document
        val relevantRanges = response.relevantRanges

        if (relevantRanges.isEmpty()) {
            return
        }

        val focusOffsets = relevantRanges.map { range ->
            val startOffset = getOffset(editor, range.start.line, range.start.character)
            val endOffset = getOffset(editor, range.end.line, range.end.character)
            startOffset to endOffset
        }.sortedBy { it.first }

        val fadeRanges = mutableListOf<Pair<Int, Int>>()
        var currentOffset = 0

        for ((start, end) in focusOffsets) {
            if (currentOffset < start) {
                fadeRanges.add(currentOffset to start)
            }
            currentOffset = maxOf(currentOffset, end)
        }

        if (currentOffset < document.textLength) {
            fadeRanges.add(currentOffset to document.textLength)
        }

        val fadeAttrs = TextAttributes().apply {
            foregroundColor = NORMAL_FADE_FOREGROUND
        }

        for ((start, end) in fadeRanges) {
            if (start < end) {
                addHighlighter(editor, start, end, fadeAttrs, LAYER_FADE)
            }
        }

        for ((startOffset, endOffset) in focusOffsets) {
            if (startOffset < endOffset) {
                addHighlighter(editor, startOffset, endOffset, TextAttributes(), LAYER_FOCUS)
            }
        }

        val seedRange = response.focusedPlace.range
        val startOffset = getOffset(editor, seedRange.start.line, seedRange.start.character)
        val endOffset = getOffset(editor, seedRange.end.line, seedRange.end.character)

        val seedAttrs = TextAttributes().apply {
            backgroundColor = SEED_BG_COLOR
        }

        addHighlighter(editor, startOffset, endOffset, seedAttrs, LAYER_SEED)
    }

    private fun applyAdvancedMode(editor: Editor, response: FocusModeResponse) {
        val document = editor.document

        val allDetails = mutableListOf<SliceRangeInfo>()
        response.backwardSlice?.sliceRangeDetails?.let { allDetails.addAll(it) }
        response.forwardSlice?.sliceRangeDetails?.let { allDetails.addAll(it) }

        if (allDetails.isEmpty()) {
            return
        }

        val focusOffsets = allDetails.map { detail ->
            getOffset(editor, detail.range.start.line, detail.range.start.character) to
            getOffset(editor, detail.range.end.line, detail.range.end.character)
        }.sortedBy { it.first }

        val fadeRanges = mutableListOf<Pair<Int, Int>>()
        var currentOffset = 0

        for ((start, end) in focusOffsets) {
            if (currentOffset < start) {
                fadeRanges.add(currentOffset to start)
            }
            currentOffset = maxOf(currentOffset, end)
        }

        if (currentOffset < document.textLength) {
            fadeRanges.add(currentOffset to document.textLength)
        }

        val fadeAttrs = TextAttributes().apply {
            foregroundColor = NORMAL_FADE_FOREGROUND
        }

        for ((start, end) in fadeRanges) {
            if (start < end) {
                addHighlighter(editor, start, end, fadeAttrs, LAYER_FADE)
            }
        }

        val seedRange = response.focusedPlace.range
        val seedStart = getOffset(editor, seedRange.start.line, seedRange.start.character)
        val seedEnd = getOffset(editor, seedRange.end.line, seedRange.end.character)

        val seedAttrs = TextAttributes().apply {
            backgroundColor = SEED_BG_COLOR
            effectColor = SEED_BORDER_COLOR
            effectType = com.intellij.openapi.editor.markup.EffectType.ROUNDED_BOX
        }
        addHighlighter(editor, seedStart, seedEnd, seedAttrs, LAYER_SEED, SEED_ICON, "Focused symbol: ${response.focusedPlace.name}")

        allDetails.forEach { detail ->
            val startOffset = getOffset(editor, detail.range.start.line, detail.range.start.character)
            val endOffset = getOffset(editor, detail.range.end.line, detail.range.end.character)

            if (startOffset >= endOffset) {
                return@forEach
            }

            when (detail.relation) {
                SliceRelation.SOURCE -> {
                    val attrs = TextAttributes().apply {
                        backgroundColor = SOURCE_BG_COLOR
                        effectColor = SOURCE_BORDER_COLOR
                        effectType = com.intellij.openapi.editor.markup.EffectType.BOXED
                    }
                    addHighlighter(editor, startOffset, endOffset, attrs, LAYER_FOCUS, SOURCE_ICON, "Source: ${detail.place.name}")
                }
                SliceRelation.TRANSFORM -> {
                    val attrs = TextAttributes().apply {
                        backgroundColor = TRANSFORM_BG_COLOR
                        effectColor = TRANSFORM_BORDER_COLOR
                        effectType = com.intellij.openapi.editor.markup.EffectType.BOXED
                    }
                    addHighlighter(editor, startOffset, endOffset, attrs, LAYER_FOCUS, TRANSFORM_ICON, "Transform: ${detail.place.name}")
                }
                SliceRelation.SINK -> {
                    val attrs = TextAttributes().apply {
                        backgroundColor = SINK_BG_COLOR
                        effectColor = SINK_BORDER_COLOR
                        effectType = com.intellij.openapi.editor.markup.EffectType.BOXED
                    }
                    addHighlighter(editor, startOffset, endOffset, attrs, LAYER_FOCUS, SINK_ICON, "Sink: ${detail.place.name}")
                }
            }
        }
    }

    fun clearHighlights(editor: Editor) {
        highlighters[editor]?.forEach { it.dispose() }
        highlighters.remove(editor)

        val project = editor.project
        if (project != null) {
            com.jetbrains.rider.plugins.sharpfocus.codevision.SharpFocusCodeVisionProvider.clear()

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val codeVisionHost = project.getService(com.intellij.codeInsight.codeVision.CodeVisionHost::class.java)
                codeVisionHost?.invalidateProvider(
                    com.intellij.codeInsight.codeVision.CodeVisionHost.LensInvalidateSignal(editor)
                )
            }

            project.getUserData(FlowTreeToolWindowFactory.FLOW_TREE_VIEW_KEY)?.clear()
        }
    }

    /**
     * Clears all highlights across all editors.
     */
    fun clearAll() {
        highlighters.keys.toList().forEach { clearHighlights(it) }
        highlighters.clear()

        // Clear CodeVision globally
        com.jetbrains.rider.plugins.sharpfocus.codevision.SharpFocusCodeVisionProvider.clear()
    }

    private fun addHighlighter(
        editor: Editor,
        startOffset: Int,
        endOffset: Int,
        attributes: TextAttributes,
        layer: Int,
        gutterIcon: Icon? = null,
        gutterTooltip: String? = null
    ) {
        val highlighter = editor.markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            layer,
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )

        // Add gutter icon if provided
        if (gutterIcon != null && gutterTooltip != null) {
            highlighter.gutterIconRenderer = FlowGutterIconRenderer(gutterIcon, gutterTooltip)
        }

        highlighters.getOrPut(editor) { mutableListOf() }.add(highlighter)
    }

    private fun getOffset(editor: Editor, line: Int, character: Int): Int {
        val document = editor.document
        if (line >= document.lineCount) {
            return document.textLength
        }

        val lineStartOffset = document.getLineStartOffset(line)
        val lineEndOffset = document.getLineEndOffset(line)
        val targetOffset = lineStartOffset + character

        return minOf(targetOffset, lineEndOffset, document.textLength)
    }
}

/**
 * Display modes for focus highlighting.
 */
enum class DisplayMode {
    NORMAL,    // Simple fade with seed highlight
    ADVANCED   // Color-coded by relation type
}
