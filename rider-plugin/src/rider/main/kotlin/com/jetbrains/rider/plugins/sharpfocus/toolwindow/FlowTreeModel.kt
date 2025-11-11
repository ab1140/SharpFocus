package com.jetbrains.rider.plugins.sharpfocus.toolwindow

import com.jetbrains.rider.plugins.sharpfocus.lsp.FocusModeResponse
import com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRangeInfo
import com.jetbrains.rider.plugins.sharpfocus.lsp.SliceRelation

/**
 * Represents a location that can be navigated to in the flow analysis.
 */
data class NavigableLocation(
    val filePath: String,
    val line: Int,
    val character: Int,
    val endLine: Int,
    val endCharacter: Int,
    val label: String,
    val description: String,
    val relation: SliceRelation,
    val category: FlowCategory,
    val summary: String?
)

/**
 * Flow category for visual distinction.
 */
enum class FlowCategory {
    SOURCE,
    TRANSFORM,
    SINK
}

/**
 * Base class for tree nodes.
 */
sealed class FlowTreeNode(
    open val displayText: String,
    open val description: String?
) {
    
    /**
     * Header node showing the focused symbol.
     */
    data class Header(
        override val displayText: String,
        override val description: String,
        val totalLocations: Int
    ) : FlowTreeNode(displayText, description)
    
    /**
     * Group node for multiple locations in the same method.
     */
    data class Group(
        override val displayText: String,
        override val description: String,
        val children: List<Location>
    ) : FlowTreeNode(displayText, description)
    
    /**
     * Individual location node.
     */
    data class Location(
        override val displayText: String,
        override val description: String,
        val location: NavigableLocation,
        val isCurrent: Boolean,
        val index: Int
    ) : FlowTreeNode(displayText, description)
    
    /**
     * Empty state node.
     */
    data class Empty(
        override val displayText: String
    ) : FlowTreeNode(displayText, null)
}

/**
 * Manages the flow tree structure and navigation state.
 */
class FlowTreeModel {
    
    private var currentResponse: FocusModeResponse? = null
    private var currentFilePath: String? = null
    private var locations: List<NavigableLocation> = emptyList()
    private var currentIndex: Int = -1
    
    /**
     * Update the model with new focus mode results.
     */
    fun update(filePath: String, response: FocusModeResponse?) {
        currentFilePath = filePath
        currentResponse = response
        
        if (response != null) {
            locations = extractLocations(filePath, response)
            currentIndex = -1
        } else {
            locations = emptyList()
            currentIndex = -1
        }
    }
    
    /**
     * Clear the model.
     */
    fun clear() {
        currentResponse = null
        currentFilePath = null
        locations = emptyList()
        currentIndex = -1
    }
    
    /**
     * Get all navigable locations.
     */
    fun getLocations(): List<NavigableLocation> = locations
    
    /**
     * Get the current navigation index.
     */
    fun getCurrentIndex(): Int = currentIndex
    
    /**
     * Set the current navigation index.
     */
    fun setCurrentIndex(index: Int) {
        if (index in locations.indices) {
            currentIndex = index
        }
    }
    
    /**
     * Check if there are any locations.
     */
    fun hasLocations(): Boolean = locations.isNotEmpty()
    
    /**
     * Get the total count of locations.
     */
    fun getTotalCount(): Int = locations.size
    
    /**
     * Get the focused symbol name.
     */
    fun getFocusedSymbol(): String? = currentResponse?.focusedPlace?.name
    
    /**
     * Build the tree structure for display.
     */
    fun buildTree(): List<FlowTreeNode> {
        if (!hasLocations() || currentResponse == null) {
            return listOf(FlowTreeNode.Empty("No flow analysis active"))
        }
        
        val nodes = mutableListOf<FlowTreeNode>()
        val focusedSymbol = getFocusedSymbol() ?: "Unknown"
        val positionText = if (currentIndex >= 0) {
            "${currentIndex + 1}/${locations.size}"
        } else {
            "${locations.size} location${if (locations.size == 1) "" else "s"}"
        }
        
        // Add header
        nodes.add(FlowTreeNode.Header(focusedSymbol, positionText, locations.size))
        
        // Group locations by method/summary
        val grouped = groupLocationsByMethod()
        
        for (group in grouped) {
            if (group.locations.size == 1) {
                // Single location - add directly
                val locInfo = group.locations[0]
                nodes.add(createLocationNode(locInfo.location, locInfo.index == currentIndex, locInfo.index))
            } else {
                // Multiple locations - create group
                val children = group.locations.map { locInfo ->
                    createLocationNode(locInfo.location, locInfo.index == currentIndex, locInfo.index)
                }
                nodes.add(
                    FlowTreeNode.Group(
                        displayText = group.methodName,
                        description = "${group.locations.size} location${if (group.locations.size > 1) "s" else ""}",
                        children = children
                    )
                )
            }
        }
        
        return nodes
    }
    
    /**
     * Extract navigable locations from the focus mode response.
     */
    private fun extractLocations(filePath: String, response: FocusModeResponse): List<NavigableLocation> {
        val locs = mutableListOf<NavigableLocation>()
        
        // Extract backward slice locations (sources/transforms)
        response.backwardSlice?.sliceRangeDetails?.forEach { detail ->
            val category = toCategory(detail.relation)
            locs.add(createLocation(filePath, detail, category))
        }
        
        // Extract forward slice locations (sinks/transforms)
        response.forwardSlice?.sliceRangeDetails?.forEach { detail ->
            val category = toCategory(detail.relation)
            locs.add(createLocation(filePath, detail, category))
        }
        
        // Sort by line number
        return locs.sortedWith(compareBy({ it.line }, { it.character }))
    }
    
    /**
     * Create a navigable location from slice range info.
     */
    private fun createLocation(
        filePath: String,
        detail: SliceRangeInfo,
        category: FlowCategory
    ): NavigableLocation {
        val symbolName = detail.place?.name ?: ""
        
        return NavigableLocation(
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
    }
    
    /**
     * Convert relation to category.
     */
    private fun toCategory(relation: SliceRelation): FlowCategory {
        return when (relation) {
            SliceRelation.SOURCE -> FlowCategory.SOURCE
            SliceRelation.TRANSFORM -> FlowCategory.TRANSFORM
            SliceRelation.SINK -> FlowCategory.SINK
        }
    }
    
    /**
     * Group locations by method name or line number.
     */
    private fun groupLocationsByMethod(): List<GroupInfo> {
        val groups = mutableMapOf<String, MutableList<LocationInfo>>()
        
        locations.forEachIndexed { index, location ->
            val methodName = location.summary ?: "Line ${location.line + 1}"
            
            groups.getOrPut(methodName) { mutableListOf() }
                .add(LocationInfo(location, index))
        }
        
        return groups.map { (methodName, locs) ->
            GroupInfo(methodName, locs)
        }
    }
    
    /**
     * Create a location tree node.
     */
    private fun createLocationNode(
        location: NavigableLocation,
        isCurrent: Boolean,
        index: Int
    ): FlowTreeNode.Location {
        return FlowTreeNode.Location(
            displayText = location.label,
            description = location.description,
            location = location,
            isCurrent = isCurrent,
            index = index
        )
    }
    
    /**
     * Helper class for grouping.
     */
    private data class LocationInfo(
        val location: NavigableLocation,
        val index: Int
    )
    
    /**
     * Helper class for group information.
     */
    private data class GroupInfo(
        val methodName: String,
        val locations: List<LocationInfo>
    )
}
