package com.jetbrains.rider.plugins.sharpfocus.lsp

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

/**
 * Extended language server API that includes SharpFocus custom methods.
 *
 * This interface extends the standard LSP LanguageServer interface with custom
 * methods specific to SharpFocus. The @JsonRequest annotation tells LSP4J how
 * to serialize/deserialize these custom requests.
 */
interface SharpFocusLanguageServerAPI : LanguageServer {

    /**
     * Request focus mode analysis for a specific position in a document.
     *
     * @param request The focus mode request parameters
     * @return A CompletableFuture that completes with the focus mode analysis results
     */
    @JsonRequest("sharpfocus/focusMode")
    fun focusMode(request: FocusModeRequest): CompletableFuture<FocusModeResponse>
}
