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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hrtkaffee.ar.web.model.FinasterideCurvePoint
import dev.hrtkaffee.ar.web.model.FinasterideKineticModel
import dev.hrtkaffee.ar.web.model.FinasterideKineticResult
import dev.hrtkaffee.ar.web.model.FinasterideRegimen
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val FinBackground = Color(0xFF080B0F)
private val FinPanel = Color(0xFF11171D)
private val FinRaised = Color(0xFF172129)
private val FinGrid = Color(0xFF2A3842)
private val FinCyan = Color(0xFF54E6E0)
private val FinAmber = Color(0xFFFFC857)
private val FinCoral = Color(0xFFFF6B5F)
private val FinText = Color(0xFFF4F7F7)
private val FinMuted = Color(0xFF97A7B0)

@Composable
internal fun FinasterideKineticsPanel(modifier: Modifier = Modifier) {
    val model = remember { FinasterideKineticModel() }
    var doseCentiMg by remember { mutableIntStateOf(1_500) }
    var days by remember { mutableIntStateOf(14) }
    val result = remember(doseCentiMg, days) {
        model.simulate(
            FinasterideRegimen(
                dailyDoseMg = doseCentiMg / 100.0,
                days = days,
            ),
        )
    }

    BoxWithConstraints(modifier.fillMaxSize().background(FinBackground)) {
        val compact = maxWidth < 720.dp
        val wide = maxWidth >= 1080.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 16.dp else 34.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FinasterideHeader(compact)
            DosePresets(doseCentiMg) { doseCentiMg = it }
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RegimenCard(
                        doseCentiMg = doseCentiMg,
                        days = days,
                        onDoseChange = { doseCentiMg = it },
                        onDaysChange = { days = it },
                        modifier = Modifier.weight(0.9f),
                    )
                    ResultCard(result, Modifier.weight(1.1f))
                }
            } else {
                RegimenCard(
                    doseCentiMg = doseCentiMg,
                    days = days,
                    onDoseChange = { doseCentiMg = it },
                    onDaysChange = { days = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ResultCard(result, Modifier.fillMaxWidth())
            }
            CurveCard(result)
            FinasterideBoundary(result.regimen.isRepeatedDoseReferenceDomain)
        }
    }
}

@Composable
private fun FinasterideHeader(compact: Boolean) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            HeaderTitle()
            LiveTag()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            HeaderTitle()
            LiveTag()
        }
    }
}

@Composable
private fun HeaderTitle() {
    Column {
        Text(
            text = "K/5AR // FINASTERIDE KINETICS",
            color = FinCyan,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
        )
        Text(
            text = "非那雄胺 · 结合占有与 DHT 时间曲线",
            color = FinText,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
        )
        Text(
            text = "输入每日剂量与观察天数；计算每日口服后的动态结果。",
            color = FinMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun LiveTag() {
    Box(
        modifier = Modifier.border(1.dp, FinCyan).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = "KOTLIN/WASM · LIVE",
            color = FinCyan,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun DosePresets(activeDoseCentiMg: Int, onSelect: (Int) -> Unit) {
    val presets = listOf(
        20 to "0.2 mg",
        100 to "1 mg",
        500 to "5 mg",
        1_500 to "15 mg",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (dose, label) ->
            val active = dose == activeDoseCentiMg
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) FinCyan.copy(alpha = 0.13f) else FinPanel)
                    .border(1.dp, if (active) FinCyan else FinGrid)
                    .clickable(role = Role.Button) { onSelect(dose) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) FinCyan else FinMuted,
                    fontFamily = LocalArFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun RegimenCard(
    doseCentiMg: Int,
    days: Int,
    onDoseChange: (Int) -> Unit,
    onDaysChange: (Int) -> Unit,
    modifier: Modifier,
) {
    FinCard(modifier, FinAmber) {
        SectionTitle("01", "输入 / REGIMEN")
        Spacer(Modifier.height(18.dp))
        SliderField(
            label = "每日口服剂量",
            value = "${decimal(doseCentiMg / 100.0, 2)} mg/day",
            sliderValue = doseCentiMg.toFloat(),
            valueRange = 0f..2_000f,
            onValueChange = { onDoseChange(it.roundToInt().coerceIn(0, 2_000)) },
            accent = FinAmber,
        )
        Spacer(Modifier.height(17.dp))
        SliderField(
            label = "连续观察",
            value = "$days days",
            sliderValue = days.toFloat(),
            valueRange = 1f..42f,
            onValueChange = { onDaysChange(it.roundToInt().coerceIn(1, 42)) },
            accent = FinCyan,
        )
        Text(
            text = "给药间隔固定为 24 h",
            color = FinMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SliderField(
    label: String,
    value: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accent: Color,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = FinText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            value,
            color = accent,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
    Slider(
        value = sliderValue,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = FinGrid,
        ),
    )
}

@Composable
private fun ResultCard(result: FinasterideKineticResult, modifier: Modifier) {
    FinCard(modifier, FinCyan) {
        SectionTitle("02", "结果 / CURRENT ENDPOINT")
        Spacer(Modifier.height(16.dp))
        Text(
            text = percent(result.finalPoint.serumDhtSuppressionFraction),
            color = FinCyan,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Black,
            fontSize = 38.sp,
        )
        Text("观察终点血清 DHT 抑制", color = FinMuted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("峰值 DHT 抑制", percent(result.peakDhtSuppressionFraction), FinCyan, Modifier.weight(1f))
            Metric("5αR2 结合占有", percent(result.finalPoint.type2OccupancyFraction), FinAmber, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("5αR1 抑制", percent(result.finalPoint.type1InhibitionFraction), FinCoral, Modifier.weight(1f))
            Metric("游离药物浓度", "${decimal(result.finalPoint.plasmaConcentrationNm, 1)} nM", FinText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(FinRaised).padding(10.dp)) {
        Text(label, color = FinMuted, fontSize = 9.sp)
        Text(
            value,
            color = accent,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun CurveCard(result: FinasterideKineticResult) {
    FinCard(Modifier.fillMaxWidth(), FinCyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionTitle("03", "动态曲线 / TIME COURSE")
            Text("0–100%", color = FinMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("DHT 抑制", FinCyan)
            Legend("5αR2 占有", FinAmber)
            Legend("5αR1 抑制", FinCoral)
        }
        Spacer(Modifier.height(10.dp))
        KineticChart(result.curve)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DAY 0", color = FinMuted, fontSize = 9.sp)
            Text("DAY ${result.regimen.days}", color = FinMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp).height(2.dp).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = FinMuted, fontSize = 10.sp)
    }
}

@Composable
private fun KineticChart(points: List<FinasterideCurvePoint>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .background(FinBackground)
            .border(1.dp, FinGrid)
            .padding(10.dp),
    ) {
        for (index in 0..4) {
            val fraction = index / 4f
            drawLine(
                color = FinGrid.copy(alpha = 0.65f),
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
                strokeWidth = 1f,
            )
            drawLine(
                color = FinGrid.copy(alpha = 0.35f),
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
                strokeWidth = 1f,
            )
        }
        val maximumTime = points.lastOrNull()?.timeHours?.coerceAtLeast(1.0) ?: 1.0
        fun pathFor(value: (FinasterideCurvePoint) -> Double): Path = Path().apply {
            points.forEachIndexed { index, point ->
                val x = (point.timeHours / maximumTime * size.width).toFloat()
                val y = ((1.0 - value(point).coerceIn(0.0, 1.0)) * size.height).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(pathFor { it.serumDhtSuppressionFraction }, FinCyan, style = Stroke(3f, cap = StrokeCap.Round))
        drawPath(pathFor { it.type2OccupancyFraction }, FinAmber, style = Stroke(2.2f, cap = StrokeCap.Round))
        drawPath(pathFor { it.type1InhibitionFraction }, FinCoral, style = Stroke(2.2f, cap = StrokeCap.Round))
    }
}

@Composable
private fun FinasterideBoundary(inReferenceDomain: Boolean) {
    val accent = if (inReferenceDomain) FinAmber else FinCoral
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
            .border(1.dp, accent.copy(alpha = 0.55f), CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
            .padding(15.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = if (inReferenceDomain) "POPULATION MODEL" else "EXTRAPOLATED DOSE",
                color = accent,
                fontFamily = LocalArFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = if (inReferenceDomain) {
                    "非那雄胺作用于 5α-还原酶并降低 T→DHT 通量；它不是直接 AR 拮抗剂。"
                } else {
                    "每日剂量高于 5 mg：曲线是模型外推，不表示额外临床收益；非那雄胺也不是直接 AR 拮抗剂。"
                },
                color = FinText,
                fontSize = 11.sp,
            )
            Text(
                text = "参数：Suzuki et al. 2010, DOI 10.2133/dmpk.25.208 · 结构：PDB 7BW1 · 仅供研究模拟",
                color = FinMuted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun FinCard(modifier: Modifier, accent: Color, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(FinPanel, CutCornerShape(topEnd = 22.dp, bottomStart = 14.dp))
            .border(1.dp, FinGrid, CutCornerShape(topEnd = 22.dp, bottomStart = 14.dp))
            .padding(16.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(accent).align(Alignment.TopCenter))
        Column(modifier = Modifier.padding(top = 8.dp), content = { content() })
    }
}

@Composable
private fun SectionTitle(index: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = index,
            color = FinBackground,
            modifier = Modifier.background(FinCyan).padding(horizontal = 7.dp, vertical = 3.dp),
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = FinMuted,
            fontFamily = LocalArFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
    }
}

private fun percent(value: Double): String = "${decimal(value * 100.0, 1)}%"

private fun decimal(value: Double, digits: Int): String {
    val scale = when (digits) {
        2 -> 100L
        1 -> 10L
        else -> 1L
    }
    val scaled = (abs(value) * scale).roundToLong()
    val sign = if (value < 0.0) "−" else ""
    val whole = scaled / scale
    if (digits == 0) return "$sign$whole"
    return "$sign$whole.${(scaled % scale).toString().padStart(digits, '0')}"
}
