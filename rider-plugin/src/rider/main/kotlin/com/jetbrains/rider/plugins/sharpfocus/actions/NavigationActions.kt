package com.jetbrains.rider.plugins.sharpfocus.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rider.plugins.sharpfocus.navigation.NavigationManager

class NavigateNextAction : AnAction("Navigate Next", "Navigate to next flow location", null) {

    private val logger = logger<NavigateNextAction>()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val navigationManager = NavigationManager.getInstance(project)

        if (!navigationManager.hasLocations()) {
            showNotification(project, "No flow locations to navigate. Click on a symbol first.")
            return
        }

        val success = navigationManager.navigateNext()

        if (success) {
            val info = navigationManager.getCurrentLocationInfo()
            if (info.isNotEmpty()) {
                showStatusMessage(project, "SharpFocus: $info")
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val navigationManager = NavigationManager.getInstance(project)
        event.presentation.isEnabled = navigationManager.hasLocations()

        if (navigationManager.hasLocations()) {
            val symbolName = navigationManager.getSymbolName()
            val current = navigationManager.getCurrentIndex() + 1
            val total = navigationManager.getTotalCount()
            event.presentation.description = "Navigate to next flow location ($current/$total in $symbolName)"
        } else {
            event.presentation.description = "Navigate to next flow location"
        }
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SharpFocus")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun showStatusMessage(project: com.intellij.openapi.project.Project, message: String) {
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)
            ?.info = message
    }
}

class NavigatePreviousAction : AnAction("Navigate Previous", "Navigate to previous flow location", null) {

    private val logger = logger<NavigatePreviousAction>()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val navigationManager = NavigationManager.getInstance(project)

        if (!navigationManager.hasLocations()) {
            showNotification(project, "No flow locations to navigate. Click on a symbol first.")
            return
        }

        val success = navigationManager.navigatePrevious()

        if (success) {
            val info = navigationManager.getCurrentLocationInfo()
            if (info.isNotEmpty()) {
                showStatusMessage(project, "SharpFocus: $info")
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val navigationManager = NavigationManager.getInstance(project)
        event.presentation.isEnabled = navigationManager.hasLocations()

        if (navigationManager.hasLocations()) {
            val symbolName = navigationManager.getSymbolName()
            val current = navigationManager.getCurrentIndex() + 1
            val total = navigationManager.getTotalCount()
            event.presentation.description = "Navigate to previous flow location ($current/$total in $symbolName)"
        } else {
            event.presentation.description = "Navigate to previous flow location"
        }
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SharpFocus")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun showStatusMessage(project: com.intellij.openapi.project.Project, message: String) {
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)
            ?.info = message
    }
}

class NavigateToLocationAction : AnAction("Navigate to Location", "Navigate to specific flow location", null) {

    override fun actionPerformed(event: AnActionEvent) {
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = false
    }
}
