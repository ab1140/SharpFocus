package com.jetbrains.rider.plugins.sharpfocus.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageServer

/**
 * Synchronizes document open/close/change events with the language server.
 * This is essential for the language server to maintain semantic models.
 */
class TextDocumentSynchronizer(private val project: Project) : Disposable {
    
    private val logger = logger<TextDocumentSynchronizer>()
    private val openDocuments = mutableSetOf<String>()
    
    fun start() {
        // Listen for file editor events
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    if (file.fileType.name == "C#") {
                        logger.info("C# file opened: ${file.path}")
                        notifyDidOpen(file)
                    }
                }
                
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    if (file.fileType.name == "C#") {
                        logger.info("C# file closed: ${file.path}")
                        notifyDidClose(file)
                    }
                }
            }
        )
        
        // Notify about already open C# files
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { file ->
            if (file.fileType.name == "C#") {
                logger.info("Notifying about already open C# file: ${file.path}")
                notifyDidOpen(file)
            }
        }
    }
    
    private fun notifyDidOpen(file: VirtualFile) {
        val serverManager = SharpFocusLanguageServerManager.getInstance(project)
        val server = serverManager.getServer()
        
        if (server == null || !serverManager.isServerRunning()) {
            logger.warn("Cannot notify didOpen: language server not running")
            return
        }
        
        val documentUri = file.url
        if (openDocuments.contains(documentUri)) {
            logger.debug("Document already reported as open: $documentUri")
            return
        }
        
        // Get document content with read access
        val documentText = com.intellij.openapi.application.ReadAction.compute<String?, RuntimeException> {
            val document = FileDocumentManager.getInstance().getDocument(file)
            if (document == null) {
                logger.warn("Cannot get document for file: ${file.path}")
                return@compute null
            }
            document.text
        }
        
        if (documentText == null) {
            return
        }
        
        val textDocumentItem = TextDocumentItem().apply {
            uri = documentUri
            languageId = "csharp"
            version = 1
            text = documentText
        }
        
        val params = DidOpenTextDocumentParams(textDocumentItem)
        
        try {
            server.textDocumentService.didOpen(params)
            openDocuments.add(documentUri)
            logger.info("Notified language server: didOpen for $documentUri")
        } catch (e: Exception) {
            logger.error("Error notifying didOpen for $documentUri", e)
        }
    }
    
    private fun notifyDidClose(file: VirtualFile) {
        val serverManager = SharpFocusLanguageServerManager.getInstance(project)
        val server = serverManager.getServer()
        
        if (server == null || !serverManager.isServerRunning()) {
            return
        }
        
        val documentUri = file.url
        if (!openDocuments.contains(documentUri)) {
            return
        }
        
        val textDocumentIdentifier = org.eclipse.lsp4j.TextDocumentIdentifier(documentUri)
        val params = DidCloseTextDocumentParams(textDocumentIdentifier)
        
        try {
            server.textDocumentService.didClose(params)
            openDocuments.remove(documentUri)
            logger.info("Notified language server: didClose for $documentUri")
        } catch (e: Exception) {
            logger.error("Error notifying didClose for $documentUri", e)
        }
    }
    
    override fun dispose() {
        // Cleanup
        openDocuments.clear()
    }
}
