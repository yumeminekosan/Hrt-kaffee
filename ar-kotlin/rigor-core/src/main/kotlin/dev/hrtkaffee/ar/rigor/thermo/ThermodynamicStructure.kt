package dev.hrtkaffee.ar.rigor.thermo

import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator

/** Exact activity a/a° = γ(c/c°); no ideal-mixture assumption is hidden here. */
data class ExactActivity(
    val concentration: Rational,
    val activityCoefficient: Rational,
    val standardConcentration: Rational = Rational.ONE,
) {
    init {
        require(concentration > Rational.ZERO)
        require(activityCoefficient > Rational.ZERO)
        require(standardConcentration > Rational.ZERO)
    }

    val relativeActivity: Rational
        get() = activityCoefficient * concentration / standardConcentration

    /** Dimensionless increment β(μ−μ°)=log(a/a°), kept as a formal exact logarithm. */
    fun chemicalPotentialIncrement(): FormalLogRational =
        FormalLogRational.of(relativeActivity)
}

/** A positive Gibbs/Boltzmann weight represents exp(−βF) without evaluating a logarithm. */
data class FormalFreeEnergy(val boltzmannWeight: Rational) {
    init {
        require(boltzmannWeight > Rational.ZERO)
    }

    /** β[F(target)−F(this)] = log(w_this/w_target). */
    fun changeTo(target: FormalFreeEnergy): FormalLogRational =
        FormalLogRational.of(boltzmannWeight / target.boltzmannWeight)
}

data class LocalDetailedBalanceViolation(
    val source: Int,
    val target: Int,
    val actualRateRatio: Rational?,
    val expectedRateRatio: Rational?,
    val reason: String,
)

data class LocalDetailedBalanceReport(
    val satisfied: Boolean,
    val violations: List<LocalDetailedBalanceViolation>,
)

/**
 * Exact multiplicative local detailed balance audit
 *
 * q(x,y)/q(y,x) = [w(y)/w(x)] R(x,y),
 *
 * where w=exp(−βF) and R is the declared reservoir/activity driving factor.
 * R=1 is the equilibrium special case. Physical interpretation of w and R remains
 * a modelling input; the audit only certifies the stated algebraic relation.
 */
object LocalDetailedBalance {
    fun audit(
        generator: ExactGenerator,
        stateFreeEnergies: List<FormalFreeEnergy>,
        reservoirFactor: (source: Int, target: Int) -> Rational = { _, _ -> Rational.ONE },
    ): Evidence<LocalDetailedBalanceReport> {
        require(stateFreeEnergies.size == generator.size)
        val violations = mutableListOf<LocalDetailedBalanceViolation>()

        for (source in 0 until generator.size) {
            for (target in source + 1 until generator.size) {
                val forward = generator.matrix[source, target]
                val reverse = generator.matrix[target, source]
                if (forward == Rational.ZERO && reverse == Rational.ZERO) continue
                if (forward == Rational.ZERO || reverse == Rational.ZERO) {
                    violations += LocalDetailedBalanceViolation(
                        source = source,
                        target = target,
                        actualRateRatio = null,
                        expectedRateRatio = null,
                        reason = "Local detailed balance requires reverse support on every active edge.",
                    )
                    continue
                }

                val forwardReservoir = reservoirFactor(source, target)
                val reverseReservoir = reservoirFactor(target, source)
                require(forwardReservoir > Rational.ZERO && reverseReservoir > Rational.ZERO)
                if (forwardReservoir * reverseReservoir != Rational.ONE) {
                    violations += LocalDetailedBalanceViolation(
                        source = source,
                        target = target,
                        actualRateRatio = forward / reverse,
                        expectedRateRatio = null,
                        reason = "Opposite reservoir factors are not exact reciprocals.",
                    )
                    continue
                }

                val actual = forward / reverse
                val expected = stateFreeEnergies[target].boltzmannWeight /
                    stateFreeEnergies[source].boltzmannWeight * forwardReservoir
                if (actual != expected) {
                    violations += LocalDetailedBalanceViolation(
                        source = source,
                        target = target,
                        actualRateRatio = actual,
                        expectedRateRatio = expected,
                        reason = "The exact rate ratio differs from the free-energy plus reservoir ratio.",
                    )
                }
            }
        }

        return Evidence(
            value = LocalDetailedBalanceReport(violations.isEmpty(), violations),
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "Every local detailed-balance ratio was compared as an exact rational identity.",
        )
    }
}
