package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Identified population PK/PD parameters from Suzuki et al. (2010),
 * DOI 10.2133/dmpk.25.208. Concentrations are nM, amounts are nmol,
 * and time is hours.
 */
data class FinasteridePkPdParameters(
    val molecularWeightGramsPerMole: Double,
    val oralBioavailability: Double,
    val absorptionRatePerHour: Double,
    val eliminationRatePerHour: Double,
    val centralVolumeLitres: Double,
    val type2AssociationPerNmoleHour: Double,
    val type2DissociationPerHour: Double,
    val totalType2AmountNmole: Double,
    val type2DhtFormationFraction: Double,
    val dhtTurnoverPerHour: Double,
    val type1InhibitionConstantNm: Double,
) {
    init {
        require(molecularWeightGramsPerMole > 0.0)
        require(oralBioavailability in 0.0..1.0)
        require(absorptionRatePerHour > 0.0 && eliminationRatePerHour > 0.0)
        require(centralVolumeLitres > 0.0)
        require(type2AssociationPerNmoleHour > 0.0 && type2DissociationPerHour > 0.0)
        require(totalType2AmountNmole > 0.0)
        require(type2DhtFormationFraction in 0.0..1.0)
        require(dhtTurnoverPerHour > 0.0 && type1InhibitionConstantNm > 0.0)
    }

    /** Effective fitted Kd of the coarse-grained SRD5A2-bound compartment. */
    val effectiveType2DissociationNm: Double
        get() = type2DissociationPerHour /
            (type2AssociationPerNmoleHour * centralVolumeLitres)

    companion object {
        fun suzuki2010(): FinasteridePkPdParameters = FinasteridePkPdParameters(
            molecularWeightGramsPerMole = 372.55,
            oralBioavailability = 0.8,
            absorptionRatePerHour = 1.87,
            eliminationRatePerHour = 0.177,
            centralVolumeLitres = 73.7,
            type2AssociationPerNmoleHour = 0.0293,
            type2DissociationPerHour = 0.0185,
            totalType2AmountNmole = 320.0,
            type2DhtFormationFraction = 0.574,
            dhtTurnoverPerHour = 0.188,
            type1InhibitionConstantNm = 220.0,
        )
    }
}

data class FinasterideRegimen(
    val dailyDoseMg: Double,
    val days: Int,
    val doseIntervalHours: Double = 24.0,
    val integrationStepHours: Double = 0.05,
) {
    init {
        require(dailyDoseMg in 0.0..20.0) { "The interactive domain is 0 to 20 mg/day" }
        require(days in 1..42) { "The interactive horizon is 1 to 42 days" }
        require(doseIntervalHours > 0.0)
        require(integrationStepHours > 0.0 && integrationStepHours <= 0.1)
    }

    val isRepeatedDoseReferenceDomain: Boolean
        get() = dailyDoseMg == 0.0 || dailyDoseMg in 0.01..5.0
}

data class FinasterideKineticState(
    val gutAmountNmole: Double,
    val plasmaConcentrationNm: Double,
    val type2BoundAmountNmole: Double,
    val serumDhtFraction: Double,
)

data class FinasterideCurvePoint(
    val timeHours: Double,
    val plasmaConcentrationNm: Double,
    val type1InhibitionFraction: Double,
    val type2OccupancyFraction: Double,
    val serumDhtFraction: Double,
) {
    val serumDhtSuppressionFraction: Double get() = 1.0 - serumDhtFraction
}

data class FinasterideKineticResult(
    val regimen: FinasterideRegimen,
    val parameters: FinasteridePkPdParameters,
    val curve: List<FinasterideCurvePoint>,
    val finalPoint: FinasterideCurvePoint,
    val peakDhtSuppressionFraction: Double,
    val peakType2OccupancyFraction: Double,
)

/**
 * Population PK/PD projection with saturable 1:1 SRD5A2 binding.
 *
 * This is not a direct AR-antagonist model. Finasteride changes the upstream
 * testosterone-to-DHT production flux. The fitted SRD5A2-bound compartment is
 * a coarse-grained projection of the NADP-dihydrofinasteride inhibited state,
 * whose structural assignment is supported by PDB 7BW1.
 */
class FinasterideKineticModel(
    private val parameters: FinasteridePkPdParameters = FinasteridePkPdParameters.suzuki2010(),
) {
    fun simulate(regimen: FinasterideRegimen): FinasterideKineticResult =
        simulateInternal(regimen)

    fun certify(
        regimen: FinasterideRegimen,
        tolerance: Double = 2e-3,
    ): Evidence<FinasterideKineticResult> {
        require(tolerance > 0.0)
        val coarse = simulateInternal(regimen)
        val refined = simulateInternal(
            regimen.copy(integrationStepHours = regimen.integrationStepHours / 2.0),
        )
        val error = maxOf(
            abs(coarse.finalPoint.serumDhtFraction - refined.finalPoint.serumDhtFraction),
            abs(coarse.finalPoint.type2OccupancyFraction - refined.finalPoint.type2OccupancyFraction),
            abs(coarse.finalPoint.type1InhibitionFraction - refined.finalPoint.type1InhibitionFraction),
        )
        require(error <= tolerance) {
            "RK4 step-halving residual $error exceeded tolerance $tolerance"
        }
        return Evidence(
            value = refined,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The displayed PK/PD trajectory is the step-refined RK4 projection of the identified saturable-binding equations.",
            assumptions = listOf(
                Assumption(
                    AssumptionIds.FINASTERIDE_COARSE_GRAINED_BINDING,
                    "The fitted SRD5A2-bound compartment is Markovian at the population PK/PD time scale.",
                    AssumptionStatus.DECLARED,
                    "Suzuki et al. fitted reversible 1:1 association/dissociation to repeated-dose plasma and DHT profiles.",
                ),
                Assumption(
                    AssumptionIds.FINASTERIDE_POPULATION_PARAMETERS,
                    "Population parameters are used without patient-specific re-identification.",
                    AssumptionStatus.DECLARED,
                    "The UI labels results as a population model and flags doses above the repeated-dose reference domain.",
                ),
            ),
            diagnostics = listOf(
                NumericalDiagnostic("RK4 step-halving terminal-state residual", error, tolerance),
            ),
        )
    }

    private fun simulateInternal(regimen: FinasterideRegimen): FinasterideKineticResult {
        val duration = regimen.days * 24.0
        val totalSteps = ceil(duration / regimen.integrationStepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val doseNmole = regimen.dailyDoseMg * 1_000_000.0 /
            parameters.molecularWeightGramsPerMole

        var state = FinasterideKineticState(
            gutAmountNmole = 0.0,
            plasmaConcentrationNm = 0.0,
            type2BoundAmountNmole = 0.0,
            serumDhtFraction = 1.0,
        )
        var time = 0.0
        var nextDoseTime = 0.0
        var stepIndex = 0
        val points = mutableListOf<FinasterideCurvePoint>()
        var peakDhtSuppression = 0.0
        var peakType2Occupancy = 0.0

        while (true) {
            if (time + 1e-9 >= nextDoseTime && nextDoseTime < duration - 1e-9) {
                state = state.copy(gutAmountNmole = state.gutAmountNmole + doseNmole)
                nextDoseTime += regimen.doseIntervalHours
            }

            val point = observation(time, state)
            peakDhtSuppression = max(peakDhtSuppression, point.serumDhtSuppressionFraction)
            peakType2Occupancy = max(peakType2Occupancy, point.type2OccupancyFraction)
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                if (points.lastOrNull()?.timeHours != time) points += point
            }
            if (time >= duration - 1e-9) break

            val step = minOf(regimen.integrationStepHours, duration - time)
            state = rk4(state, step)
            time = minOf(duration, time + step)
            stepIndex += 1
        }

        return FinasterideKineticResult(
            regimen = regimen,
            parameters = parameters,
            curve = points,
            finalPoint = points.last(),
            peakDhtSuppressionFraction = peakDhtSuppression,
            peakType2OccupancyFraction = peakType2Occupancy,
        )
    }

    private fun rk4(state: FinasterideKineticState, step: Double): FinasterideKineticState {
        val k1 = derivative(state)
        val k2 = derivative(state.plusScaled(k1, step / 2.0))
        val k3 = derivative(state.plusScaled(k2, step / 2.0))
        val k4 = derivative(state.plusScaled(k3, step))
        return FinasterideKineticState(
            gutAmountNmole = nonNegative(
                state.gutAmountNmole + step * (k1.gutAmountNmole + 2.0 * k2.gutAmountNmole +
                    2.0 * k3.gutAmountNmole + k4.gutAmountNmole) / 6.0,
            ),
            plasmaConcentrationNm = nonNegative(
                state.plasmaConcentrationNm + step * (k1.plasmaConcentrationNm +
                    2.0 * k2.plasmaConcentrationNm + 2.0 * k3.plasmaConcentrationNm +
                    k4.plasmaConcentrationNm) / 6.0,
            ),
            type2BoundAmountNmole = (
                state.type2BoundAmountNmole + step * (k1.type2BoundAmountNmole +
                    2.0 * k2.type2BoundAmountNmole + 2.0 * k3.type2BoundAmountNmole +
                    k4.type2BoundAmountNmole) / 6.0
            ).coerceIn(0.0, parameters.totalType2AmountNmole),
            serumDhtFraction = (
                state.serumDhtFraction + step * (k1.serumDhtFraction +
                    2.0 * k2.serumDhtFraction + 2.0 * k3.serumDhtFraction +
                    k4.serumDhtFraction) / 6.0
            ).coerceIn(0.0, 1.0),
        )
    }

    private fun derivative(state: FinasterideKineticState): FinasterideKineticState {
        val gut = nonNegative(state.gutAmountNmole)
        val concentration = nonNegative(state.plasmaConcentrationNm)
        val bound = state.type2BoundAmountNmole.coerceIn(0.0, parameters.totalType2AmountNmole)
        val freeType2 = parameters.totalType2AmountNmole - bound
        val association = parameters.type2AssociationPerNmoleHour * concentration * freeType2
        val dissociation = parameters.type2DissociationPerHour * bound
        val type2Inhibition = bound / parameters.totalType2AmountNmole
        val type1Inhibition = concentration / (concentration + parameters.type1InhibitionConstantNm)
        val residualDhtProduction =
            parameters.type2DhtFormationFraction * (1.0 - type2Inhibition) +
                (1.0 - parameters.type2DhtFormationFraction) * (1.0 - type1Inhibition)

        return FinasterideKineticState(
            gutAmountNmole = -parameters.absorptionRatePerHour * gut,
            plasmaConcentrationNm =
                parameters.absorptionRatePerHour * parameters.oralBioavailability * gut /
                parameters.centralVolumeLitres +
                dissociation / parameters.centralVolumeLitres -
                parameters.eliminationRatePerHour * concentration -
                association,
            type2BoundAmountNmole =
                association * parameters.centralVolumeLitres - dissociation,
            serumDhtFraction = parameters.dhtTurnoverPerHour *
                (residualDhtProduction - state.serumDhtFraction.coerceIn(0.0, 1.0)),
        )
    }

    private fun observation(
        time: Double,
        state: FinasterideKineticState,
    ): FinasterideCurvePoint {
        val concentration = nonNegative(state.plasmaConcentrationNm)
        return FinasterideCurvePoint(
            timeHours = time,
            plasmaConcentrationNm = concentration,
            type1InhibitionFraction =
                concentration / (concentration + parameters.type1InhibitionConstantNm),
            type2OccupancyFraction =
                (state.type2BoundAmountNmole / parameters.totalType2AmountNmole).coerceIn(0.0, 1.0),
            serumDhtFraction = state.serumDhtFraction.coerceIn(0.0, 1.0),
        )
    }

    private fun FinasterideKineticState.plusScaled(
        derivative: FinasterideKineticState,
        scale: Double,
    ): FinasterideKineticState = FinasterideKineticState(
        gutAmountNmole = gutAmountNmole + derivative.gutAmountNmole * scale,
        plasmaConcentrationNm = plasmaConcentrationNm + derivative.plasmaConcentrationNm * scale,
        type2BoundAmountNmole = type2BoundAmountNmole + derivative.type2BoundAmountNmole * scale,
        serumDhtFraction = serumDhtFraction + derivative.serumDhtFraction * scale,
    )

    private fun nonNegative(value: Double): Double = if (value > 0.0) value else 0.0
}
