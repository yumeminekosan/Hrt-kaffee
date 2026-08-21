package dev.hrtkaffee.ar.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hrtkaffee.ar.web.model.MolecularBindingTheoryMap
import dev.hrtkaffee.ar.web.model.TheoryNode
import dev.hrtkaffee.ar.web.model.TheoryNodeId
import dev.hrtkaffee.ar.web.model.TheoryStageStatus
import dev.hrtkaffee.ar.web.model.WebThermalDeBroglie
import kotlin.math.abs
import kotlin.math.roundToLong

private val FlowPanel = Color(0xFF10171D)
private val FlowRaised = Color(0xFF172129)
private val FlowGrid = Color(0xFF2A3842)
private val FlowCyan = Color(0xFF54E6E0)
private val FlowAmber = Color(0xFFFFC857)
private val FlowCoral = Color(0xFFFF6B5F)
private val FlowViolet = Color(0xFFC792EA)
private val FlowText = Color(0xFFF4F7F7)
private val FlowMuted = Color(0xFF97A7B0)

@Composable
internal fun MolecularTheoryFlowPanel(
    wide: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var massDalton by remember { mutableIntStateOf(1) }
    var temperatureKelvin by remember { mutableIntStateOf(310) }
    val quantumScale = remember(massDalton, temperatureKelvin) {
        WebThermalDeBroglie.evaluate(massDalton.toDouble(), temperatureKelvin.toDouble())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(FlowPanel.copy(alpha = 0.97f), CutCornerShape(topEnd = 30.dp, bottomStart = 20.dp))
            .border(1.dp, FlowGrid, CutCornerShape(topEnd = 30.dp, bottomStart = 20.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(FlowViolet))
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            FlowHeader(compact)
            QuantumTelemetry(
                massDalton = massDalton,
                temperatureKelvin = temperatureKelvin,
                wavelengthPicometres = quantumScale.wavelengthPicometres,
                onMassChange = { massDalton = it },
                onTemperatureChange = { temperatureKelvin = it },
                wide = wide,
            )

            FlowChain(
                ids = listOf(
                    TheoryNodeId.QUANTUM_SCALE,
                    TheoryNodeId.QUANTUM_CALIBRATION,
                    TheoryNodeId.DISCRETE_CTMC,
                ),
                horizontal = wide,
            )

            FlowConnector("同一反应表 → 生成元 / 反应钟 / 跳鞅")
            FlowNodeCard(MolecularBindingTheoryMap.node(TheoryNodeId.JUMP_STRUCTURE))

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowLane(
                        title = "LAW OF LARGE NUMBERS",
                        ids = listOf(TheoryNodeId.FLUID_LIMIT),
                        modifier = Modifier.weight(0.8f),
                    )
                    FlowLane(
                        title = "RARE TRAJECTORY GEOMETRY",
                        ids = listOf(
                            TheoryNodeId.NONLINEAR_GENERATOR,
                            TheoryNodeId.HAMILTONIAN_HJ,
                            TheoryNodeId.METASTABILITY,
                        ),
                        modifier = Modifier.weight(1.25f),
                    )
                    FlowLane(
                        title = "CONDITIONED PATHS",
                        ids = listOf(
                            TheoryNodeId.TILTED_OPERATOR,
                            TheoryNodeId.DOOB_TRANSFORM,
                            TheoryNodeId.DRIVEN_GILLESPIE,
                        ),
                        modifier = Modifier.weight(1.15f),
                    )
                }
            } else {
                FlowLane("LAW OF LARGE NUMBERS", listOf(TheoryNodeId.FLUID_LIMIT))
                FlowLane(
                    "RARE TRAJECTORY GEOMETRY",
                    listOf(
                        TheoryNodeId.NONLINEAR_GENERATOR,
                        TheoryNodeId.HAMILTONIAN_HJ,
                        TheoryNodeId.METASTABILITY,
                    ),
                )
                FlowLane(
                    "CONDITIONED PATHS",
                    listOf(
                        TheoryNodeId.TILTED_OPERATOR,
                        TheoryNodeId.DOOB_TRANSFORM,
                        TheoryNodeId.DRIVEN_GILLESPIE,
                    ),
                )
            }

            Text(
                text = "PARALLEL LIFTS FROM NODE A",
                color = FlowMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.3.sp,
            )
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowLane(
                        "SPATIAL",
                        listOf(TheoryNodeId.SPATIAL_PARTICLES, TheoryNodeId.HYDRODYNAMIC_PDE),
                        Modifier.weight(1f),
                    )
                    FlowLane(
                        "TOPOLOGY",
                        listOf(TheoryNodeId.CHAIN_COMPLEX),
                        Modifier.weight(1f),
                    )
                    FlowLane(
                        "LIVE PROJECTION",
                        listOf(TheoryNodeId.LIVE_EQUILIBRIUM_PROJECTION),
                        Modifier.weight(1f),
                    )
                }
            } else {
                FlowLane("SPATIAL", listOf(TheoryNodeId.SPATIAL_PARTICLES, TheoryNodeId.HYDRODYNAMIC_PDE))
                FlowLane("TOPOLOGY", listOf(TheoryNodeId.CHAIN_COMPLEX))
                FlowLane("LIVE PROJECTION", listOf(TheoryNodeId.LIVE_EQUILIBRIUM_PROJECTION))
            }

            QuantumBoundaryNotice()
            ReferenceRail(wide)
        }
    }
}

@Composable
private fun FlowHeader(compact: Boolean) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FlowTitle()
            FlowStatusLegend()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            FlowTitle()
            FlowStatusLegend()
        }
    }
}

@Composable
private fun FlowTitle() {
    Column {
        Text(
            text = "00  FLOWCHART TD // AUDITED",
            color = FlowViolet,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = "分子结合：微观跳过程 → 极限、稀有事件与空间拓扑",
            color = FlowText,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun FlowStatusLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowTag("EXACT CORE", FlowCyan)
        FlowTag("GATED", FlowAmber)
        FlowTag("INPUT OPEN", FlowCoral)
    }
}

@Composable
private fun QuantumTelemetry(
    massDalton: Int,
    temperatureKelvin: Int,
    wavelengthPicometres: Double,
    onMassChange: (Int) -> Unit,
    onTemperatureChange: (Int) -> Unit,
    wide: Boolean,
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuantumSlider(
                label = "核质量 / u",
                value = massDalton,
                range = 1..128,
                onChange = onMassChange,
                modifier = Modifier.weight(1f),
            )
            QuantumSlider(
                label = "温度 / K",
                value = temperatureKelvin,
                range = 20..400,
                onChange = onTemperatureChange,
                modifier = Modifier.weight(1f),
            )
            QuantumReadout(wavelengthPicometres, Modifier.weight(0.8f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuantumSlider("核质量 / u", massDalton, 1..128, onMassChange)
            QuantumSlider("温度 / K", temperatureKelvin, 20..400, onTemperatureChange)
            QuantumReadout(wavelengthPicometres, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QuantumSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(FlowRaised, RoundedCornerShape(2.dp))
            .border(1.dp, FlowGrid, RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = FlowMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("$value", color = FlowViolet, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = "$label，当前 $value" },
            colors = SliderDefaults.colors(
                thumbColor = FlowViolet,
                activeTrackColor = FlowViolet,
                inactiveTrackColor = FlowGrid,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun QuantumReadout(wavelengthPicometres: Double, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(FlowViolet.copy(alpha = 0.1f), CutCornerShape(topEnd = 15.dp, bottomStart = 10.dp))
            .border(1.dp, FlowViolet.copy(alpha = 0.75f), CutCornerShape(topEnd = 15.dp, bottomStart = 10.dp))
            .padding(14.dp),
    ) {
        Text("THERMAL λₜₕ", color = FlowMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text(
            "${decimal(wavelengthPicometres, 1)} pm",
            color = FlowViolet,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
        )
        Text("h / √(2πmkBT)", color = FlowMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    }
}

@Composable
private fun FlowChain(ids: List<TheoryNodeId>, horizontal: Boolean) {
    if (horizontal) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ids.forEachIndexed { index, id ->
                FlowNodeCard(MolecularBindingTheoryMap.node(id), Modifier.weight(1f))
                if (index < ids.lastIndex) {
                    Text("→", color = FlowViolet, fontWeight = FontWeight.Black, fontSize = 19.sp)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ids.forEachIndexed { index, id ->
                FlowNodeCard(MolecularBindingTheoryMap.node(id))
                if (index < ids.lastIndex) FlowConnector("↓")
            }
        }
    }
}

@Composable
private fun FlowLane(
    title: String,
    ids: List<TheoryNodeId>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
            .border(1.dp, FlowGrid.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            color = FlowMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ids.forEachIndexed { index, id ->
            FlowNodeCard(MolecularBindingTheoryMap.node(id))
            if (index < ids.lastIndex) FlowConnector("↓")
        }
    }
}

@Composable
private fun FlowNodeCard(node: TheoryNode, modifier: Modifier = Modifier) {
    val accent = statusColor(node.status)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FlowRaised, CutCornerShape(topEnd = 13.dp, bottomStart = 8.dp))
            .border(1.dp, accent.copy(alpha = 0.58f), CutCornerShape(topEnd = 13.dp, bottomStart = 8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(31.dp)
                .background(accent)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                node.code,
                color = Color(0xFF080B0F),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                node.title,
                color = FlowText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                node.detail,
                color = FlowMuted,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                statusLabel(node.status),
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun FlowConnector(label: String) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        color = FlowViolet,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun QuantumBoundaryNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FlowCoral.copy(alpha = 0.08f))
            .border(1.dp, FlowCoral.copy(alpha = 0.64f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("Q!", color = FlowCoral, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                "λₜₕ 不是结合自由能，也不是“结合增强率”。",
                color = FlowText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Text(
                "轻核、低温会增大离域尺度；但零点能、隧穿和不同振动模的竞争可增强或削弱结合。当前 AR 跳率保持不变，直到提供经审计的 ΔΔGbind / ΔΔG‡。",
                color = FlowMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun ReferenceRail(wide: Boolean) {
    val references = MolecularBindingTheoryMap.references
    if (wide) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            references.forEach { reference ->
                ReferenceCell(reference.code, reference.citation, reference.locator, Modifier.weight(1f))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            references.forEach { reference ->
                ReferenceCell(reference.code, reference.citation, reference.locator, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReferenceCell(code: String, citation: String, locator: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, FlowGrid)
            .padding(8.dp),
    ) {
        Text(code, color = FlowCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        Text(citation, color = FlowText, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(locator, color = FlowMuted, fontFamily = FontFamily.Monospace, fontSize = 7.sp)
    }
}

@Composable
private fun FlowTag(text: String, color: Color) {
    Text(
        text,
        color = color,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 3.dp),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 7.sp,
    )
}

private fun statusColor(status: TheoryStageStatus): Color = when (status) {
    TheoryStageStatus.EXACT_CORE -> FlowCyan
    TheoryStageStatus.CONDITIONAL_THEOREM -> FlowAmber
    TheoryStageStatus.NUMERICAL_CERTIFICATE -> FlowViolet
    TheoryStageStatus.INPUT_REQUIRED -> FlowCoral
    TheoryStageStatus.LIVE_PROJECTION -> Color(0xFF8FE388)
}

private fun statusLabel(status: TheoryStageStatus): String = when (status) {
    TheoryStageStatus.EXACT_CORE -> "IMPLEMENTED · EXACT STRUCTURE"
    TheoryStageStatus.CONDITIONAL_THEOREM -> "THEOREM · NAMED GATES REQUIRED"
    TheoryStageStatus.NUMERICAL_CERTIFICATE -> "NUMERICAL · RESIDUAL REQUIRED"
    TheoryStageStatus.INPUT_REQUIRED -> "PHYSICAL INPUT REQUIRED"
    TheoryStageStatus.LIVE_PROJECTION -> "LIVE · REDUCED MODEL ONLY"
}

private fun decimal(value: Double, digits: Int): String {
    val scale = when (digits) {
        0 -> 1L
        1 -> 10L
        else -> error("Unsupported display precision: $digits")
    }
    val scaled = (abs(value) * scale).roundToLong()
    val sign = if (value < 0.0) "−" else ""
    val whole = scaled / scale
    return if (digits == 0) "$sign$whole" else "$sign$whole.${(scaled % scale).toString().padStart(digits, '0')}"
}
