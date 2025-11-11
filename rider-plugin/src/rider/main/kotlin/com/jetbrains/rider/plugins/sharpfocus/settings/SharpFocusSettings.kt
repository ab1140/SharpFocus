package com.jetbrains.rider.plugins.sharpfocus.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.rider.plugins.sharpfocus.highlighting.DisplayMode

/**
 * Persistent settings for SharpFocus plugin.
 * Stores configuration at project level.
 */
@State(
    name = "SharpFocusSettings",
    storages = [Storage("sharpfocus.xml")]
)
@Service(Service.Level.PROJECT)
class SharpFocusSettings : PersistentStateComponent<SharpFocusSettings> {
    
    /**
     * Analysis mode - how the plugin responds to cursor movement.
     */
    var analysisMode: AnalysisMode = AnalysisMode.FOCUS
    
    /**
     * Display mode - how flow locations are rendered in the editor.
     */
    var displayMode: DisplayMode = DisplayMode.NORMAL
    
    /**
     * Custom path to language server DLL.
     * If empty, the plugin will search for it automatically.
     */
    var serverPath: String = ""
    
    /**
     * Enable trace logging for language server communication.
     */
    var enableTracing: Boolean = false
    
    /**
     * Automatically disable Rider's native word highlighting to avoid conflicts.
     */
    var disableNativeWordHighlight: Boolean = true
    
    companion object {
        fun getInstance(project: Project): SharpFocusSettings {
            return project.getService(SharpFocusSettings::class.java)
        }
    }
    
    override fun getState(): SharpFocusSettings {
        return this
    }
    
    override fun loadState(state: SharpFocusSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

/**
 * Analysis mode determines when the plugin performs flow analysis.
 */
enum class AnalysisMode(val displayName: String, val description: String) {
    /**
     * Focus mode: Automatically analyze on cursor movement (debounced).
     */
    FOCUS(
        "Focus Mode", 
        "Automatically highlights flow when you move the cursor (recommended)"
    ),
    
    /**
     * Manual mode: Only analyze when explicitly triggered via action.
     */
    MANUAL(
        "Manual", 
        "Only analyze when you press Ctrl+Alt+F"
    ),
    
    /**
     * Off: Disable all automatic analysis.
     */
    OFF(
        "Off", 
        "Disable automatic analysis completely"
    );
    
    override fun toString(): String = displayName
}
