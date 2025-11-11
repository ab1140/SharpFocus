package com.jetbrains.rider.plugins.sharpfocus.navigation

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.rider.plugins.sharpfocus.lsp.FocusModeResponse
import com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowTreeToolWindowFactory
import com.jetbrains.rider.plugins.sharpfocus.toolwindow.NavigableLocation

@Service(Service.Level.PROJECT)
class NavigationManager(private val project: Project) {

    private val logger = logger<NavigationManager>()

    private var locations: List<NavigableLocation> = emptyList()
    private var currentIndex: Int = -1
    private var focusedSymbolName: String = ""
    private var currentFilePath: String? = null

    private var navigationInProgress = false
    private var onNavigationChangeCallback: (() -> Unit)? = null

    companion object {
        fun getInstance(project: Project): NavigationManager {
            return project.getService(NavigationManager::class.java)
        }
    }

    fun onNavigationChange(callback: () -> Unit) {
        onNavigationChangeCallback = callback
    }

    fun initialize(filePath: String, response: FocusModeResponse) {
        locations = extractLocations(filePath, response)
        currentIndex = -1
        focusedSymbolName = response.focusedPlace.name
        currentFilePath = filePath
        notifyNavigationChange()
    }

    fun clear() {
        locations = emptyList()
        currentIndex = -1
        focusedSymbolName = ""
        currentFilePath = null
        notifyNavigationChange()
    }

    fun hasLocations(): Boolean = locations.isNotEmpty()

    fun getTotalCount(): Int = locations.size

    fun getCurrentIndex(): Int = currentIndex

    fun getSymbolName(): String = focusedSymbolName

    fun getLocations(): List<NavigableLocation> = locations

    fun isNavigationInProgress(): Boolean = navigationInProgress

    fun navigateNext(): Boolean {
        if (!hasLocations()) {
            return false
        }

        currentIndex = (currentIndex + 1) % locations.size
        navigateToCurrentLocation()
        notifyNavigationChange()
        return true
    }

    fun navigatePrevious(): Boolean {
        if (!hasLocations()) {
            return false
        }

        currentIndex = if (currentIndex <= 0) locations.size - 1 else currentIndex - 1
        navigateToCurrentLocation()
        notifyNavigationChange()
        return true
    }

    fun navigateToIndex(index: Int): Boolean {
        if (index < 0 || index >= locations.size) {
            return false
        }

        currentIndex = index
        navigateToCurrentLocation()
        notifyNavigationChange()
        return true
    }

    fun navigateToLocation(location: NavigableLocation) {
        navigationInProgress = true

        try {
            val index = locations.indexOf(location)
            if (index >= 0) {
                currentIndex = index
            }

            val virtualFile = LocalFileSystem.getInstance().findFileByPath(location.filePath)

            if (virtualFile != null) {
                val descriptor = OpenFileDescriptor(
                    project,
                    virtualFile,
                    location.line,
                    location.character
                )
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                updateToolWindow()
            } else {
                logger.warn("Could not find file: ${location.filePath}")
            }
        } finally {
            java.util.concurrent.CompletableFuture.runAsync {
                Thread.sleep(500)
                navigationInProgress = false
            }
        }

        notifyNavigationChange()
    }

    fun getCurrentLocationInfo(): String {
        if (!hasLocations() || currentIndex < 0) {
            return ""
        }

        val location = locations[currentIndex]
        val position = currentIndex + 1
        return "$position/${locations.size}: ${location.label} · ${location.description}"
    }

    private fun extractLocations(filePath: String, response: FocusModeResponse): List<NavigableLocation> {
        val locs = mutableListOf<NavigableLocation>()

        response.backwardSlice?.sliceRangeDetails?.forEach { detail ->
            val category = when (detail.relation) {
                com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRelation.SOURCE ->
                    com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowCategory.SOURCE
                com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRelation.TRANSFORM ->
                    com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowCategory.TRANSFORM
                else -> com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowCategory.SOURCE
            }

            val symbolName = detail.place?.name ?: ""
            locs.add(
                NavigableLocation(
                    filePath = filePath,
                    line = detail.range.start.line,
                    character = detail.range.start.character,
                    endLine = detail.range.end.line,
                    endCharacter = detail.range.end.character,
                    label = "Line ${detail.range.start.line + 1}",
                    description = "${detail.relation}${if (symbolName.isNotEmpty()) " · $symbolName" else ""}",
                    relation = detail.relation,
                    category = category,
                    summary = detail.summary
                )
            )
        }

        response.forwardSlice?.sliceRangeDetails?.forEach { detail ->
            val category = when (detail.relation) {
                com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRelation.SINK ->
                    com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowCategory.SINK
                com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRelation.TRANSFORM ->
                    com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowCategory.TRANSFORM
                else -> com.jetbrains.rider.plugins.sharpfocus.toolwindow.FlowCategory.SINK
            }

            val symbolName = detail.place?.name ?: ""
            locs.add(
                NavigableLocation(
                    filePath = filePath,
                    line = detail.range.start.line,
                    character = detail.range.start.character,
                    endLine = detail.range.end.line,
                    endCharacter = detail.range.end.character,
                    label = "Line ${detail.range.start.line + 1}",
                    description = "${detail.relation}${if (symbolName.isNotEmpty()) " · $symbolName" else ""}",
                    relation = detail.relation,
                    category = category,
                    summary = detail.summary
                )
            )
        }

        return locs.sortedWith(compareBy({ it.line }, { it.character }))
    }

    private fun navigateToCurrentLocation() {
        if (currentIndex < 0 || currentIndex >= locations.size) {
            return
        }

        val location = locations[currentIndex]
        navigateToLocation(location)
    }

    private fun updateToolWindow() {
        project.getUserData(FlowTreeToolWindowFactory.FLOW_TREE_VIEW_KEY)?.setCurrentIndex(currentIndex)
    }

    private fun notifyNavigationChange() {
        onNavigationChangeCallback?.invoke()
        updateToolWindow()
    }
}
