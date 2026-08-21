package dev.hrtkaffee.ar.embedded

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

enum class EmbeddedProgestogen(
    val wireId: String,
    val displayName: String,
    val defaultDoseMg: Double,
) {
    MEDROXYPROGESTERONE("MPA", "MPA / 醋酸甲羟孕酮", 10.0),
    CYPROTERONE("CPA", "CPA / 醋酸环丙孕酮", 25.0),
    DROSPIRENONE("DRSP", "DRSP / 屈螺酮", 3.0),
    LEVONORGESTREL("LNG", "LNG / 左炔诺孕酮", 0.15),
    NORETHISTERONE("NET", "NET / 炔诺酮", 1.0),
    PROGESTERONE("P4", "P4 / 微粉化孕酮", 100.0),
    ;

    companion object {
        fun fromWireId(value: String): EmbeddedProgestogen =
            entries.firstOrNull { it.wireId == value } ?: PROGESTERONE
    }
}

data class EmbeddedProgestogenParameters(
    val molecularWeightGramsPerMole: Double,
    val apparentOralBioavailability: Double,
    val absorptionRatePerHour: Double,
    val eliminationRatePerHour: Double,
    val centralVolumeLitres: Double,
    val apparentPrHalfOccupancyNm: Double,
    val associationRatePerNmPerHour: Double = 0.005,
    val feedbackRelaxationPerHour: Double = ln(2.0) / 36.0,
    val maximumPulseSuppressionFraction: Double = 0.85,
) {
    val dissociationRatePerHour: Double
        get() = apparentPrHalfOccupancyNm * associationRatePerNmPerHour

    companion object {
        fun forLigand(ligand: EmbeddedProgestogen): EmbeddedProgestogenParameters =
            when (ligand) {
                EmbeddedProgestogen.MEDROXYPROGESTERONE ->
                    EmbeddedProgestogenParameters(386.52, 0.95, 1.20, 20.0 / 35.0, 35.0, 6.20)
                EmbeddedProgestogen.CYPROTERONE ->
                    EmbeddedProgestogenParameters(416.94, 0.88, 0.60, ln(2.0) / 60.0, 520.0, 9.01)
                EmbeddedProgestogen.DROSPIRENONE ->
                    EmbeddedProgestogenParameters(366.49, 0.76, 1.50, ln(2.0) / 32.0, 100.0, 9.01 / 0.19)
                EmbeddedProgestogen.LEVONORGESTREL ->
                    EmbeddedProgestogenParameters(312.45, 0.90, 1.00, ln(2.0) / 12.0, 180.0, 5.46)
                EmbeddedProgestogen.NORETHISTERONE ->
                    EmbeddedProgestogenParameters(298.42, 0.64, 1.20, ln(2.0) / 8.0, 80.0, 14.32)
                EmbeddedProgestogen.PROGESTERONE ->
                    EmbeddedProgestogenParameters(314.47, 0.028, 0.50, 0.70, 50.0, 9.01)
            }
    }
}

data class ProgestogenFeedbackCurvePoint(
    val timeHours: Double,
    val plasmaConcentrationNm: Double,
    val plasmaConcentrationNgPerMl: Double,
    val prOccupancyFraction: Double,
    val gnrhPulseSuppressionFraction: Double,
) {
    val gnrhPulseActivityFraction: Double get() = 1.0 - gnrhPulseSuppressionFraction
}

data class EmbeddedProgestogenProjection(
    val ligand: EmbeddedProgestogen,
    val doseMg: Double,
    val intervalHours: Double,
    val days: Int,
    val curve: List<ProgestogenFeedbackCurvePoint>,
    val endpoint: ProgestogenFeedbackCurvePoint,
    val peakPrOccupancyFraction: Double,
    val peakGnRHPulseSuppressionFraction: Double,
    val isReferenceDomain: Boolean,
    val boundaryMessage: String,
    val referenceLabel: String,
    val referenceUrl: String,
)

/** Browser projection of the audited ligand–PR→GnRH feedback equations. */
object EmbeddedProgestogenGnRHModel {
    private data class State(
        val gutAmountNmole: Double,
        val plasmaConcentrationNm: Double,
        val prBoundFraction: Double,
        val feedbackSignalFraction: Double,
    )

    fun simulate(
        ligand: EmbeddedProgestogen,
        doseMg: Double,
        intervalHours: Double,
        days: Int,
        integrationStepHours: Double = 0.05,
    ): EmbeddedProgestogenProjection {
        require(doseMg in 0.0..500.0)
        require(intervalHours in 4.0..168.0)
        require(days in 1..365)
        require(integrationStepHours > 0.0 && integrationStepHours <= 0.1)
        val parameters = EmbeddedProgestogenParameters.forLigand(ligand)
        val duration = days * 24.0
        val totalSteps = ceil(duration / integrationStepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val doseNmole = doseMg * 1_000_000.0 / parameters.molecularWeightGramsPerMole
        var state = State(0.0, 0.0, 0.0, 0.0)
        var time = 0.0
        var nextDoseTime = 0.0
        var stepIndex = 0
        var peakOccupancy = 0.0
        var peakSuppression = 0.0
        val curve = mutableListOf<ProgestogenFeedbackCurvePoint>()

        fun observe(): ProgestogenFeedbackCurvePoint {
            val concentration = nonNegative(state.plasmaConcentrationNm)
            return ProgestogenFeedbackCurvePoint(
                timeHours = time,
                plasmaConcentrationNm = concentration,
                plasmaConcentrationNgPerMl =
                    concentration * parameters.molecularWeightGramsPerMole / 1_000.0,
                prOccupancyFraction = state.prBoundFraction.coerceIn(0.0, 1.0),
                gnrhPulseSuppressionFraction =
                    parameters.maximumPulseSuppressionFraction *
                        state.feedbackSignalFraction.coerceIn(0.0, 1.0),
            )
        }

        while (true) {
            if (time + 1e-9 >= nextDoseTime && nextDoseTime < duration - 1e-9) {
                state = state.copy(gutAmountNmole = state.gutAmountNmole + doseNmole)
                nextDoseTime += intervalHours
            }
            val point = observe()
            peakOccupancy = max(peakOccupancy, point.prOccupancyFraction)
            peakSuppression = max(peakSuppression, point.gnrhPulseSuppressionFraction)
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                if (curve.lastOrNull()?.timeHours != time) curve += point
            }
            if (time >= duration - 1e-9) break
            val step = minOf(integrationStepHours, duration - time)
            state = rk4(state, step, parameters)
            time = minOf(duration, time + step)
            stepIndex += 1
        }

        val dailyDose = doseMg * 24.0 / intervalHours
        val referenceDomain = ligand == EmbeddedProgestogen.PROGESTERONE &&
            (doseMg == 0.0 || dailyDose in 100.0..300.0) &&
            intervalHours == 24.0 && days <= 21
        val boundary = when {
            ligand != EmbeddedProgestogen.PROGESTERONE ->
                "该合成孕激素显示的是相对 PR 结合/PK 结构外推；GnRH 反馈未作个体校准。"
            !referenceDomain ->
                "超出 P4 100–300 mg/day、q24h、≤21 天参考域；显示的是模型外推。"
            else ->
                "位于口服微粉化 P4 PK 参考域；GnRH 为 E2 预激人群反馈投影。"
        }
        return EmbeddedProgestogenProjection(
            ligand = ligand,
            doseMg = doseMg,
            intervalHours = intervalHours,
            days = days,
            curve = curve,
            endpoint = curve.last(),
            peakPrOccupancyFraction = peakOccupancy,
            peakGnRHPulseSuppressionFraction = peakSuppression,
            isReferenceDomain = referenceDomain,
            boundaryMessage = boundary,
            referenceLabel = if (ligand == EmbeddedProgestogen.PROGESTERONE) {
                "PDB 1A28 · PR binding / human pulse feedback"
            } else {
                "Comparative human PR binding · structural extrapolation"
            },
            referenceUrl = if (ligand == EmbeddedProgestogen.PROGESTERONE) {
                "https://pubmed.ncbi.nlm.nih.gov/9467578/"
            } else {
                "https://pmc.ncbi.nlm.nih.gov/articles/PMC2999493/"
            },
        )
    }

    private fun derivative(
        state: State,
        parameters: EmbeddedProgestogenParameters,
    ): State {
        val gut = nonNegative(state.gutAmountNmole)
        val concentration = nonNegative(state.plasmaConcentrationNm)
        val bound = state.prBoundFraction.coerceIn(0.0, 1.0)
        val feedback = state.feedbackSignalFraction.coerceIn(0.0, 1.0)
        val association = parameters.associationRatePerNmPerHour * concentration * (1.0 - bound)
        val dissociation = parameters.dissociationRatePerHour * bound
        return State(
            gutAmountNmole = -parameters.absorptionRatePerHour * gut,
            plasmaConcentrationNm =
                parameters.absorptionRatePerHour * parameters.apparentOralBioavailability * gut /
                parameters.centralVolumeLitres -
                parameters.eliminationRatePerHour * concentration,
            prBoundFraction = association - dissociation,
            feedbackSignalFraction = parameters.feedbackRelaxationPerHour * (bound - feedback),
        )
    }

    private fun rk4(
        state: State,
        step: Double,
        parameters: EmbeddedProgestogenParameters,
    ): State {
        val k1 = derivative(state, parameters)
        val k2 = derivative(state.plusScaled(k1, step / 2.0), parameters)
        val k3 = derivative(state.plusScaled(k2, step / 2.0), parameters)
        val k4 = derivative(state.plusScaled(k3, step), parameters)
        fun update(selector: (State) -> Double): Double = selector(state) + step *
            (selector(k1) + 2.0 * selector(k2) + 2.0 * selector(k3) + selector(k4)) / 6.0
        return State(
            gutAmountNmole = nonNegative(update(State::gutAmountNmole)),
            plasmaConcentrationNm = nonNegative(update(State::plasmaConcentrationNm)),
            prBoundFraction = update(State::prBoundFraction).coerceIn(0.0, 1.0),
            feedbackSignalFraction = update(State::feedbackSignalFraction).coerceIn(0.0, 1.0),
        )
    }

    private fun State.plusScaled(derivative: State, scale: Double): State = State(
        gutAmountNmole = gutAmountNmole + derivative.gutAmountNmole * scale,
        plasmaConcentrationNm = plasmaConcentrationNm + derivative.plasmaConcentrationNm * scale,
        prBoundFraction = prBoundFraction + derivative.prBoundFraction * scale,
        feedbackSignalFraction = feedbackSignalFraction + derivative.feedbackSignalFraction * scale,
    )

    private fun nonNegative(value: Double): Double = if (value > 0.0) value else 0.0
}
