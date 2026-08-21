package dev.hrtkaffee.ar.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import dev.hrtkaffee.ar.web.ui.ArSuppressionApp
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("loading-shell")?.remove()
    val body = document.body ?: return
    ComposeViewport(body) {
        if (window.location.search == "?diagnostic=minimal") {
            WasmRuntimeProbe()
        } else {
            ArSuppressionApp()
        }
    }
}

@Composable
private fun WasmRuntimeProbe() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080B0F)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "KOTLIN/WASM RUNTIME OK",
            color = Color(0xFF54E6E0),
            fontFamily = FontFamily.Monospace,
        )
    }
}
