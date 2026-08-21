package dev.hrtkaffee.ar.web.ui

import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.hrtkaffee.ar.webapp.generated.resources.Res
import dev.hrtkaffee.ar.webapp.generated.resources.terra_ops_cjk
import org.jetbrains.compose.resources.Font

internal val LocalArFontFamily = staticCompositionLocalOf { FontFamily.Default }

@Composable
internal fun ArTypography(content: @Composable () -> Unit) {
    val arFontFamily = FontFamily(
        Font(Res.font.terra_ops_cjk, weight = FontWeight.Medium),
    )

    CompositionLocalProvider(LocalArFontFamily provides arFontFamily) {
        ProvideTextStyle(
            value = TextStyle(fontFamily = arFontFamily),
            content = content,
        )
    }
}
