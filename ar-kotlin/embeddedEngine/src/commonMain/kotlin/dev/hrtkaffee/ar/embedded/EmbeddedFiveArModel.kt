package dev.hrtkaffee.ar.embedded

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

enum class FiveArDrug(
    val wireId: String,
    val displayName: String,
    val defaultDoseMg: Double,
) {
    FINASTERIDE("finasteride", "Finasteride / 非那雄胺", 1.0),
    DUTASTERIDE("dutasteride", "Dutasteride / 度他雄胺", 0.5),
    ;

    companion object {
        fun fromWireId(value: String): FiveArDrug =
            entries.firstOrNull { it.wireId == value } ?: FINASTERIDE
    }
}

data class FiveArCurvePoint(
    val timeHours: Double,
    val concentrationNm: Double,
    val type1InhibitionFraction: Double,
    val type2InhibitionFraction: Double,
    val dhtSuppressionFraction: Double,
)

data class FiveArProjection(
    val drug: FiveArDrug,
    val dailyDoseMg: Double,
    val days: Int,
    val curve: List<FiveArCurvePoint>,
    val endpoint: FiveArCurvePoint,
    val peakDhtSuppressionFraction: Double,
    val isReferenceDomain: Boolean,
    val boundaryMessage: String,
    val referenceLabel: String,
    val referenceUrl: String,
)

/**
 * Browser projection of the same population equations audited on the JVM.
 * The microscopic CTMC/LDP/Doob/spatial/topological layers remain internal;
 * only this density-scale observable is rendered by the page.
 */
object EmbeddedFiveArModel {
    fun simulate(
        drug: FiveArDrug,
        dailyDoseMg: Double,
        days: Int,
        integrationStepHours: Double = 0.05,
    ): FiveArProjection {
        require(dailyDoseMg in 0.0..20.0)
        require(days in 1..365)
        require(integrationStepHours > 0.0 && integrationStepHours <= 0.1)
        return when (drug) {
            FiveArDrug.FINASTERIDE ->
                simulateFinasteride(dailyDoseMg, days, integrationStepHours)
            FiveArDrug.DUTASTERIDE ->
                simulateDutasteride(dailyDoseMg, days, integrationStepHours)
        }
    }

    private data class FinState(
        val gutNmole: Double,
        val concentrationNm: Double,
        val type2BoundNmole: Double,
        val dhtFraction: Double,
    )

    private fun simulateFinasteride(
        doseMg: Double,
        days: Int,
        stepHours: Double,
    ): FiveArProjection {
        val duration = days * 24.0
        val totalSteps = ceil(duration / stepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val doseNmole = doseMg * 1_000_000.0 / 372.55
        var state = FinState(0.0, 0.0, 0.0, 1.0)
        var time = 0.0
        var nextDose = 0.0
        var stepIndex = 0
        var peak = 0.0
        val curve = mutableListOf<FiveArCurvePoint>()

        fun observe(): FiveArCurvePoint {
            val concentration = nonNegative(state.concentrationNm)
            return FiveArCurvePoint(
                timeHours = time,
                concentrationNm = concentration,
                type1InhibitionFraction = concentration / (concentration + 220.0),
                type2InhibitionFraction = (state.type2BoundNmole / 320.0).coerceIn(0.0, 1.0),
                dhtSuppressionFraction = 1.0 - state.dhtFraction.coerceIn(0.0, 1.0),
            )
        }

        while (true) {
            if (time + 1e-9 >= nextDose && nextDose < duration - 1e-9) {
                state = state.copy(gutNmole = state.gutNmole + doseNmole)
                nextDose += 24.0
            }
            val point = observe()
            peak = max(peak, point.dhtSuppressionFraction)
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                if (curve.lastOrNull()?.timeHours != time) curve += point
            }
            if (time >= duration - 1e-9) break
            val step = minOf(stepHours, duration - time)
            state = finRk4(state, step)
            time = minOf(duration, time + step)
            stepIndex += 1
        }
        val doseReference = doseMg == 0.0 || doseMg in 0.01..5.0
        val timeReference = days <= 42
        val boundary = when {
            !doseReference -> "剂量超出 0.01–5 mg/day 重复给药参考域；显示的是模型外推。"
            !timeReference -> "时长超出非那雄胺 42 天校准窗；显示的是模型外推。"
            else -> "位于非那雄胺重复给药参考域；结果为群体动力学投影。"
        }
        return FiveArProjection(
            drug = FiveArDrug.FINASTERIDE,
            dailyDoseMg = doseMg,
            days = days,
            curve = curve,
            endpoint = curve.last(),
            peakDhtSuppressionFraction = peak,
            isReferenceDomain = doseReference && timeReference,
            boundaryMessage = boundary,
            referenceLabel = "Suzuki 2010 · saturable SRD5A2 binding",
            referenceUrl = "https://doi.org/10.2133/dmpk.25.208",
        )
    }

    private fun finDerivative(state: FinState): FinState {
        val gut = nonNegative(state.gutNmole)
        val concentration = nonNegative(state.concentrationNm)
        val bound = state.type2BoundNmole.coerceIn(0.0, 320.0)
        val association = 0.0293 * concentration * (320.0 - bound)
        val dissociation = 0.0185 * bound
        val type2Inhibition = bound / 320.0
        val type1Inhibition = concentration / (concentration + 220.0)
        val residualProduction =
            0.574 * (1.0 - type2Inhibition) + 0.426 * (1.0 - type1Inhibition)
        return FinState(
            gutNmole = -1.87 * gut,
            concentrationNm =
                1.87 * 0.8 * gut / 73.7 + dissociation / 73.7 -
                    0.177 * concentration - association,
            type2BoundNmole = association * 73.7 - dissociation,
            dhtFraction = 0.188 *
                (residualProduction - state.dhtFraction.coerceIn(0.0, 1.0)),
        )
    }

    private fun finRk4(state: FinState, step: Double): FinState {
        val k1 = finDerivative(state)
        val k2 = finDerivative(state.plusScaled(k1, step / 2.0))
        val k3 = finDerivative(state.plusScaled(k2, step / 2.0))
        val k4 = finDerivative(state.plusScaled(k3, step))
        fun update(selector: (FinState) -> Double): Double = selector(state) + step *
            (selector(k1) + 2.0 * selector(k2) + 2.0 * selector(k3) + selector(k4)) / 6.0
        return FinState(
            gutNmole = nonNegative(update(FinState::gutNmole)),
            concentrationNm = nonNegative(update(FinState::concentrationNm)),
            type2BoundNmole = update(FinState::type2BoundNmole).coerceIn(0.0, 320.0),
            dhtFraction = update(FinState::dhtFraction).coerceIn(0.0, 1.0),
        )
    }

    private fun FinState.plusScaled(derivative: FinState, scale: Double): FinState = FinState(
        gutNmole = gutNmole + derivative.gutNmole * scale,
        concentrationNm = concentrationNm + derivative.concentrationNm * scale,
        type2BoundNmole = type2BoundNmole + derivative.type2BoundNmole * scale,
        dhtFraction = dhtFraction + derivative.dhtFraction * scale,
    )

    private data class DutState(
        val gutNmole: Double,
        val concentrationNm: Double,
        val activeType1Fraction: Double,
        val activeType2Fraction: Double,
        val dhtFraction: Double,
    )

    private fun simulateDutasteride(
        doseMg: Double,
        days: Int,
        stepHours: Double,
    ): FiveArProjection {
        val duration = days * 24.0
        val totalSteps = ceil(duration / stepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val doseNmole = doseMg * 1_000_000.0 / 528.53
        var state = DutState(0.0, 0.0, 1.0, 1.0, 1.0)
        var time = 0.0
        var nextDose = 0.0
        var stepIndex = 0
        var peak = 0.0
        val curve = mutableListOf<FiveArCurvePoint>()

        fun observe(): FiveArCurvePoint = FiveArCurvePoint(
            timeHours = time,
            concentrationNm = nonNegative(state.concentrationNm),
            type1InhibitionFraction = 1.0 - state.activeType1Fraction.coerceIn(0.0, 1.0),
            type2InhibitionFraction = 1.0 - state.activeType2Fraction.coerceIn(0.0, 1.0),
            dhtSuppressionFraction = 1.0 - state.dhtFraction.coerceIn(0.0, 1.0),
        )

        while (true) {
            if (time + 1e-9 >= nextDose && nextDose < duration - 1e-9) {
                state = state.copy(gutNmole = state.gutNmole + doseNmole)
                nextDose += 24.0
            }
            val point = observe()
            peak = max(peak, point.dhtSuppressionFraction)
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                if (curve.lastOrNull()?.timeHours != time) curve += point
            }
            if (time >= duration - 1e-9) break
            val step = minOf(stepHours, duration - time)
            state = dutRk4(state, step)
            time = minOf(duration, time + step)
            stepIndex += 1
        }
        val doseReference = doseMg == 0.0 || doseMg in 0.01..5.0
        val boundary = if (doseReference) {
            "位于度他雄胺 0.01–5 mg/day 剂量研究域；结果为群体动力学投影。"
        } else {
            "剂量超出度他雄胺 0.01–5 mg/day 研究域；显示的是模型外推。"
        }
        return FiveArProjection(
            drug = FiveArDrug.DUTASTERIDE,
            dailyDoseMg = doseMg,
            days = days,
            curve = curve,
            endpoint = curve.last(),
            peakDhtSuppressionFraction = peak,
            isReferenceDomain = doseReference,
            boundaryMessage = boundary,
            referenceLabel = "Clark 2004 · dual 5αR dose-ranging",
            referenceUrl = "https://pubmed.ncbi.nlm.nih.gov/15126539/",
        )
    }

    private fun dutDerivative(state: DutState): DutState {
        val gut = nonNegative(state.gutNmole)
        val concentration = nonNegative(state.concentrationNm)
        val activeType1 = state.activeType1Fraction.coerceIn(0.0, 1.0)
        val activeType2 = state.activeType2Fraction.coerceIn(0.0, 1.0)
        val type1Engagement = concentration / (concentration + 46.8)
        val type2Engagement = concentration / (concentration + 21.6)
        val residualProduction = 0.80 * activeType2 + 0.20 * activeType1
        return DutState(
            gutNmole = -1.5 * gut,
            concentrationNm =
                1.5 * 0.60 * gut / 300.0 - ln(2.0) / (35.0 * 24.0) * concentration,
            activeType1Fraction =
                0.006 * (1.0 - activeType1) - 0.12 * type1Engagement * activeType1,
            activeType2Fraction =
                0.006 * (1.0 - activeType2) - 0.12 * type2Engagement * activeType2,
            dhtFraction = 0.188 *
                (residualProduction - state.dhtFraction.coerceIn(0.0, 1.0)),
        )
    }

    private fun dutRk4(state: DutState, step: Double): DutState {
        val k1 = dutDerivative(state)
        val k2 = dutDerivative(state.plusScaled(k1, step / 2.0))
        val k3 = dutDerivative(state.plusScaled(k2, step / 2.0))
        val k4 = dutDerivative(state.plusScaled(k3, step))
        fun update(selector: (DutState) -> Double): Double = selector(state) + step *
            (selector(k1) + 2.0 * selector(k2) + 2.0 * selector(k3) + selector(k4)) / 6.0
        return DutState(
            gutNmole = nonNegative(update(DutState::gutNmole)),
            concentrationNm = nonNegative(update(DutState::concentrationNm)),
            activeType1Fraction = update(DutState::activeType1Fraction).coerceIn(0.0, 1.0),
            activeType2Fraction = update(DutState::activeType2Fraction).coerceIn(0.0, 1.0),
            dhtFraction = update(DutState::dhtFraction).coerceIn(0.0, 1.0),
        )
    }

    private fun DutState.plusScaled(derivative: DutState, scale: Double): DutState = DutState(
        gutNmole = gutNmole + derivative.gutNmole * scale,
        concentrationNm = concentrationNm + derivative.concentrationNm * scale,
        activeType1Fraction = activeType1Fraction + derivative.activeType1Fraction * scale,
        activeType2Fraction = activeType2Fraction + derivative.activeType2Fraction * scale,
        dhtFraction = dhtFraction + derivative.dhtFraction * scale,
    )

    private fun nonNegative(value: Double): Double = if (value > 0.0) value else 0.0
}
