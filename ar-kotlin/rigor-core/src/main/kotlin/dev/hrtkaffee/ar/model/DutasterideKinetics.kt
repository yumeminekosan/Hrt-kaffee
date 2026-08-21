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

/**
 * Population PK/PD parameters for oral dutasteride. The PK anchors are the
 * AVODART product information (60% bioavailability, 300–500 L distribution
 * volume, 1–3 h Tmax and 3–5 week terminal half-life). The dual-enzyme
 * inactivation layer is calibrated to the reported 0.5 mg/day DHT reductions
 * at one and two weeks and the 24-week dose-ranging trial.
 */
data class DutasteridePkPdParameters(
    val molecularWeightGramsPerMole: Double,
    val oralBioavailability: Double,
    val absorptionRatePerHour: Double,
    val eliminationRatePerHour: Double,
    val centralVolumeLitres: Double,
    val structuralType1BindingKiNm: Double,
    val structuralType2BindingKiNm: Double,
    val populationAccessibilityScale: Double,
    val enzymeRecoveryPerHour: Double,
    val maximumInactivationPerHour: Double,
    val type2DhtFormationFraction: Double,
    val dhtTurnoverPerHour: Double,
) {
    init {
        require(molecularWeightGramsPerMole > 0.0)
        require(oralBioavailability in 0.0..1.0)
        require(absorptionRatePerHour > 0.0 && eliminationRatePerHour > 0.0)
        require(centralVolumeLitres > 0.0)
        require(structuralType1BindingKiNm > 0.0 && structuralType2BindingKiNm > 0.0)
        require(populationAccessibilityScale >= 1.0)
        require(enzymeRecoveryPerHour > 0.0 && maximumInactivationPerHour > 0.0)
        require(type2DhtFormationFraction in 0.0..1.0)
        require(dhtTurnoverPerHour > 0.0)
    }

    val effectiveType1HalfSaturationNm: Double
        get() = structuralType1BindingKiNm * populationAccessibilityScale

    val effectiveType2HalfSaturationNm: Double
        get() = structuralType2BindingKiNm * populationAccessibilityScale

    companion object {
        fun populationReference(): DutasteridePkPdParameters = DutasteridePkPdParameters(
            molecularWeightGramsPerMole = 528.53,
            oralBioavailability = 0.60,
            absorptionRatePerHour = 1.5,
            eliminationRatePerHour = ln(2.0) / (35.0 * 24.0),
            centralVolumeLitres = 300.0,
            structuralType1BindingKiNm = 3.9,
            structuralType2BindingKiNm = 1.8,
            populationAccessibilityScale = 12.0,
            enzymeRecoveryPerHour = 0.006,
            maximumInactivationPerHour = 0.12,
            type2DhtFormationFraction = 0.80,
            dhtTurnoverPerHour = 0.188,
        )
    }
}

data class DutasterideRegimen(
    val dailyDoseMg: Double,
    val days: Int,
    val doseIntervalHours: Double = 24.0,
    val integrationStepHours: Double = 0.05,
) {
    init {
        require(dailyDoseMg in 0.0..20.0) { "The interactive domain is 0 to 20 mg/day" }
        require(days in 1..365) { "The interactive horizon is 1 to 365 days" }
        require(doseIntervalHours > 0.0)
        require(integrationStepHours > 0.0 && integrationStepHours <= 0.1)
    }

    val isPopulationReferenceDomain: Boolean
        get() = dailyDoseMg == 0.0 || dailyDoseMg in 0.01..5.0
}

data class DutasterideKineticState(
    val gutAmountNmole: Double,
    val plasmaConcentrationNm: Double,
    val activeType1Fraction: Double,
    val activeType2Fraction: Double,
    val serumDhtFraction: Double,
)

data class DutasterideCurvePoint(
    val timeHours: Double,
    val plasmaConcentrationNm: Double,
    val type1InhibitionFraction: Double,
    val type2InhibitionFraction: Double,
    val serumDhtFraction: Double,
) {
    val serumDhtSuppressionFraction: Double get() = 1.0 - serumDhtFraction
}

data class DutasterideKineticResult(
    val regimen: DutasterideRegimen,
    val parameters: DutasteridePkPdParameters,
    val curve: List<DutasterideCurvePoint>,
    val finalPoint: DutasterideCurvePoint,
    val peakDhtSuppressionFraction: Double,
)

/**
 * Dual SRD5A1/SRD5A2 competitive, time-dependent enzyme-inactivation model.
 * It changes T→DHT production upstream and is not a direct AR-antagonist model.
 */
class DutasterideKineticModel(
    private val parameters: DutasteridePkPdParameters =
        DutasteridePkPdParameters.populationReference(),
) {
    fun simulate(regimen: DutasterideRegimen): DutasterideKineticResult =
        simulateInternal(regimen)

    fun certify(
        regimen: DutasterideRegimen,
        tolerance: Double = 2e-3,
    ): Evidence<DutasterideKineticResult> {
        require(tolerance > 0.0)
        val coarse = simulateInternal(regimen)
        val refined = simulateInternal(
            regimen.copy(integrationStepHours = regimen.integrationStepHours / 2.0),
        )
        val error = maxOf(
            abs(coarse.finalPoint.serumDhtFraction - refined.finalPoint.serumDhtFraction),
            abs(coarse.finalPoint.type1InhibitionFraction - refined.finalPoint.type1InhibitionFraction),
            abs(coarse.finalPoint.type2InhibitionFraction - refined.finalPoint.type2InhibitionFraction),
        )
        require(error <= tolerance) {
            "RK4 step-halving residual $error exceeded tolerance $tolerance"
        }
        return Evidence(
            value = refined,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The displayed dutasteride trajectory is the step-refined RK4 projection of the dual 5-alpha-reductase reaction model.",
            assumptions = listOf(
                Assumption(
                    AssumptionIds.DUTASTERIDE_DUAL_ENZYME_INACTIVATION,
                    "Competitive binding and time-dependent loss of active SRD5A1/SRD5A2 are coarse-grained as Markovian enzyme fractions.",
                    AssumptionStatus.DECLARED,
                    "The mechanism follows the irreversible-inhibitor DHT turnover model and dual-isoenzyme product information.",
                ),
                Assumption(
                    AssumptionIds.DUTASTERIDE_POPULATION_PARAMETERS,
                    "Population PK/PD parameters are used without patient-specific re-identification.",
                    AssumptionStatus.DECLARED,
                    "The page reports a population projection and flags values outside the dose-ranging reference domain.",
                ),
            ),
            diagnostics = listOf(
                NumericalDiagnostic("RK4 step-halving terminal-state residual", error, tolerance),
            ),
        )
    }

    private fun simulateInternal(regimen: DutasterideRegimen): DutasterideKineticResult {
        val duration = regimen.days * 24.0
        val totalSteps = ceil(duration / regimen.integrationStepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val doseNmole = regimen.dailyDoseMg * 1_000_000.0 /
            parameters.molecularWeightGramsPerMole
        var state = DutasterideKineticState(0.0, 0.0, 1.0, 1.0, 1.0)
        var time = 0.0
        var nextDoseTime = 0.0
        var stepIndex = 0
        var peakDhtSuppression = 0.0
        val points = mutableListOf<DutasterideCurvePoint>()

        while (true) {
            if (time + 1e-9 >= nextDoseTime && nextDoseTime < duration - 1e-9) {
                state = state.copy(gutAmountNmole = state.gutAmountNmole + doseNmole)
                nextDoseTime += regimen.doseIntervalHours
            }
            val point = observation(time, state)
            peakDhtSuppression = max(peakDhtSuppression, point.serumDhtSuppressionFraction)
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                if (points.lastOrNull()?.timeHours != time) points += point
            }
            if (time >= duration - 1e-9) break

            val step = minOf(regimen.integrationStepHours, duration - time)
            state = rk4(state, step)
            time = minOf(duration, time + step)
            stepIndex += 1
        }
        return DutasterideKineticResult(
            regimen = regimen,
            parameters = parameters,
            curve = points,
            finalPoint = points.last(),
            peakDhtSuppressionFraction = peakDhtSuppression,
        )
    }

    private fun derivative(state: DutasterideKineticState): DutasterideKineticState {
        val gut = nonNegative(state.gutAmountNmole)
        val concentration = nonNegative(state.plasmaConcentrationNm)
        val activeType1 = state.activeType1Fraction.coerceIn(0.0, 1.0)
        val activeType2 = state.activeType2Fraction.coerceIn(0.0, 1.0)
        val type1Engagement = concentration /
            (concentration + parameters.effectiveType1HalfSaturationNm)
        val type2Engagement = concentration /
            (concentration + parameters.effectiveType2HalfSaturationNm)
        val residualDhtProduction =
            parameters.type2DhtFormationFraction * activeType2 +
                (1.0 - parameters.type2DhtFormationFraction) * activeType1

        return DutasterideKineticState(
            gutAmountNmole = -parameters.absorptionRatePerHour * gut,
            plasmaConcentrationNm =
                parameters.absorptionRatePerHour * parameters.oralBioavailability * gut /
                parameters.centralVolumeLitres -
                parameters.eliminationRatePerHour * concentration,
            activeType1Fraction =
                parameters.enzymeRecoveryPerHour * (1.0 - activeType1) -
                parameters.maximumInactivationPerHour * type1Engagement * activeType1,
            activeType2Fraction =
                parameters.enzymeRecoveryPerHour * (1.0 - activeType2) -
                parameters.maximumInactivationPerHour * type2Engagement * activeType2,
            serumDhtFraction = parameters.dhtTurnoverPerHour *
                (residualDhtProduction - state.serumDhtFraction.coerceIn(0.0, 1.0)),
        )
    }

    private fun rk4(state: DutasterideKineticState, step: Double): DutasterideKineticState {
        val k1 = derivative(state)
        val k2 = derivative(state.plusScaled(k1, step / 2.0))
        val k3 = derivative(state.plusScaled(k2, step / 2.0))
        val k4 = derivative(state.plusScaled(k3, step))
        fun update(selector: (DutasterideKineticState) -> Double): Double =
            selector(state) + step *
                (selector(k1) + 2.0 * selector(k2) + 2.0 * selector(k3) + selector(k4)) / 6.0
        return DutasterideKineticState(
            gutAmountNmole = nonNegative(update(DutasterideKineticState::gutAmountNmole)),
            plasmaConcentrationNm =
                nonNegative(update(DutasterideKineticState::plasmaConcentrationNm)),
            activeType1Fraction =
                update(DutasterideKineticState::activeType1Fraction).coerceIn(0.0, 1.0),
            activeType2Fraction =
                update(DutasterideKineticState::activeType2Fraction).coerceIn(0.0, 1.0),
            serumDhtFraction =
                update(DutasterideKineticState::serumDhtFraction).coerceIn(0.0, 1.0),
        )
    }

    private fun observation(
        time: Double,
        state: DutasterideKineticState,
    ): DutasterideCurvePoint = DutasterideCurvePoint(
        timeHours = time,
        plasmaConcentrationNm = nonNegative(state.plasmaConcentrationNm),
        type1InhibitionFraction = 1.0 - state.activeType1Fraction.coerceIn(0.0, 1.0),
        type2InhibitionFraction = 1.0 - state.activeType2Fraction.coerceIn(0.0, 1.0),
        serumDhtFraction = state.serumDhtFraction.coerceIn(0.0, 1.0),
    )

    private fun DutasterideKineticState.plusScaled(
        derivative: DutasterideKineticState,
        scale: Double,
    ): DutasterideKineticState = DutasterideKineticState(
        gutAmountNmole = gutAmountNmole + derivative.gutAmountNmole * scale,
        plasmaConcentrationNm = plasmaConcentrationNm + derivative.plasmaConcentrationNm * scale,
        activeType1Fraction = activeType1Fraction + derivative.activeType1Fraction * scale,
        activeType2Fraction = activeType2Fraction + derivative.activeType2Fraction * scale,
        serumDhtFraction = serumDhtFraction + derivative.serumDhtFraction * scale,
    )

    private fun nonNegative(value: Double): Double = if (value > 0.0) value else 0.0
}
