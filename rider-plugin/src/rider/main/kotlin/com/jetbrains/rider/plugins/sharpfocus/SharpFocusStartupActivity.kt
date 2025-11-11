package com.jetbrains.rider.plugins.sharpfocus

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Initializes the SharpFocus plugin when a project is opened.
 * This activity is responsible for starting the language server and
 * setting up the necessary listeners and services.
 */
class SharpFocusStartupActivity : StartupActivity {
    private val logger = logger<SharpFocusStartupActivity>()
    
    override fun runActivity(project: Project) {
        try {
            logger.info("SharpFocus startup activity triggered for project: ${project.name}")
            
            // Get the plugin instance and initialize it
            val plugin = SharpFocusPlugin.getInstance(project)
            plugin.initialize()
            
            logger.info("SharpFocus plugin startup activity completed")
        } catch (e: Exception) {
            logger.error("Failed to initialize SharpFocus plugin", e)
        }
    }
}
