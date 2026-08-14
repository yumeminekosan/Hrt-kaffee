package dev.hrtkaffee.ar.rigor.thermo

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import dev.hrtkaffee.ar.rigor.exact.ExactMatrix
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import kotlin.math.ln
import kotlin.math.max

data class DetailedBalanceViolation(
    val leftState: Int,
    val rightState: Int,
    val forwardStationaryFlow: Rational,
    val reverseStationaryFlow: Rational,
)

data class DetailedBalanceReport(
    val satisfied: Boolean,
    val violations: List<DetailedBalanceViolation>,
)

data class EntropyProduction(
    val value: Double,
    val hasOneWayStationaryFlow: Boolean,
)

object Thermodynamics {
    fun stationaryDistribution(generator: ExactGenerator): Evidence<List<Rational>> {
        require(generator.isIrreducible()) { "A unique stationary law requires irreducibility here" }
        val size = generator.size
        val transposed = generator.matrix.transpose().toLists().map { it.toMutableList() }.toMutableList()
        transposed[size - 1] = MutableList(size) { Rational.ONE }
        val rightHandSide = MutableList(size) { Rational.ZERO }.apply { this[size - 1] = Rational.ONE }
        val stationary = ExactMatrix.of(transposed).solve(rightHandSide)

        require(stationary.all { it >= Rational.ZERO })
        require(stationary.fold(Rational.ZERO, Rational::plus) == Rational.ONE)
        require(generator.matrix.transpose() * stationary == List(size) { Rational.ZERO })

        return Evidence(
            value = stationary,
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "πQ = 0 and Σπ = 1 hold as exact rational identities.",
            assumptions = generator.structuralAssumptions(),
        )
    }

    fun detailedBalance(
        generator: ExactGenerator,
        stationary: List<Rational>,
    ): Evidence<DetailedBalanceReport> {
        require(stationary.size == generator.size)
        val violations = buildList {
            for (left in 0 until generator.size) {
                for (right in left + 1 until generator.size) {
                    val forward = stationary[left] * generator.matrix[left, right]
                    val reverse = stationary[right] * generator.matrix[right, left]
                    if (forward != reverse) {
                        add(DetailedBalanceViolation(left, right, forward, reverse))
                    }
                }
            }
        }
        return Evidence(
            value = DetailedBalanceReport(violations.isEmpty(), violations),
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "Every pairwise stationary flow equality was compared exactly.",
        )
    }

    fun entropyProductionRate(
        generator: ExactGenerator,
        stationary: List<Rational>,
    ): Evidence<EntropyProduction> {
        require(stationary.size == generator.size)
        var entropyProduction = 0.0
        var oneWayFlow = false
        var largestNegativeRoundoff = 0.0

        for (left in 0 until generator.size) {
            for (right in left + 1 until generator.size) {
                val forward = stationary[left] * generator.matrix[left, right]
                val reverse = stationary[right] * generator.matrix[right, left]
                if (forward == Rational.ZERO && reverse == Rational.ZERO) continue
                if (forward == Rational.ZERO || reverse == Rational.ZERO) {
                    oneWayFlow = true
                    entropyProduction = Double.POSITIVE_INFINITY
                    continue
                }
                val forwardDouble = forward.toDouble()
                val reverseDouble = reverse.toDouble()
                val term = (forwardDouble - reverseDouble) * ln(forwardDouble / reverseDouble)
                if (term < 0.0) largestNegativeRoundoff = max(largestNegativeRoundoff, -term)
                if (entropyProduction.isFinite()) entropyProduction += term
            }
        }

        val reverseSupport = if (oneWayFlow) {
            emptyList()
        } else {
            listOf(
                Assumption(
                    AssumptionIds.POSITIVE_REVERSE_RATES,
                    "Every positive stationary edge flow has positive reverse flow.",
                    AssumptionStatus.CHECKED,
                    "Exact support comparison found reverse flow for every active pair.",
                ),
            )
        }
        return Evidence(
            value = EntropyProduction(entropyProduction, oneWayFlow),
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The logarithmic entropy-production expression is numerical; stationary flows remain exact.",
            assumptions = reverseSupport,
            diagnostics = listOf(
                NumericalDiagnostic(
                    name = "largest negative pair-term from floating-point roundoff",
                    value = largestNegativeRoundoff,
                    tolerance = 1e-12,
                ),
            ),
        )
    }
}
