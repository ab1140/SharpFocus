package com.jetbrains.rider.plugins.sharpfocus.lsp

import com.intellij.openapi.diagnostic.logger
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/**
 * LSP client implementation for SharpFocus.
 * Handles messages from the language server.
 */
class SharpFocusLanguageClient : LanguageClient {
    
    private val logger = logger<SharpFocusLanguageClient>()
    
    override fun telemetryEvent(obj: Any?) {
        logger.debug("Telemetry event: $obj")
    }
    
    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        diagnostics?.let {
            logger.debug("Diagnostics for ${it.uri}: ${it.diagnostics.size} items")
        }
    }
    
    override fun showMessage(messageParams: MessageParams?) {
        messageParams?.let {
            when (it.type) {
                MessageType.Error -> logger.error(it.message)
                MessageType.Warning -> logger.warn(it.message)
                MessageType.Info -> logger.info(it.message)
                MessageType.Log -> logger.debug(it.message)
            }
        }
    }
    
    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        requestParams?.let {
            logger.info("Message request: ${it.message}")
        }
        return CompletableFuture.completedFuture(MessageActionItem("OK"))
    }
    
    override fun logMessage(message: MessageParams?) {
        message?.let {
            logger.info("[LSP] ${it.message}")
        }
    }
}
