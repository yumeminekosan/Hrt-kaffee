package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

enum class ProgestogenLigand(
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
        fun fromWireId(value: String): ProgestogenLigand =
            entries.firstOrNull { it.wireId == value } ?: PROGESTERONE
    }
}

data class ProgestogenGnRHParameters(
    val molecularWeightGramsPerMole: Double,
    val apparentOralBioavailability: Double,
    val absorptionRatePerHour: Double,
    val eliminationRatePerHour: Double,
    val centralVolumeLitres: Double,
    val apparentPrHalfOccupancyNm: Double,
    val associationRatePerNmPerHour: Double,
    val feedbackRelaxationPerHour: Double,
    val maximumPulseSuppressionFraction: Double,
) {
    init {
        require(molecularWeightGramsPerMole > 0.0)
        require(apparentOralBioavailability in 0.0..1.0)
        require(absorptionRatePerHour > 0.0 && eliminationRatePerHour > 0.0)
        require(centralVolumeLitres > 0.0)
        require(apparentPrHalfOccupancyNm > 0.0)
        require(associationRatePerNmPerHour > 0.0)
        require(feedbackRelaxationPerHour > 0.0)
        require(maximumPulseSuppressionFraction in 0.0..1.0)
    }

    val dissociationRatePerHour: Double
        get() = apparentPrHalfOccupancyNm * associationRatePerNmPerHour

    companion object {
        /**
         * Population profiles for the oral products already present in the page.
         * P4 PK is calibrated to the 100 mg label Cmax; synthetic profiles are
         * relative structure/PK projections and are labelled as such in the UI.
         */
        fun forLigand(ligand: ProgestogenLigand): ProgestogenGnRHParameters = when (ligand) {
            ProgestogenLigand.MEDROXYPROGESTERONE -> ProgestogenGnRHParameters(
                386.52, 0.95, 1.20, 20.0 / 35.0, 35.0, 6.20,
                COMMON_ASSOCIATION_RATE, COMMON_FEEDBACK_RATE, MAXIMUM_SUPPRESSION,
            )
            ProgestogenLigand.CYPROTERONE -> ProgestogenGnRHParameters(
                416.94, 0.88, 0.60, ln(2.0) / 60.0, 520.0, 9.01,
                COMMON_ASSOCIATION_RATE, COMMON_FEEDBACK_RATE, MAXIMUM_SUPPRESSION,
            )
            ProgestogenLigand.DROSPIRENONE -> ProgestogenGnRHParameters(
                366.49, 0.76, 1.50, ln(2.0) / 32.0, 100.0, 9.01 / 0.19,
                COMMON_ASSOCIATION_RATE, COMMON_FEEDBACK_RATE, MAXIMUM_SUPPRESSION,
            )
            ProgestogenLigand.LEVONORGESTREL -> ProgestogenGnRHParameters(
                312.45, 0.90, 1.00, ln(2.0) / 12.0, 180.0, 5.46,
                COMMON_ASSOCIATION_RATE, COMMON_FEEDBACK_RATE, MAXIMUM_SUPPRESSION,
            )
            ProgestogenLigand.NORETHISTERONE -> ProgestogenGnRHParameters(
                298.42, 0.64, 1.20, ln(2.0) / 8.0, 80.0, 14.32,
                COMMON_ASSOCIATION_RATE, COMMON_FEEDBACK_RATE, MAXIMUM_SUPPRESSION,
            )
            ProgestogenLigand.PROGESTERONE -> ProgestogenGnRHParameters(
                314.47, 0.028, 0.50, 0.70, 50.0, 9.01,
                COMMON_ASSOCIATION_RATE, COMMON_FEEDBACK_RATE, MAXIMUM_SUPPRESSION,
            )
        }

        private const val COMMON_ASSOCIATION_RATE = 0.005
        private val COMMON_FEEDBACK_RATE = ln(2.0) / 36.0
        private const val MAXIMUM_SUPPRESSION = 0.85
    }
}

data class ProgestogenGnRHRegimen(
    val ligand: ProgestogenLigand,
    val doseMg: Double,
    val doseIntervalHours: Double,
    val days: Int,
    val integrationStepHours: Double = 0.05,
) {
    init {
        require(doseMg in 0.0..500.0)
        require(doseIntervalHours in 4.0..168.0)
        require(days in 1..365)
        require(integrationStepHours > 0.0 && integrationStepHours <= 0.1)
    }

    val dailyDoseMg: Double get() = doseMg * 24.0 / doseIntervalHours

    val isPopulationReferenceDomain: Boolean
        get() = ligand == ProgestogenLigand.PROGESTERONE &&
            (doseMg == 0.0 || dailyDoseMg in 100.0..300.0) &&
            doseIntervalHours == 24.0 && days <= 21
}

data class ProgestogenGnRHState(
    val gutAmountNmole: Double,
    val plasmaConcentrationNm: Double,
    val prBoundFraction: Double,
    val feedbackSignalFraction: Double,
)

data class ProgestogenGnRHCurvePoint(
    val timeHours: Double,
    val plasmaConcentrationNm: Double,
    val plasmaConcentrationNgPerMl: Double,
    val prOccupancyFraction: Double,
    val gnrhPulseSuppressionFraction: Double,
) {
    val gnrhPulseActivityFraction: Double get() = 1.0 - gnrhPulseSuppressionFraction
}

data class ProgestogenGnRHResult(
    val regimen: ProgestogenGnRHRegimen,
    val parameters: ProgestogenGnRHParameters,
    val curve: List<ProgestogenGnRHCurvePoint>,
    val finalPoint: ProgestogenGnRHCurvePoint,
    val peakPrOccupancyFraction: Double,
    val peakGnRHPulseSuppressionFraction: Double,
)

/**
 * Ligand–PR binding followed by a delayed, E2-primed GnRH pulse-generator
 * feedback projection. Progesterone is not represented as a GnRH-receptor
 * antagonist; the inhibited observable is the pulse-generator activity.
 */
class ProgestogenGnRHModel {
    fun simulate(regimen: ProgestogenGnRHRegimen): ProgestogenGnRHResult =
        simulateInternal(regimen)

    fun certify(
        regimen: ProgestogenGnRHRegimen,
        tolerance: Double = 2e-3,
    ): Evidence<ProgestogenGnRHResult> {
        require(tolerance > 0.0)
        val coarse = simulateInternal(regimen)
        val refined = simulateInternal(
            regimen.copy(integrationStepHours = regimen.integrationStepHours / 2.0),
        )
        val error = maxOf(
            abs(coarse.finalPoint.plasmaConcentrationNm - refined.finalPoint.plasmaConcentrationNm),
            abs(coarse.finalPoint.prOccupancyFraction - refined.finalPoint.prOccupancyFraction),
            abs(
                coarse.finalPoint.gnrhPulseSuppressionFraction -
                    refined.finalPoint.gnrhPulseSuppressionFraction,
            ),
        )
        require(error <= tolerance) {
            "RK4 step-halving residual $error exceeded tolerance $tolerance"
        }
        return Evidence(
            value = refined,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The progesterone-panel trajectory is the step-refined RK4 projection of ligand–PR binding and delayed GnRH pulse feedback.",
            assumptions = listOf(
                Assumption(
                    AssumptionIds.PROGESTOGEN_PR_BINDING,
                    "Competitive ligand occupancy is coarse-grained at PR; no direct binding to the GnRH receptor is asserted.",
                    AssumptionStatus.DECLARED,
                    "The P4 structural anchor is PDB 1A28 and the apparent affinities are competitive PR-binding measurements or declared relative-affinity extrapolations.",
                ),
                Assumption(
                    AssumptionIds.PROGESTOGEN_GNRH_FEEDBACK,
                    "PR occupancy drives a delayed E2-primed population GnRH pulse-frequency feedback state.",
                    AssumptionStatus.DECLARED,
                    "The feedback observable is a population projection, not an individualized hormone or fertility prediction.",
                ),
            ),
            diagnostics = listOf(
                NumericalDiagnostic("RK4 step-halving terminal-state residual", error, tolerance),
            ),
        )
    }

    private fun simulateInternal(regimen: ProgestogenGnRHRegimen): ProgestogenGnRHResult {
        val parameters = ProgestogenGnRHParameters.forLigand(regimen.ligand)
        val duration = regimen.days * 24.0
        val totalSteps = ceil(duration / regimen.integrationStepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val doseNmole = regimen.doseMg * 1_000_000.0 /
            parameters.molecularWeightGramsPerMole
        var state = ProgestogenGnRHState(0.0, 0.0, 0.0, 0.0)
        var time = 0.0
        var nextDoseTime = 0.0
        var stepIndex = 0
        var peakOccupancy = 0.0
        var peakSuppression = 0.0
        val points = mutableListOf<ProgestogenGnRHCurvePoint>()

        while (true) {
            if (time + 1e-9 >= nextDoseTime && nextDoseTime < duration - 1e-9) {
                state = state.copy(gutAmountNmole = state.gutAmountNmole + doseNmole)
                nextDoseTime += regimen.doseIntervalHours
            }
            val point = observation(time, state, parameters)
            peakOccupancy = max(peakOccupancy, point.prOccupancyFraction)
            peakSuppression = max(peakSuppression, point.gnrhPulseSuppressionFraction)
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                if (points.lastOrNull()?.timeHours != time) points += point
            }
            if (time >= duration - 1e-9) break
            val step = minOf(regimen.integrationStepHours, duration - time)
            state = rk4(state, step, parameters)
            time = minOf(duration, time + step)
            stepIndex += 1
        }
        return ProgestogenGnRHResult(
            regimen = regimen,
            parameters = parameters,
            curve = points,
            finalPoint = points.last(),
            peakPrOccupancyFraction = peakOccupancy,
            peakGnRHPulseSuppressionFraction = peakSuppression,
        )
    }

    private fun derivative(
        state: ProgestogenGnRHState,
        parameters: ProgestogenGnRHParameters,
    ): ProgestogenGnRHState {
        val gut = nonNegative(state.gutAmountNmole)
        val concentration = nonNegative(state.plasmaConcentrationNm)
        val bound = state.prBoundFraction.coerceIn(0.0, 1.0)
        val feedback = state.feedbackSignalFraction.coerceIn(0.0, 1.0)
        val association = parameters.associationRatePerNmPerHour * concentration * (1.0 - bound)
        val dissociation = parameters.dissociationRatePerHour * bound
        return ProgestogenGnRHState(
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
        state: ProgestogenGnRHState,
        step: Double,
        parameters: ProgestogenGnRHParameters,
    ): ProgestogenGnRHState {
        val k1 = derivative(state, parameters)
        val k2 = derivative(state.plusScaled(k1, step / 2.0), parameters)
        val k3 = derivative(state.plusScaled(k2, step / 2.0), parameters)
        val k4 = derivative(state.plusScaled(k3, step), parameters)
        fun update(selector: (ProgestogenGnRHState) -> Double): Double =
            selector(state) + step *
                (selector(k1) + 2.0 * selector(k2) + 2.0 * selector(k3) + selector(k4)) / 6.0
        return ProgestogenGnRHState(
            gutAmountNmole = nonNegative(update(ProgestogenGnRHState::gutAmountNmole)),
            plasmaConcentrationNm =
                nonNegative(update(ProgestogenGnRHState::plasmaConcentrationNm)),
            prBoundFraction = update(ProgestogenGnRHState::prBoundFraction).coerceIn(0.0, 1.0),
            feedbackSignalFraction =
                update(ProgestogenGnRHState::feedbackSignalFraction).coerceIn(0.0, 1.0),
        )
    }

    private fun observation(
        time: Double,
        state: ProgestogenGnRHState,
        parameters: ProgestogenGnRHParameters,
    ): ProgestogenGnRHCurvePoint {
        val concentration = nonNegative(state.plasmaConcentrationNm)
        return ProgestogenGnRHCurvePoint(
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

    private fun ProgestogenGnRHState.plusScaled(
        derivative: ProgestogenGnRHState,
        scale: Double,
    ): ProgestogenGnRHState = ProgestogenGnRHState(
        gutAmountNmole = gutAmountNmole + derivative.gutAmountNmole * scale,
        plasmaConcentrationNm = plasmaConcentrationNm + derivative.plasmaConcentrationNm * scale,
        prBoundFraction = prBoundFraction + derivative.prBoundFraction * scale,
        feedbackSignalFraction = feedbackSignalFraction + derivative.feedbackSignalFraction * scale,
    )

    private fun nonNegative(value: Double): Double = if (value > 0.0) value else 0.0
}
