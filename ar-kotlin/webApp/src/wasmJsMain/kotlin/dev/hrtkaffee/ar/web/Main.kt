package dev.hrtkaffee.ar.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.hrtkaffee.ar.web.ui.ArSuppressionApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("loading-shell")?.remove()
    val body = document.body ?: return
    ComposeViewport(body) {
        ArSuppressionApp()
    }
}
