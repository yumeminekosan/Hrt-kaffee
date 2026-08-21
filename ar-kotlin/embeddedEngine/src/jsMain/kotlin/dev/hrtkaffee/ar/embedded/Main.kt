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
    val endpoint: HTMLElement,
    val peak: HTMLElement,
    val occupancy: HTMLElement,
    val activity: HTMLElement,
    val concentration: HTMLElement,
    val boundary: HTMLElement,
    val reference: HTMLAnchorElement,
    val canvas: HTMLCanvasElement,
)

private fun bindProgestogenModule() {
    val elements = findProgestogenElements() ?: return
    var latest: EmbeddedProgestogenProjection? = null

    fun hide() {
        elements.results.style.display = "none"
        elements.panel.setAttribute("aria-busy", "false")
    }

    fun render() {
        if (elements.drug.value == "none") {
            hide()
            return
        }
        val ligand = EmbeddedProgestogen.fromWireId(elements.drug.value)
        val dose = (elements.dose.value.toDoubleOrNull() ?: ligand.defaultDoseMg)
            .coerceIn(0.0, 500.0)
        val interval = (elements.interval.value.toDoubleOrNull() ?: 24.0)
            .coerceIn(4.0, 168.0)
        val days = (elements.days.value.toIntOrNull() ?: 14).coerceIn(1, 365)
        elements.panel.setAttribute("aria-busy", "true")
        val result = EmbeddedProgestogenGnRHModel.simulate(ligand, dose, interval, days)
        latest = result
        elements.endpoint.textContent = percent(result.endpoint.gnrhPulseSuppressionFraction)
        elements.peak.textContent = percent(result.peakGnRHPulseSuppressionFraction)
        elements.occupancy.textContent = percent(result.endpoint.prOccupancyFraction)
        elements.activity.textContent = percent(result.endpoint.gnrhPulseActivityFraction)
        elements.concentration.textContent = concentrationNgMl(
            result.endpoint.plasmaConcentrationNgPerMl,
        )
        elements.boundary.textContent = result.boundaryMessage
        elements.boundary.classList.toggle("extrapolated", !result.isReferenceDomain)
        elements.reference.textContent = result.referenceLabel
        elements.reference.href = result.referenceUrl
        elements.results.style.display = "block"
        drawProgestogenChart(elements.canvas, result)
        elements.panel.setAttribute("aria-busy", "false")
    }

    elements.drug.addEventListener("change", { _: Event -> render() })
    elements.dose.addEventListener("change", { _: Event -> render() })
    elements.interval.addEventListener("change", { _: Event -> render() })
    elements.days.addEventListener("change", { _: Event -> render() })
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
        endpoint = element("pgGnrhEffect") ?: return null,
        peak = element("pgGnrhPeak") ?: return null,
        occupancy = element("pgPrOccupancy") ?: return null,
        activity = element("pgGnrhActivity") ?: return null,
        concentration = element("pgGnrhConcentration") ?: return null,
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
