package dev.hrtkaffee.ar.embedded

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import kotlin.js.Date
import kotlin.math.roundToInt

private data class FiveArElements(
    val panel: HTMLElement,
    val drug: HTMLSelectElement,
    val dose: HTMLInputElement,
    val days: HTMLInputElement,
    val endpoint: HTMLElement,
    val peak: HTMLElement,
    val type1: HTMLElement,
    val type2: HTMLElement,
    val concentration: HTMLElement,
    val boundary: HTMLElement,
    val reference: HTMLAnchorElement,
    val canvas: HTMLCanvasElement,
)

fun main() {
    bindFiveArModule()
    bindProgestogenModule()
}

private fun bindFiveArModule() {
    val elements = findElements() ?: return
    var selectedDrug = FiveArDrug.fromWireId(elements.drug.value)
    var finasterideDose = elements.dose.value.toDoubleOrNull() ?: 15.0
    var dutasterideDose = FiveArDrug.DUTASTERIDE.defaultDoseMg
    var latest: FiveArProjection? = null

    fun render() {
        val dose = (elements.dose.value.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 20.0)
        val days = (elements.days.value.toIntOrNull() ?: 14).coerceIn(1, 365)
        elements.panel.setAttribute("aria-busy", "true")
        val result = EmbeddedFiveArModel.simulate(selectedDrug, dose, days)
        latest = result
        elements.endpoint.textContent = percent(result.endpoint.dhtSuppressionFraction)
        elements.peak.textContent = percent(result.peakDhtSuppressionFraction)
        elements.type1.textContent = percent(result.endpoint.type1InhibitionFraction)
        elements.type2.textContent = percent(result.endpoint.type2InhibitionFraction)
        elements.concentration.textContent = concentration(result.endpoint.concentrationNm)
        elements.boundary.textContent = result.boundaryMessage
        elements.boundary.classList.toggle("extrapolated", !result.isReferenceDomain)
        elements.reference.textContent = result.referenceLabel
        elements.reference.href = result.referenceUrl
        drawChart(elements.canvas, result)
        elements.panel.setAttribute("aria-busy", "false")
    }

    elements.drug.addEventListener("change", { _: Event ->
        when (selectedDrug) {
            FiveArDrug.FINASTERIDE -> finasterideDose =
                elements.dose.value.toDoubleOrNull() ?: finasterideDose
            FiveArDrug.DUTASTERIDE -> dutasterideDose =
                elements.dose.value.toDoubleOrNull() ?: dutasterideDose
        }
        selectedDrug = FiveArDrug.fromWireId(elements.drug.value)
        elements.dose.value = when (selectedDrug) {
            FiveArDrug.FINASTERIDE -> finasterideDose
            FiveArDrug.DUTASTERIDE -> dutasterideDose
        }.displayNumber()
        render()
    })
    elements.dose.addEventListener("change", { _: Event -> render() })
    elements.days.addEventListener("change", { _: Event -> render() })
    window.addEventListener("resize", { _: Event -> latest?.let { drawChart(elements.canvas, it) } })
    render()
}

private data class ProgestogenElements(
    val panel: HTMLElement,
    val results: HTMLElement,
    val drug: HTMLSelectElement,
    val dose: HTMLInputElement,
    val interval: HTMLInputElement,
    val days: HTMLInputElement,
    val threshold: HTMLSelectElement,
    val lastDoseAt: HTMLInputElement,
    val endpoint: HTMLElement,
    val peak: HTMLElement,
    val occupancy: HTMLElement,
    val activity: HTMLElement,
    val concentration: HTMLElement,
    val peakConcentration: HTMLElement,
    val dailyExposure: HTMLElement,
    val coverageDuration: HTMLElement,
    val coverageDate: HTMLElement,
    val coverageStatus: HTMLElement,
    val coverageFill: HTMLElement,
    val boundary: HTMLElement,
    val reference: HTMLAnchorElement,
    val canvas: HTMLCanvasElement,
)

private data class ProgestogenFormState(
    val doseMg: Double,
    val intervalHours: Double,
    val days: Int,
)

private fun bindProgestogenModule() {
    val elements = findProgestogenElements() ?: return
    var latest: EmbeddedProgestogenProjection? = null
    var selectedDrug: EmbeddedProgestogen? = null
    val storedStates = mutableMapOf<EmbeddedProgestogen, ProgestogenFormState>()
    if (elements.lastDoseAt.value.isBlank()) {
        elements.lastDoseAt.value = currentLocalDateTimeValue()
    }

    fun hide() {
        elements.results.style.display = "none"
        elements.panel.setAttribute("aria-busy", "false")
    }

    fun formState(ligand: EmbeddedProgestogen): ProgestogenFormState = ProgestogenFormState(
        doseMg = boundedInput(elements.dose.value, ligand.defaultDoseMg, 0.0, 500.0),
        intervalHours = boundedInput(elements.interval.value, 24.0, 4.0, 168.0),
        days = boundedInput(elements.days.value, 14.0, 1.0, 365.0).roundToInt(),
    )

    fun setFormState(state: ProgestogenFormState) {
        elements.dose.value = state.doseMg.displayNumber()
        elements.interval.value = state.intervalHours.displayNumber()
        elements.days.value = state.days.toString()
    }

    fun render() {
        if (elements.drug.value == "none") {
            hide()
            return
        }
        val ligand = EmbeddedProgestogen.fromWireId(elements.drug.value)
        val state = formState(ligand)
        if (selectedDrug != ligand) {
            selectedDrug?.let { previous -> storedStates[previous] = formState(previous) }
            val restored = storedStates[ligand] ?: ProgestogenFormState(
                doseMg = ligand.defaultDoseMg,
                intervalHours = 24.0,
                days = 14,
            )
            selectedDrug = ligand
            setFormState(restored)
            render()
            return
        }
        setFormState(state)
        elements.panel.setAttribute("aria-busy", "true")
        val result = EmbeddedProgestogenGnRHModel.simulate(
            ligand,
            state.doseMg,
            state.intervalHours,
            state.days,
        )
        val threshold = boundedInput(elements.threshold.value, 0.10, 0.01, 0.80)
        val coverage = EmbeddedProgestogenGnRHModel.estimateCoverageAfterLastDose(
            ligand,
            state.doseMg,
            state.intervalHours,
            state.days,
            threshold,
        )
        latest = result
        elements.endpoint.textContent = percent(result.endpoint.gnrhPulseSuppressionFraction)
        elements.peak.textContent = percent(result.peakGnRHPulseSuppressionFraction)
        elements.occupancy.textContent = percent(result.endpoint.prOccupancyFraction)
        elements.activity.textContent = percent(result.endpoint.gnrhPulseActivityFraction)
        elements.concentration.textContent = concentrationNgMl(
            result.endpoint.plasmaConcentrationNgPerMl,
        )
        elements.peakConcentration.textContent = concentrationNgMl(
            result.curve.maxOf { point -> point.plasmaConcentrationNgPerMl },
        )
        elements.dailyExposure.textContent = "${(state.doseMg * 24.0 / state.intervalHours).displayNumber()} mg/day"
        elements.coverageDuration.textContent = when {
            !coverage.reachesThreshold -> "< ${percent(threshold)}"
            else -> duration(coverage.timeUntilFinalBelowThresholdHours)
        }
        elements.coverageStatus.textContent = when {
            !coverage.reachesThreshold ->
                "末次给药后，模型峰值未达到 ${percent(threshold)} 的 GnRH 反馈阈值。"
            else ->
                "末次给药后，PR–GnRH 反馈最后一次降至 ${percent(threshold)} 以下。"
        }
        elements.coverageDate.textContent = projectedDateTime(
            elements.lastDoseAt.value,
            coverage.timeUntilFinalBelowThresholdHours,
            coverage.reachesThreshold,
        )
        elements.coverageFill.style.width = "${(
            (coverage.timeUntilFinalBelowThresholdHours / coverage.searchHorizonHours)
                .coerceIn(0.0, 1.0) * 100.0
            ).roundToInt()}%"
        elements.boundary.textContent = result.boundaryMessage
        elements.boundary.classList.toggle("extrapolated", !result.isReferenceDomain)
        elements.reference.textContent = result.referenceLabel
        elements.reference.href = result.referenceUrl
        elements.results.style.display = "block"
        drawProgestogenChart(elements.canvas, result)
        elements.panel.setAttribute("aria-busy", "false")
    }

    elements.drug.addEventListener("change", { _: Event -> render() })
    listOf(elements.dose, elements.interval, elements.days).forEach { input ->
        input.addEventListener("input", { _: Event ->
            if (input.value.toDoubleOrNull()?.isFinite() == true) render()
        })
        input.addEventListener("change", { _: Event -> render() })
    }
    elements.threshold.addEventListener("change", { _: Event -> render() })
    elements.lastDoseAt.addEventListener("change", { _: Event -> render() })
    window.addEventListener("resize", { _: Event ->
        latest?.let { drawProgestogenChart(elements.canvas, it) }
    })
    render()
}

private fun findProgestogenElements(): ProgestogenElements? {
    fun element(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement
    return ProgestogenElements(
        panel = element("progestogenInteractionPanel") ?: return null,
        results = element("pgGnrhResults") ?: return null,
        drug = element("progestogenSelect") as? HTMLSelectElement ?: return null,
        dose = element("progestogenDose") as? HTMLInputElement ?: return null,
        interval = element("progestogenInterval") as? HTMLInputElement ?: return null,
        days = element("progestogenDays") as? HTMLInputElement ?: return null,
        threshold = element("pgCoverageThreshold") as? HTMLSelectElement ?: return null,
        lastDoseAt = element("pgLastDoseAt") as? HTMLInputElement ?: return null,
        endpoint = element("pgGnrhEffect") ?: return null,
        peak = element("pgGnrhPeak") ?: return null,
        occupancy = element("pgPrOccupancy") ?: return null,
        activity = element("pgGnrhActivity") ?: return null,
        concentration = element("pgGnrhConcentration") ?: return null,
        peakConcentration = element("pgPeakConcentration") ?: return null,
        dailyExposure = element("pgDailyExposure") ?: return null,
        coverageDuration = element("pgCoverageDuration") ?: return null,
        coverageDate = element("pgCoverageDate") ?: return null,
        coverageStatus = element("pgCoverageStatus") ?: return null,
        coverageFill = element("pgCoverageFill") ?: return null,
        boundary = element("pgGnrhDomain") ?: return null,
        reference = element("pgGnrhReference") as? HTMLAnchorElement ?: return null,
        canvas = element("pgGnrhChart") as? HTMLCanvasElement ?: return null,
    )
}

private fun findElements(): FiveArElements? {
    fun element(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement
    return FiveArElements(
        panel = element("fiveArModule") ?: return null,
        drug = element("targetDrugSelect") as? HTMLSelectElement ?: return null,
        dose = element("targetDoseInput") as? HTMLInputElement ?: return null,
        days = element("targetDaysInput") as? HTMLInputElement ?: return null,
        endpoint = element("targetEffectValue") ?: return null,
        peak = element("targetPeakValue") ?: return null,
        type1 = element("targetType1Value") ?: return null,
        type2 = element("targetType2Value") ?: return null,
        concentration = element("targetConcentrationValue") ?: return null,
        boundary = element("targetEffectDomain") ?: return null,
        reference = element("targetEffectReference") as? HTMLAnchorElement ?: return null,
        canvas = element("fiveArChart") as? HTMLCanvasElement ?: return null,
    )
}

private fun percent(fraction: Double): String =
    "${(fraction.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"

private fun concentration(valueNm: Double): String = when {
    valueNm < 0.01 -> "<0.01 nM"
    valueNm < 10.0 -> "${(valueNm * 100.0).roundToInt() / 100.0} nM"
    else -> "${(valueNm * 10.0).roundToInt() / 10.0} nM"
}

private fun concentrationNgMl(value: Double): String = when {
    value < 0.01 -> "<0.01 ng/mL"
    value < 10.0 -> "${(value * 100.0).roundToInt() / 100.0} ng/mL"
    else -> "${(value * 10.0).roundToInt() / 10.0} ng/mL"
}

private fun boundedInput(value: String, fallback: Double, minimum: Double, maximum: Double): Double =
    value.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(minimum, maximum) ?: fallback

private fun duration(hours: Double): String {
    val wholeMinutes = (hours.coerceAtLeast(0.0) * 60.0).roundToInt()
    val days = wholeMinutes / (24 * 60)
    val remainingHours = (wholeMinutes % (24 * 60)) / 60
    val minutes = wholeMinutes % 60
    return when {
        days > 0 -> "${days}d ${remainingHours}h"
        remainingHours > 0 -> "${remainingHours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun projectedDateTime(
    dateTimeLocal: String,
    delayHours: Double,
    reachesThreshold: Boolean,
): String = when {
    !reachesThreshold -> "无覆盖截止时刻：所选阈值未被达到。"
    dateTimeLocal.isBlank() -> "填写末次给药时间即可映射到日历。"
    else -> {
        val start = Date(dateTimeLocal).getTime()
        if (!start.isFinite()) {
            "末次给药时间格式无效。"
        } else {
            "预计 ${Date(start + delayHours * 3_600_000.0).toLocaleString()}"
        }
    }
}

private fun currentLocalDateTimeValue(): String {
    val now = Date()
    val local = Date(now.getTime() - now.getTimezoneOffset().toDouble() * 60_000.0)
    return local.toISOString().take(16)
}

private fun Double.displayNumber(): String =
    if (this == roundToInt().toDouble()) roundToInt().toString() else toString()

private fun drawChart(canvas: HTMLCanvasElement, result: FiveArProjection) {
    val width = canvas.clientWidth.coerceAtLeast(260)
    val height = canvas.clientHeight.coerceAtLeast(220)
    if (canvas.width != width) canvas.width = width
    if (canvas.height != height) canvas.height = height
    val context = canvas.getContext("2d") as? CanvasRenderingContext2D ?: return
    val left = 38.0
    val right = 10.0
    val top = 12.0
    val bottom = 28.0
    val plotWidth = width - left - right
    val plotHeight = height - top - bottom
    val horizon = result.curve.lastOrNull()?.timeHours?.coerceAtLeast(1.0) ?: 1.0

    context.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
    context.fillStyle = "#0a0c10"
    context.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())
    context.font = "10px JetBrains Mono, monospace"
    context.fillStyle = "#6e737c"
    context.strokeStyle = "#262a31"
    context.lineWidth = 1.0
    for (index in 0..4) {
        val fraction = index / 4.0
        val y = top + plotHeight * (1.0 - fraction)
        context.beginPath()
        context.moveTo(left, y)
        context.lineTo(left + plotWidth, y)
        context.stroke()
        context.fillText("${(fraction * 100).roundToInt()}%", 3.0, y + 3.0)
    }
    context.fillText("0d", left, height - 8.0)
    context.fillText("${result.days}d", left + plotWidth - 24.0, height - 8.0)

    fun plot(color: String, selector: (FiveArCurvePoint) -> Double) {
        context.strokeStyle = color
        context.lineWidth = 1.8
        context.beginPath()
        result.curve.forEachIndexed { index, point ->
            val x = left + point.timeHours / horizon * plotWidth
            val y = top + (1.0 - selector(point).coerceIn(0.0, 1.0)) * plotHeight
            if (index == 0) context.moveTo(x, y) else context.lineTo(x, y)
        }
        context.stroke()
    }
    plot("#4ac4c4", FiveArCurvePoint::dhtSuppressionFraction)
    plot("#f6c857", FiveArCurvePoint::type1InhibitionFraction)
    plot("#ff6b5f", FiveArCurvePoint::type2InhibitionFraction)
}

private fun drawProgestogenChart(
    canvas: HTMLCanvasElement,
    result: EmbeddedProgestogenProjection,
) {
    val width = canvas.clientWidth.coerceAtLeast(260)
    val height = canvas.clientHeight.coerceAtLeast(220)
    if (canvas.width != width) canvas.width = width
    if (canvas.height != height) canvas.height = height
    val context = canvas.getContext("2d") as? CanvasRenderingContext2D ?: return
    val left = 38.0
    val right = 10.0
    val top = 12.0
    val bottom = 28.0
    val plotWidth = width - left - right
    val plotHeight = height - top - bottom
    val horizon = result.curve.lastOrNull()?.timeHours?.coerceAtLeast(1.0) ?: 1.0

    context.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
    context.fillStyle = "#0a0c10"
    context.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())
    context.font = "10px JetBrains Mono, monospace"
    context.fillStyle = "#6e737c"
    context.strokeStyle = "#262a31"
    context.lineWidth = 1.0
    for (index in 0..4) {
        val fraction = index / 4.0
        val y = top + plotHeight * (1.0 - fraction)
        context.beginPath()
        context.moveTo(left, y)
        context.lineTo(left + plotWidth, y)
        context.stroke()
        context.fillText("${(fraction * 100).roundToInt()}%", 3.0, y + 3.0)
    }
    context.fillText("0d", left, height - 8.0)
    context.fillText("${result.days}d", left + plotWidth - 24.0, height - 8.0)

    fun plot(color: String, selector: (ProgestogenFeedbackCurvePoint) -> Double) {
        context.strokeStyle = color
        context.lineWidth = 1.8
        context.beginPath()
        result.curve.forEachIndexed { index, point ->
            val x = left + point.timeHours / horizon * plotWidth
            val y = top + (1.0 - selector(point).coerceIn(0.0, 1.0)) * plotHeight
            if (index == 0) context.moveTo(x, y) else context.lineTo(x, y)
        }
        context.stroke()
    }
    plot("#4ac4c4", ProgestogenFeedbackCurvePoint::gnrhPulseSuppressionFraction)
    plot("#f6c857", ProgestogenFeedbackCurvePoint::prOccupancyFraction)
    plot("#ff6b5f", ProgestogenFeedbackCurvePoint::gnrhPulseActivityFraction)
}
