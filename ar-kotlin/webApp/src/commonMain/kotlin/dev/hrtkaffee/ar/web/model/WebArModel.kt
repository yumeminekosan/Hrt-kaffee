package dev.hrtkaffee.ar.web.model

import kotlin.jvm.JvmInline
import kotlin.math.abs

/**
 * Exact, normalized 64-bit rational arithmetic for the bounded browser controls.
 *
 * The production JVM rigor module intentionally keeps its arbitrary-precision
 * BigInteger implementation. This browser type uses cross-cancellation before
 * multiplication; the declared 0..10 000 basis-point domain stays far inside
 * Long range and is checked against the JVM model in [WebArModelParityTest].
 */
class Rational private constructor(
    val numerator: Long,
    val denominator: Long,
) : Comparable<Rational> {
    init {
        require(denominator > 0L) { "The denominator must be positive" }
        require(gcd(numerator, denominator) == 1L) { "Rational must be normalized" }
    }

    operator fun plus(other: Rational): Rational {
        val common = gcd(denominator, other.denominator)
        val leftScale = other.denominator / common
        val rightScale = denominator / common
        return of(
            numerator * leftScale + other.numerator * rightScale,
            denominator * leftScale,
        )
    }

    operator fun minus(other: Rational): Rational = this + -other

    operator fun times(other: Rational): Rational {
        val leftCancellation = gcd(numerator, other.denominator)
        val rightCancellation = gcd(other.numerator, denominator)
        return of(
            (numerator / leftCancellation) * (other.numerator / rightCancellation),
            (denominator / rightCancellation) * (other.denominator / leftCancellation),
        )
    }

    operator fun div(other: Rational): Rational {
        require(other != ZERO) { "Division by zero" }
        return this * of(other.denominator, other.numerator)
    }

    operator fun unaryMinus(): Rational = of(-numerator, denominator)

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    override fun compareTo(other: Rational): Int = (this - other).numerator.compareTo(0L)

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = if (denominator == 1L) "$numerator" else "$numerator/$denominator"

    companion object {
        val ZERO: Rational = Rational(0L, 1L)
        val ONE: Rational = Rational(1L, 1L)
        val TWO: Rational = Rational(2L, 1L)

        fun of(value: Int): Rational = of(value.toLong())

        fun of(value: Long): Rational = Rational(value, 1L)

        fun of(numerator: Int, denominator: Int): Rational =
            of(numerator.toLong(), denominator.toLong())

        fun of(numerator: Long, denominator: Long): Rational {
            require(denominator != 0L) { "The denominator cannot be zero" }
            if (numerator == 0L) return ZERO

            val signedNumerator = if (denominator < 0L) -numerator else numerator
            val positiveDenominator = abs(denominator)
            val common = gcd(signedNumerator, positiveDenominator)
            return Rational(signedNumerator / common, positiveDenominator / common)
        }

        private fun gcd(first: Long, second: Long): Long {
            var a = abs(first)
            var b = abs(second)
            while (b != 0L) {
                val remainder = a % b
                a = b
                b = remainder
            }
            return if (a == 0L) 1L else a
        }
    }
}

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
    companion object {
        /** Dimensionless demonstration values; never fitted clinical parameters. */
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
    val control: Rational,
    val directOnly: Rational,
    val upstreamOnly: Rational,
    val combined: Rational,
)

data class ModelEvidence(
    val kind: String,
    val claim: String,
    val assumptions: List<String>,
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
    val exactModelEvidence: ModelEvidence,
)

/** Browser projection of the audited exact four-world AR equilibrium. */
class ArSuppressionModel(private val parameters: ArEquilibriumParameters) {
    private val assumptions = listOf(
        "DHT production and clearance are in quasi-steady balance for each counterfactual.",
        "Testosterone exposure is held fixed across the four counterfactual worlds.",
    )

    fun evaluate(intervention: ArIntervention): ArSuppressionResult {
        val zero = BasisPoints(0)
        val control = signal(ArIntervention(zero, zero))
        val directOnly = signal(ArIntervention(intervention.directArCompetition, zero))
        val upstreamOnly = signal(ArIntervention(zero, intervention.fiveAlphaReductaseInhibition))
        val combined = signal(intervention)
        val signals = ArCounterfactualSignals(control, directOnly, upstreamOnly, combined)

        val directShapley =
            ((control - directOnly) + (upstreamOnly - combined)) / Rational.TWO / control
        val upstreamShapley =
            ((control - upstreamOnly) + (directOnly - combined)) / Rational.TWO / control
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
            exactModelEvidence = ModelEvidence(
                kind = "EXACT_IDENTITY",
                claim = "Exact inside the declared reduced equilibrium model.",
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
