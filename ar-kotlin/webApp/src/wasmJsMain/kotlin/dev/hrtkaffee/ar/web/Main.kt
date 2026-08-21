package dev.hrtkaffee.ar.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.hrtkaffee.ar.web.ui.ArSuppressionApp
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    if (!supportsWebGl2()) {
        renderDomFallback(body)
        return
    }

    document.getElementById("loading-shell")?.remove()
    ComposeViewport(body) {
        ArSuppressionApp()
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun supportsWebGl2(): Boolean =
    (document.createElement("canvas") as HTMLCanvasElement).getContext("webgl2") != null
