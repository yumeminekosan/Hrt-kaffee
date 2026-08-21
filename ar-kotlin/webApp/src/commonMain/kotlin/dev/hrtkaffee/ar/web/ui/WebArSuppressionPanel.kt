package dev.hrtkaffee.ar.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hrtkaffee.ar.web.model.ArEquilibriumParameters
import dev.hrtkaffee.ar.web.model.ArIntervention
import dev.hrtkaffee.ar.web.model.ArSuppressionModel
import dev.hrtkaffee.ar.web.model.ArSuppressionResult
import dev.hrtkaffee.ar.web.model.BasisPoints
import dev.hrtkaffee.ar.web.model.Rational
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.roundToInt

private val TerminalBlack = Color(0xFF080B0F)
private val TerminalPanel = Color(0xFF11171D)
private val TerminalRaised = Color(0xFF172129)
private val GridLine = Color(0xFF28343D)
private val SignalCyan = Color(0xFF54E6E0)
private val UpstreamAmber = Color(0xFFFFC857)
private val AlertCoral = Color(0xFFFF6B5F)
private val TextPrimary = Color(0xFFF4F7F7)
private val TextMuted = Color(0xFF97A7B0)

private val terminalColors = darkColorScheme(
    primary = SignalCyan,
    secondary = UpstreamAmber,
    tertiary = AlertCoral,
    background = TerminalBlack,
    surface = TerminalPanel,
    onPrimary = TerminalBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun ArSuppressionApp() {
    MaterialTheme(colorScheme = terminalColors) {
        ArTypography {
            Surface(modifier = Modifier.fillMaxSize(), color = TerminalBlack) {
                ArSuppressionPanel()
            }
        }
    }
}

@Composable
fun ArSuppressionPanel(modifier: Modifier = Modifier) {
    FinasterideKineticsPanel(modifier)
}

@Composable
private fun TacticalGrid(modifier: Modifier) {
    Canvas(modifier) {
        val spacing = 48.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(GridLine.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += spacing
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(GridLine.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1f)
            y += spacing
        }
        val slashSpacing = 150.dp.toPx()
        var slashX = -size.height
        while (slashX < size.width) {
            drawLine(
                color = SignalCyan.copy(alpha = 0.035f),
                start = Offset(slashX, size.height),
                end = Offset(slashX + size.height, 0f),
                strokeWidth = 18.dp.toPx(),
            )
            slashX += slashSpacing
        }
    }
}

@Composable
private fun TacticalHeader(compact: Boolean) {
    @Composable
    fun TitleBlock(modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            Text(
                text = "K/AR // RECEPTOR OPERATIONS",
                color = SignalCyan,
                fontFamily = LocalArFontFamily.current,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.4.sp,
                fontSize = 13.sp,
            )
            Text(
                text = "雄激素受体抑制 · 双机制战术面板",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = if (compact) 25.sp else 31.sp,
                letterSpacing = 0.4.sp,
            )
        }
    }

    @Composable
    fun RuntimeBlock(alignment: Alignment.Horizontal) {
        Column(horizontalAlignment = alignment) {
            StatusTag("KOTLIN/WASM · LIVE", SignalCyan)
            Spacer(Modifier.height(7.dp))
            Text(
                text = "EXACT COUNTERFACTUAL NODE  A-05",
                color = TextMuted,
                fontFamily = LocalArFontFamily.current,
                fontSize = 11.sp,
            )
        }
    }

    if (compact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TitleBlock()
            Spacer(Modifier.height(12.dp))
            RuntimeBlock(Alignment.Start)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            TitleBlock(Modifier.weight(1f))
            RuntimeBlock(Alignment.End)
        }
    }
}

@Composable
private fun PresetStrip(
    directBasisPoints: Int,
    upstreamBasisPoints: Int,
    onPreset: (Int, Int) -> Unit,
    compact: Boolean,
) {
    val presets = listOf(
        Triple("CONTROL", 0, 0),
        Triple("BALANCED", 4_200, 5_800),
        Triple("DIRECT+", 7_600, 3_600),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        presets.forEach { (label, direct, upstream) ->
            val active = directBasisPoints == direct && upstreamBasisPoints == upstream
            val color = if (active) SignalCyan else GridLine
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) SignalCyan.copy(alpha = 0.12f) else TerminalPanel)
                    .border(1.dp, color)
                    .clickable(role = Role.Button) { onPreset(direct, upstream) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$label  ${percentFromBasisPoints(direct)} / ${percentFromBasisPoints(upstream)}",
                    color = if (active) SignalCyan else TextMuted,
                    fontFamily = LocalArFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 8.sp else 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InputDeck(
    directBasisPoints: Int,
    upstreamBasisPoints: Int,
    onDirectChange: (Int) -> Unit,
    onUpstreamChange: (Int) -> Unit,
    modifier: Modifier,
) {
    TacticalCard(modifier, accent = TextMuted) {
        SectionLabel("01", "干预输入 / INPUT CHANNELS")
        Spacer(Modifier.height(23.dp))
        InterventionSlider(
            code = "AR-C",
            title = "直接 AR 竞争",
            caption = "改变受体竞争分母；不改变 DHT 生成率",
            basisPoints = directBasisPoints,
            accent = SignalCyan,
            onChange = onDirectChange,
        )
        Spacer(Modifier.height(26.dp))
        InterventionSlider(
            code = "5AR",
            title = "5αR 上游抑制",
            caption = "改变 T→DHT 通量；不占据 AR 位点",
            basisPoints = upstreamBasisPoints,
            accent = UpstreamAmber,
            onChange = onUpstreamChange,
        )
    }
}

@Composable
private fun InterventionSlider(
    code: String,
    title: String,
    caption: String,
    basisPoints: Int,
    accent: Color,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(43.dp)
                .background(accent, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, color = TerminalBlack, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                caption,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = percentFromBasisPoints(basisPoints),
            color = accent,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
        )
    }
    Slider(
        value = basisPoints.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceIn(0, 10_000)) },
        valueRange = 0f..10_000f,
        modifier = Modifier.semantics {
            contentDescription = "$title，当前 ${percentFromBasisPoints(basisPoints)}"
        },
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = GridLine,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SignalGauge(result: ArSuppressionResult, modifier: Modifier) {
    TacticalCard(modifier, accent = SignalCyan) {
        SectionLabel("02", "净信号 / NET SIGNAL")
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fraction = result.signalRelativeToControl.toDouble().coerceIn(0.0, 1.2)
            Canvas(
                modifier = Modifier
                    .size(205.dp)
                    .semantics { contentDescription = "相对对照 AR 信号 ${result.signalRelativeToControl.asPercent()}" },
            ) {
                val stroke = 13.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = GridLine,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Square),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(SignalCyan, Color(0xFFB5FFF7), SignalCyan)),
                    startAngle = 140f,
                    sweepAngle = (260f * fraction.coerceAtMost(1.0)).toFloat(),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Square),
                )
                val marker = Path().apply {
                    moveTo(size.width / 2f, 7.dp.toPx())
                    lineTo(size.width / 2f - 7.dp.toPx(), 20.dp.toPx())
                    lineTo(size.width / 2f + 7.dp.toPx(), 20.dp.toPx())
                    close()
                }
                drawPath(marker, AlertCoral)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = result.signalRelativeToControl.asPercent(0),
                    color = TextPrimary,
                    fontFamily = LocalArFontFamily.current,
                    fontWeight = FontWeight.Black,
                    fontSize = 42.sp,
                )
                Text("OF CONTROL", color = TextMuted, fontFamily = LocalArFontFamily.current, fontSize = 11.sp)
                Spacer(Modifier.height(9.dp))
                StatusTag("不是“总阻断率”", AlertCoral)
            }
        }
    }
}

@Composable
private fun EvidenceRail(result: ArSuppressionResult, modifier: Modifier) {
    TacticalCard(modifier, accent = UpstreamAmber) {
        SectionLabel("03", "证据边界 / EVIDENCE")
        Spacer(Modifier.height(18.dp))
        EvidenceRow("反事实代数", "EXACT", SignalCyan)
        EvidenceRow("参数来源", "ILLUSTRATIVE", UpstreamAmber)
        EvidenceRow("临床外推", "DISALLOWED", AlertCoral)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "DECLARED ASSUMPTIONS",
            color = TextMuted,
            fontFamily = LocalArFontFamily.current,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(8.dp))
        result.exactModelEvidence.assumptions.forEachIndexed { index, assumption ->
            Text(
                text = "A${index + 1}  $assumption",
                color = TextPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (index < result.exactModelEvidence.assumptions.lastIndex) Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = 12.sp)
        StatusTag(value, accent)
    }
}

@Composable
private fun MechanismLanes(result: ArSuppressionResult, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("04", "机制分轨 / MECHANISM LANES")
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MechanismCards(result, Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MechanismCards(result, Modifier.fillMaxWidth())
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "NON-ADDITIVITY  Δ = ${result.nonAdditivity.asSignedPercent()}",
                color = if (result.nonAdditivity >= Rational.ZERO) AlertCoral else TextMuted,
                fontFamily = LocalArFontFamily.current,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MechanismCards(result: ArSuppressionResult, cardModifier: Modifier) {
    MechanismCard(
        code = "DIRECT // AR-C",
        title = "受体位点竞争",
        path = "竞争剂  →  AR 可用位点  →  配体占据",
        contribution = result.directShapleyContribution,
        conditional = result.directConditionalEffect,
        accent = SignalCyan,
        modifier = cardModifier,
    )
    MechanismCard(
        code = "UPSTREAM // 5AR",
        title = "DHT 生成通量改变",
        path = "5αR 活性  →  T→DHT 通量  →  DHT·AR",
        contribution = result.upstreamShapleyContribution,
        conditional = result.upstreamConditionalEffect,
        accent = UpstreamAmber,
        modifier = cardModifier,
    )
}

@Composable
private fun MechanismCard(
    code: String,
    title: String,
    path: String,
    contribution: Rational,
    conditional: Rational,
    accent: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .background(TerminalPanel, CutCornerShape(topEnd = 22.dp, bottomStart = 14.dp))
            .border(1.dp, GridLine, CutCornerShape(topEnd = 22.dp, bottomStart = 14.dp)),
    ) {
        Box(Modifier.fillMaxHeight().width(5.dp).background(accent))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 17.dp, bottom = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(code, color = accent, fontFamily = LocalArFontFamily.current, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text(path, color = TextMuted, fontFamily = LocalArFontFamily.current, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    contribution.asSignedPercent(),
                    color = accent,
                    fontFamily = LocalArFontFamily.current,
                    fontWeight = FontWeight.Black,
                    fontSize = 25.sp,
                )
                Text("SHAPLEY / MODEL", color = TextMuted, fontFamily = LocalArFontFamily.current, fontSize = 9.sp)
                Text("条件反事实 ${conditional.asSignedPercent()}", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CounterfactualDeck(result: ArSuppressionResult, wide: Boolean) {
    val signals = result.counterfactuals
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("05", "四世界反事实 / 2×2 COUNTERFACTUAL")
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterfactualCell("00", "无干预", signals.control, signals.control, TextMuted, Modifier.weight(1f))
                CounterfactualCell("10", "仅直接竞争", signals.directOnly, signals.control, SignalCyan, Modifier.weight(1f))
                CounterfactualCell("01", "仅 5αR", signals.upstreamOnly, signals.control, UpstreamAmber, Modifier.weight(1f))
                CounterfactualCell("11", "联合世界", signals.combined, signals.control, AlertCoral, Modifier.weight(1f))
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterfactualCell("00", "无干预", signals.control, signals.control, TextMuted, Modifier.weight(1f))
                CounterfactualCell("10", "仅直接竞争", signals.directOnly, signals.control, SignalCyan, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterfactualCell("01", "仅 5αR", signals.upstreamOnly, signals.control, UpstreamAmber, Modifier.weight(1f))
                CounterfactualCell("11", "联合世界", signals.combined, signals.control, AlertCoral, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CounterfactualCell(
    code: String,
    label: String,
    signal: Rational,
    control: Rational,
    accent: Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(TerminalRaised, RoundedCornerShape(2.dp))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WORLD $code", color = accent, fontFamily = LocalArFontFamily.current, fontSize = 10.sp)
            Text((signal / control).asPercent(), color = TextPrimary, fontFamily = LocalArFontFamily.current, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("raw = $signal", color = TextMuted, fontFamily = LocalArFontFamily.current, fontSize = 9.sp)
    }
}

@Composable
private fun ModelBoundaryFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlertCoral.copy(alpha = 0.1f))
            .border(1.dp, AlertCoral.copy(alpha = 0.65f))
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", color = AlertCoral, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            "研究模拟：默认值仅用于检验机制与代码路径；不表示个体药效、临床疗效、剂量建议或风险结论。",
            color = TextPrimary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TacticalCard(modifier: Modifier, accent: Color, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(TerminalPanel.copy(alpha = 0.96f), CutCornerShape(topEnd = 26.dp, bottomStart = 16.dp))
            .border(1.dp, GridLine, CutCornerShape(topEnd = 26.dp, bottomStart = 16.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
        Column(Modifier.fillMaxSize().padding(20.dp), content = { content() })
    }
}

@Composable
private fun SectionLabel(index: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = index,
            color = TerminalBlack,
            modifier = Modifier.background(TextPrimary).padding(horizontal = 7.dp, vertical = 2.dp),
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = TextMuted,
            fontFamily = LocalArFontFamily.current,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
        )
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 3.dp),
        fontFamily = LocalArFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 0.7.sp,
    )
}

private fun Rational.asPercent(digits: Int = 1): String =
    "${fixed(toDouble() * 100.0, digits)}%"

private fun Rational.asSignedPercent(): String =
    "${fixed(toDouble() * 100.0, 1, alwaysSign = true)}%"

private fun percentFromBasisPoints(value: Int): String =
    "${fixed(value / 100.0, 1)}%"

private fun fixed(value: Double, digits: Int, alwaysSign: Boolean = false): String {
    val scale = when (digits) {
        0 -> 1L
        1 -> 10L
        2 -> 100L
        else -> error("Unsupported display precision: $digits")
    }
    val scaled = (abs(value) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = scaled % scale
    val sign = when {
        value < 0.0 -> "−"
        alwaysSign -> "+"
        else -> ""
    }
    return if (digits == 0) {
        "$sign$whole"
    } else {
        "$sign$whole.${fraction.toString().padStart(digits, '0')}"
    }
}
