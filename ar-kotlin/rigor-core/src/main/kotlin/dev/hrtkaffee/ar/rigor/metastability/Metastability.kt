package dev.hrtkaffee.ar.rigor.metastability

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import dev.hrtkaffee.ar.rigor.exact.ExactMatrix
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import kotlin.math.abs

object FirstPassageSolver {
    /** Exact solution of −Q_B h = 1 with h=0 on the target set. */
    fun meanHittingTimes(
        generator: ExactGenerator,
        targets: Set<Int>,
    ): Evidence<List<Rational>> {
        require(targets.isNotEmpty() && targets.all { it in 0 until generator.size })
        val transient = (0 until generator.size).filterNot(targets::contains)
        if (transient.isEmpty()) {
            return Evidence(
                value = List(generator.size) { Rational.ZERO },
                kind = EvidenceKind.EXACT_IDENTITY,
                claim = "All states are targets, so every hitting time is exactly zero.",
            )
        }

        val killedSystem = transient.map { source ->
            transient.map { target -> -generator.matrix[source, target] }
        }
        val solution = ExactMatrix.of(killedSystem).solve(List(transient.size) { Rational.ONE })
        require(solution.all { it >= Rational.ZERO })
        val full = MutableList(generator.size) { Rational.ZERO }
        transient.forEachIndexed { index, state -> full[state] = solution[index] }

        return Evidence(
            value = full,
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "Finite-state mean first-passage equations were solved with exact rationals.",
            assumptions = generator.structuralAssumptions().filter { it.status != AssumptionStatus.FAILED },
        )
    }
}

data class QuasiStationaryResult(
    val basinStates: List<Int>,
    val distribution: DoubleArray,
    val escapeRate: Double,
    val meanLifetime: Double,
    val iterations: Int,
)

object QuasiStationarySolver {
    /** Left Perron vector of the killed semigroup, computed after uniformization. */
    fun solve(
        generator: ExactGenerator,
        basin: Set<Int>,
        tolerance: Double = 1e-11,
        maximumIterations: Int = 100_000,
    ): Evidence<QuasiStationaryResult> {
        require(basin.isNotEmpty() && basin.size < generator.size)
        require(basin.all { it in 0 until generator.size })
        require(tolerance > 0.0 && maximumIterations > 0)
        val states = basin.sorted()
        val uniformizationRate = states.maxOf { -generator.matrix[it, it].toDouble() } + 1.0
        val transition = Array(states.size) { localSource ->
            DoubleArray(states.size) { localTarget ->
                val source = states[localSource]
                val target = states[localTarget]
                val identity = if (localSource == localTarget) 1.0 else 0.0
                identity + generator.matrix[source, target].toDouble() / uniformizationRate
            }
        }

        var distribution = DoubleArray(states.size) { 1.0 / states.size }
        var iterations = 0
        var survivalEigenvalue = 1.0
        while (iterations < maximumIterations) {
            val propagated = DoubleArray(states.size) { target ->
                states.indices.sumOf { source -> distribution[source] * transition[source][target] }
            }
            survivalEigenvalue = propagated.sum()
            require(survivalEigenvalue.isFinite() && survivalEigenvalue > 0.0)
            val next = DoubleArray(states.size) { propagated[it] / survivalEigenvalue }
            val difference = states.indices.maxOf { abs(next[it] - distribution[it]) }
            distribution = next
            iterations += 1
            if (difference <= tolerance) break
        }

        val propagated = DoubleArray(states.size) { target ->
            states.indices.sumOf { source -> distribution[source] * transition[source][target] }
        }
        val residual = states.indices.maxOf { index ->
            abs(propagated[index] - survivalEigenvalue * distribution[index])
        }
        val escapeRate = uniformizationRate * (1.0 - survivalEigenvalue)
        require(escapeRate.isFinite() && escapeRate > 0.0)
        val result = QuasiStationaryResult(
            basinStates = states,
            distribution = distribution.copyOf(),
            escapeRate = escapeRate,
            meanLifetime = 1.0 / escapeRate,
            iterations = iterations,
        )
        val assumption = Assumption(
            AssumptionIds.IRREDUCIBLE,
            "The killed dynamics inside the proposed basin has a unique positive quasi-stationary mode.",
            AssumptionStatus.DECLARED,
            "The numerical positive eigenvector is accompanied by a residual; graph irreducibility should be audited for each basin.",
        )
        return Evidence(
            value = result,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The basin quasi-stationary law and escape timescale come from the killed generator, not trajectory eyeballing.",
            assumptions = listOf(assumption),
            diagnostics = listOf(
                NumericalDiagnostic("quasi-stationary eigenvector residual", residual, tolerance * 20.0),
            ),
        )
    }
}
