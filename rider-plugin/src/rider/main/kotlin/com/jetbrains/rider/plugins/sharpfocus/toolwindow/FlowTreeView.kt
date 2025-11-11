package com.jetbrains.rider.plugins.sharpfocus.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.jetbrains.rider.plugins.sharpfocus.lsp.FocusModeResponse
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Tree view component for displaying flow analysis results.
 */
class FlowTreeView(private val project: Project) {

    private val model = FlowTreeModel()
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode: DefaultMutableTreeNode
    private val navigationManager = com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager.getInstance(project)

    private var onNavigationCallback: ((Int) -> Unit)? = null

    // Load custom icons
    private val seedIcon: Icon by lazy { IconLoader.getIcon("/icons/seed-icon.svg", FlowTreeView::class.java) }
    private val sourceIcon: Icon by lazy { IconLoader.getIcon("/icons/source-icon.svg", FlowTreeView::class.java) }
    private val transformIcon: Icon by lazy { IconLoader.getIcon("/icons/transform-icon.svg", FlowTreeView::class.java) }
    private val sinkIcon: Icon by lazy { IconLoader.getIcon("/icons/sink-icon.svg", FlowTreeView::class.java) }

    init {
        rootNode = DefaultMutableTreeNode("Flow Analysis")
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)

        setupTree()
    }

    /**
     * Set callback for navigation events.
     */
    fun onNavigation(callback: (Int) -> Unit) {
        onNavigationCallback = callback
    }

    /**
     * Get the tree component wrapped in a scroll pane.
     */
    fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())

        // Add toolbar
        val toolbar = createToolbar()
        panel.add(toolbar.component, BorderLayout.NORTH)

        // Add tree
        val scrollPane = JScrollPane(tree)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    /**
     * Update the tree with new focus mode results.
     */
    fun update(filePath: String, response: FocusModeResponse?) {
        model.update(filePath, response)
        refresh()
    }

    /**
     * Clear the tree.
     */
    fun clear() {
        model.clear()
        refresh()
    }

    /**
     * Set the current navigation index and update the tree.
     */
    fun setCurrentIndex(index: Int) {
        model.setCurrentIndex(index)
        refresh()
    }

    /**
     * Get the model for external access.
     */
    fun getModel(): FlowTreeModel = model

    /**
     * Refresh the tree display.
     */
    private fun refresh() {
        rootNode.removeAllChildren()

        val nodes = model.buildTree()

        for (node in nodes) {
            when (node) {
                is FlowTreeNode.Header -> {
                    val treeNode = DefaultMutableTreeNode(node)
                    rootNode.add(treeNode)
                }
                is FlowTreeNode.Group -> {
                    val groupNode = DefaultMutableTreeNode(node)
                    for (child in node.children) {
                        groupNode.add(DefaultMutableTreeNode(child))
                    }
                    rootNode.add(groupNode)
                }
                is FlowTreeNode.Location -> {
                    val treeNode = DefaultMutableTreeNode(node)
                    rootNode.add(treeNode)
                }
                is FlowTreeNode.Empty -> {
                    val treeNode = DefaultMutableTreeNode(node)
                    rootNode.add(treeNode)
                }
            }
        }

        treeModel.reload()

        // Expand all by default
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    /**
     * Setup tree properties and listeners.
     */
    private fun setupTree() {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = FlowTreeCellRenderer()
        tree.border = JBUI.Borders.empty(5)

        // Handle double-click navigation
        tree.addTreeSelectionListener { event ->
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val userObject = node.userObject as? FlowTreeNode ?: return@addTreeSelectionListener

            if (userObject is FlowTreeNode.Location) {
                navigateToLocation(userObject.location)
                onNavigationCallback?.invoke(userObject.index)
            }
        }
    }

    /**
     * Create the toolbar for the tool window.
     */
    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ClearAction())
            addSeparator()
            add(NavigatePreviousAction())
            add(NavigateNextAction())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("SharpFocusFlowTree", actionGroup, true)
        toolbar.targetComponent = tree

        return toolbar
    }

    /**
     * Navigate to a specific location in the editor.
     */
    private fun navigateToLocation(location: NavigableLocation) {
        // Use the navigation manager to handle navigation
        navigationManager.navigateToLocation(location)
    }

    /**
     * Custom cell renderer for the flow tree.
     */
    private inner class FlowTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            val flowNode = node.userObject as? FlowTreeNode ?: return

            when (flowNode) {
                is FlowTreeNode.Header -> {
                    icon = seedIcon
                    append(flowNode.displayText, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(flowNode.description ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is FlowTreeNode.Group -> {
                    icon = AllIcons.Nodes.Method
                    append(flowNode.displayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(flowNode.description ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is FlowTreeNode.Location -> {
                    // Set icon based on category
                    icon = when (flowNode.location.category) {
                        FlowCategory.SOURCE -> sourceIcon
                        FlowCategory.TRANSFORM -> transformIcon
                        FlowCategory.SINK -> sinkIcon
                    }

                    // Highlight current location
                    val textAttributes = if (flowNode.isCurrent) {
                        SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                    } else {
                        SimpleTextAttributes.REGULAR_ATTRIBUTES
                    }

                    append(flowNode.displayText, textAttributes)
                    append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(flowNode.description ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)

                    // Add current indicator
                    if (flowNode.isCurrent) {
                        append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        append("◄", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBUI.CurrentTheme.Link.Foreground.ENABLED))
                    }
                }

                is FlowTreeNode.Empty -> {
                    icon = AllIcons.General.Information
                    append(flowNode.displayText, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                }
            }
        }
    }

    /**
     * Action to refresh the tree.
     */
    private inner class RefreshAction : AnAction("Refresh", "Refresh the flow tree", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refresh()
        }
    }

    /**
     * Action to clear the tree.
     */
    private inner class ClearAction : AnAction("Clear", "Clear the flow analysis", AllIcons.Actions.GC) {
        override fun actionPerformed(e: AnActionEvent) {
            clear()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = model.hasLocations()
        }
    }

    /**
     * Action to navigate to previous location.
     */
    private inner class NavigatePreviousAction : AnAction(
        "Previous",
        "Navigate to previous location",
        AllIcons.Actions.Back
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            navigationManager.navigatePrevious()
            refresh()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = model.hasLocations()
        }
    }

    /**
     * Action to navigate to next location.
     */
    private inner class NavigateNextAction : AnAction(
        "Next",
        "Navigate to next location",
        AllIcons.Actions.Forward
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            navigationManager.navigateNext()
            refresh()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = model.hasLocations()
        }
    }
}
