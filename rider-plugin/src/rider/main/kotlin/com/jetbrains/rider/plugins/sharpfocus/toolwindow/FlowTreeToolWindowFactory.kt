package com.jetbrains.rider.plugins.sharpfocus.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the SharpFocus flow tree tool window.
 */
class FlowTreeToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val treeView = FlowTreeView(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(treeView.createComponent(), "", false)
        toolWindow.contentManager.addContent(content)
        
        // Store the tree view in the project for access by other components
        project.putUserData(FLOW_TREE_VIEW_KEY, treeView)
    }
    
    companion object {
        val FLOW_TREE_VIEW_KEY = com.intellij.openapi.util.Key.create<FlowTreeView>("SharpFocus.FlowTreeView")
    }
}
