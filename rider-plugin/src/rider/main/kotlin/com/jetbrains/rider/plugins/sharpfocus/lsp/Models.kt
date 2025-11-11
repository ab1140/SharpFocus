package com.jetbrains.rider.plugins.sharpfocus.lsp

import com.google.gson.annotations.SerializedName

/**
 * Models for LSP custom protocol used by SharpFocus.
 * These match the protocol defined in SharpFocus.LanguageServer.
 */

/**
 * Request for focus mode analysis.
 */
data class FocusModeRequest(
    @SerializedName("textDocument")
    val textDocument: TextDocumentIdentifier,
    
    @SerializedName("position")
    val position: Position
)

/**
 * Response from focus mode analysis.
 */
data class FocusModeResponse(
    @SerializedName("focusedPlace")
    val focusedPlace: PlaceInfo,
    
    @SerializedName("relevantRanges")
    val relevantRanges: List<Range>,
    
    @SerializedName("containerRanges")
    val containerRanges: List<Range>,
    
    @SerializedName("backwardSlice")
    val backwardSlice: SliceResponse?,
    
    @SerializedName("forwardSlice")
    val forwardSlice: SliceResponse?
)

/**
 * Response from slice analysis.
 */
data class SliceResponse(
    @SerializedName("direction")
    val direction: SliceDirection,
    
    @SerializedName("focusedPlace")
    val focusedPlace: PlaceInfo,
    
    @SerializedName("sliceRanges")
    val sliceRanges: List<Range>,
    
    @SerializedName("sliceRangeDetails")
    val sliceRangeDetails: List<SliceRangeInfo>?,
    
    @SerializedName("containerRanges")
    val containerRanges: List<Range>
)

/**
 * Detailed information about a range in a slice.
 */
data class SliceRangeInfo(
    @SerializedName("range")
    val range: Range,
    
    @SerializedName("place")
    val place: PlaceInfo,
    
    @SerializedName("relation")
    val relation: SliceRelation,
    
    @SerializedName("operationKind")
    val operationKind: String,
    
    @SerializedName("summary")
    val summary: String?
)

/**
 * Information about a place (variable, parameter, etc.).
 */
data class PlaceInfo(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("range")
    val range: Range,
    
    @SerializedName("kind")
    val kind: String
)

/**
 * Direction of slice analysis.
 */
enum class SliceDirection {
    @SerializedName("Backward")
    BACKWARD,
    
    @SerializedName("Forward")
    FORWARD
}

/**
 * Relationship classification for a range within a slice.
 */
enum class SliceRelation {
    @SerializedName("Source")
    SOURCE,
    
    @SerializedName("Transform")
    TRANSFORM,
    
    @SerializedName("Sink")
    SINK
}

/**
 * Text document identifier.
 */
data class TextDocumentIdentifier(
    @SerializedName("uri")
    val uri: String
)

/**
 * Position in a text document.
 */
data class Position(
    @SerializedName("line")
    val line: Int,
    
    @SerializedName("character")
    val character: Int
)

/**
 * Range in a text document.
 */
data class Range(
    @SerializedName("start")
    val start: Position,
    
    @SerializedName("end")
    val end: Position
)
