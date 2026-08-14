package dev.hrtkaffee.ar.rigor.thermo

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import kotlin.math.ln

/** A formal logarithm keeps rate-ratio and cycle-affinity identities exact. */
class FormalLogRational private constructor(val argument: Rational) {
    init {
        require(argument > Rational.ZERO)
    }

    operator fun plus(other: FormalLogRational): FormalLogRational = of(argument * other.argument)

    operator fun unaryMinus(): FormalLogRational = of(argument.reciprocal())

    fun isZero(): Boolean = argument == Rational.ONE

    fun evaluateDouble(): Double = ln(argument.toDouble())

    override fun equals(other: Any?): Boolean =
        other is FormalLogRational && argument == other.argument

    override fun hashCode(): Int = argument.hashCode()

    override fun toString(): String = "log($argument)"

    companion object {
        val ZERO: FormalLogRational = FormalLogRational(Rational.ONE)
        fun of(argument: Rational): FormalLogRational = FormalLogRational(argument)
    }
}

object CycleAffinity {
    /** The cycle is a closed sequence, for example [0,1,2,0]. */
    fun exact(generator: ExactGenerator, closedCycle: List<Int>): Evidence<FormalLogRational> {
        require(closedCycle.size >= 2 && closedCycle.first() == closedCycle.last())
        require(closedCycle.all { it in 0 until generator.size })
        var ratio = Rational.ONE
        closedCycle.zipWithNext().forEach { (source, target) ->
            val forward = generator.matrix[source, target]
            val reverse = generator.matrix[target, source]
            require(forward > Rational.ZERO && reverse > Rational.ZERO) {
                "Finite cycle affinity requires positive forward and reverse rates"
            }
            ratio *= forward / reverse
        }
        val reverseSupport = Assumption(
            AssumptionIds.POSITIVE_REVERSE_RATES,
            "Every edge in the selected thermodynamic cycle has positive reverse support.",
            AssumptionStatus.CHECKED,
            "All directed rate pairs were compared as exact rationals.",
        )
        return Evidence(
            value = FormalLogRational.of(ratio),
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "The cycle affinity is the formal logarithm of the exact product of forward/reverse rate ratios.",
            assumptions = listOf(reverseSupport),
        )
    }
}
