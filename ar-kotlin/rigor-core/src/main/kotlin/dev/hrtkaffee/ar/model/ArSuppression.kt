package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational

@JvmInline
value class BasisPoints(val value: Int) {
    init {
        require(value in 0..10_000) { "Basis points must be in [0, 10 000]" }
    }

    fun asFraction(): Rational = Rational.of(value.toLong(), 10_000L)
}

data class ArIntervention(
    val directArCompetition: BasisPoints,
    val fiveAlphaReductaseInhibition: BasisPoints,
)

data class ArEquilibriumParameters(
    val testosteroneExposure: Rational,
    val fiveAlphaConversionRate: Rational,
    val dhtClearanceRate: Rational,
    val maximumAntagonistExposure: Rational,
    val testosteroneDissociation: Rational,
    val dhtDissociation: Rational,
    val antagonistDissociation: Rational,
    val testosteroneEfficacy: Rational,
    val dhtEfficacy: Rational,
    val antagonistEfficacy: Rational,
) {
    init {
        val positive = listOf(
            testosteroneExposure,
            fiveAlphaConversionRate,
            dhtClearanceRate,
            maximumAntagonistExposure,
            testosteroneDissociation,
            dhtDissociation,
            antagonistDissociation,
        )
        require(positive.all { it > Rational.ZERO })
        require(listOf(testosteroneEfficacy, dhtEfficacy, antagonistEfficacy).all { it >= Rational.ZERO })
    }

    companion object {
        /** Dimensionless demonstration values. They are not fitted clinical parameters. */
        fun illustrative(): ArEquilibriumParameters = ArEquilibriumParameters(
            testosteroneExposure = Rational.of(4),
            fiveAlphaConversionRate = Rational.of(1, 2),
            dhtClearanceRate = Rational.ONE,
            maximumAntagonistExposure = Rational.of(8),
            testosteroneDissociation = Rational.of(2),
            dhtDissociation = Rational.of(1, 2),
            antagonistDissociation = Rational.ONE,
            testosteroneEfficacy = Rational.ONE,
            dhtEfficacy = Rational.of(3, 2),
            antagonistEfficacy = Rational.ZERO,
        )
    }
}

data class ArCounterfactualSignals(
    /** No direct competitor, no 5αR inhibitor. */
    val control: Rational,
    /** Direct competitor only. */
    val directOnly: Rational,
    /** 5αR inhibitor only. */
    val upstreamOnly: Rational,
    /** Both interventions. */
    val combined: Rational,
)

data class ArSuppressionResult(
    val intervention: ArIntervention,
    val counterfactuals: ArCounterfactualSignals,
    val signalRelativeToControl: Rational,
    val directShapleyContribution: Rational,
    val upstreamShapleyContribution: Rational,
    val directConditionalEffect: Rational,
    val upstreamConditionalEffect: Rational,
    val nonAdditivity: Rational,
    val exactModelEvidence: Evidence<ArCounterfactualSignals>,
    val parameterEvidence: Evidence<ArEquilibriumParameters>,
)

/**
 * Competitive equilibrium plus a declared quasi-steady DHT production relation.
 * All counterfactual algebra is exact rational arithmetic.
 */
class ArSuppressionModel(private val parameters: ArEquilibriumParameters) {
    private val assumptions = listOf(
        Assumption(
            AssumptionIds.QUASI_STEADY_DHT,
            "DHT production and clearance are in quasi-steady balance for each counterfactual.",
            AssumptionStatus.DECLARED,
            "DHT = k5α · (1 − inhibition) · T / kclear is the selected reduced model.",
        ),
        Assumption(
            AssumptionIds.FIXED_T_RESERVOIR,
            "Testosterone exposure is held fixed across the four counterfactual worlds.",
            AssumptionStatus.DECLARED,
            "The reduced panel does not model endocrine feedback or conservation of the full androgen pool.",
        ),
    )

    fun evaluate(intervention: ArIntervention): ArSuppressionResult {
        val zero = BasisPoints(0)
        val control = signal(ArIntervention(zero, zero))
        val directOnly = signal(ArIntervention(intervention.directArCompetition, zero))
        val upstreamOnly = signal(ArIntervention(zero, intervention.fiveAlphaReductaseInhibition))
        val combined = signal(intervention)
        val signals = ArCounterfactualSignals(control, directOnly, upstreamOnly, combined)

        val directShapley = (
            (control - directOnly) + (upstreamOnly - combined)
        ) / Rational.TWO / control
        val upstreamShapley = (
            (control - upstreamOnly) + (directOnly - combined)
        ) / Rational.TWO / control

        val directConditional = (upstreamOnly - combined) / control
        val upstreamConditional = (directOnly - combined) / control
        val combinedSuppression = (control - combined) / control
        val directOnlySuppression = (control - directOnly) / control
        val upstreamOnlySuppression = (control - upstreamOnly) / control

        return ArSuppressionResult(
            intervention = intervention,
            counterfactuals = signals,
            signalRelativeToControl = combined / control,
            directShapleyContribution = directShapley,
            upstreamShapleyContribution = upstreamShapley,
            directConditionalEffect = directConditional,
            upstreamConditionalEffect = upstreamConditional,
            nonAdditivity = combinedSuppression - directOnlySuppression - upstreamOnlySuppression,
            exactModelEvidence = Evidence(
                value = signals,
                kind = EvidenceKind.EXACT_IDENTITY,
                claim = "The four signals and their counterfactual decomposition are exact inside the declared reduced model.",
                assumptions = assumptions,
            ),
            parameterEvidence = Evidence(
                value = parameters,
                kind = EvidenceKind.ILLUSTRATIVE_PARAMETERIZATION,
                claim = "Default dimensionless parameters demonstrate the mechanism split; they are not patient estimates.",
                assumptions = assumptions,
            ),
        )
    }

    private fun signal(intervention: ArIntervention): Rational {
        val residualFiveAlphaActivity =
            Rational.ONE - intervention.fiveAlphaReductaseInhibition.asFraction()
        val dhtExposure = parameters.fiveAlphaConversionRate * residualFiveAlphaActivity *
            parameters.testosteroneExposure / parameters.dhtClearanceRate
        val antagonistExposure = parameters.maximumAntagonistExposure *
            intervention.directArCompetition.asFraction()

        val testosteroneWeight = parameters.testosteroneExposure / parameters.testosteroneDissociation
        val dhtWeight = dhtExposure / parameters.dhtDissociation
        val antagonistWeight = antagonistExposure / parameters.antagonistDissociation
        val partition = Rational.ONE + testosteroneWeight + dhtWeight + antagonistWeight

        return (
            parameters.testosteroneEfficacy * testosteroneWeight +
                parameters.dhtEfficacy * dhtWeight +
                parameters.antagonistEfficacy * antagonistWeight
        ) / partition
    }
}
