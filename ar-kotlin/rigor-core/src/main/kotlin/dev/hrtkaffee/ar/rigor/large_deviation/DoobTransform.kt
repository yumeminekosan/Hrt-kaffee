package dev.hrtkaffee.ar.rigor.large_deviation

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.StochasticGenerator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * A Feynman–Kac tilted operator. It is intentionally not a StochasticGenerator:
 * its rows do not sum to zero and direct Gillespie sampling would be invalid.
 */
class TiltedOperator private constructor(private val values: Array<DoubleArray>) {
    val size: Int = values.size

    operator fun get(row: Int, column: Int): Double = values[row][column]

    fun multiply(vector: DoubleArray): DoubleArray {
        require(vector.size == size)
        return DoubleArray(size) { row ->
            (0 until size).sumOf { column -> values[row][column] * vector[column] }
        }
    }

    companion object {
        fun currentTilt(
            generator: ExactGenerator,
            edgeIncrement: List<List<Double>>,
            tilt: Double,
            statePotential: List<Double> = List(generator.size) { 0.0 },
        ): TiltedOperator {
            require(tilt.isFinite())
            require(edgeIncrement.size == generator.size && edgeIncrement.all { it.size == generator.size })
            require(statePotential.size == generator.size && statePotential.all(Double::isFinite))
            val matrix = Array(generator.size) { source ->
                DoubleArray(generator.size) { target ->
                    when {
                        source == target -> generator.matrix[source, source].toDouble() +
                            tilt * statePotential[source]
                        else -> generator.matrix[source, target].toDouble() *
                            exp(tilt * edgeIncrement[source][target])
                    }
                }
            }
            return TiltedOperator(matrix)
        }
    }
}

data class PrincipalEigenpair(
    val eigenvalue: Double,
    val positiveRightEigenvector: DoubleArray,
    val residualInfinityNorm: Double,
    val iterations: Int,
)

object PrincipalEigenSolver {
    fun solve(
        operator: TiltedOperator,
        irreducibleBase: Boolean,
        tolerance: Double = 1e-11,
        maximumIterations: Int = 50_000,
    ): Evidence<PrincipalEigenpair> {
        require(irreducibleBase) { "Perron–Frobenius uniqueness requires an irreducible base process" }
        require(tolerance > 0.0 && maximumIterations > 0)
        val shift = (0 until operator.size).maxOf { row -> -operator[row, row] } + 1.0
        var vector = DoubleArray(operator.size) { 1.0 / operator.size }
        var iterations = 0

        while (iterations < maximumIterations) {
            val multiplied = operator.multiply(vector)
            val shifted = DoubleArray(operator.size) { index -> multiplied[index] + shift * vector[index] }
            require(shifted.all { it.isFinite() && it > 0.0 })
            val norm = shifted.sum()
            val next = DoubleArray(operator.size) { shifted[it] / norm }
            val difference = next.indices.maxOf { abs(next[it] - vector[it]) }
            vector = next
            iterations += 1
            if (difference <= tolerance) break
        }

        val applied = operator.multiply(vector)
        val ratios = vector.indices.map { index -> applied[index] / vector[index] }
        val eigenvalue = ratios.average()
        val residual = vector.indices.maxOf { index ->
            abs(applied[index] - eigenvalue * vector[index])
        }
        val eigenpair = PrincipalEigenpair(eigenvalue, vector.copyOf(), residual, iterations)
        val assumption = Assumption(
            AssumptionIds.PRINCIPAL_EIGENPAIR,
            "The tilted irreducible Metzler operator has a simple principal eigenvalue and positive right eigenvector.",
            AssumptionStatus.CHECKED,
            "The base graph is irreducible and the shifted power iteration retained strict positivity.",
        )
        return Evidence(
            value = eigenpair,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The principal eigenpair is accepted only with an explicit infinity-norm residual.",
            assumptions = listOf(assumption),
            diagnostics = listOf(
                NumericalDiagnostic("principal eigenpair residual", residual, tolerance * 20.0),
            ),
        )
    }
}

class DrivenGenerator internal constructor(private val values: Array<DoubleArray>) : StochasticGenerator {
    override val stateCount: Int = values.size

    init {
        require(values.isNotEmpty() && values.all { it.size == stateCount })
        values.indices.forEach { row ->
            require(values[row][row] <= 0.0)
            require(values[row].indices.all { column -> row == column || values[row][column] >= 0.0 })
            require(abs(values[row].sum()) <= 1e-10)
        }
    }

    override fun rate(source: Int, target: Int): Double = values[source][target]

    fun row(row: Int): DoubleArray = values[row].copyOf()
}

object GeneralizedDoobTransform {
    fun build(
        operator: TiltedOperator,
        eigenpairEvidence: Evidence<PrincipalEigenpair>,
        tolerance: Double = 1e-9,
    ): Evidence<DrivenGenerator> {
        require(eigenpairEvidence.kind == EvidenceKind.NUMERICAL_CERTIFICATE)
        require(eigenpairEvidence.diagnostics.all { it.passed }) {
            "A failed principal-eigenpair residual cannot drive a stochastic process"
        }
        val eigenpair = eigenpairEvidence.value
        val right = eigenpair.positiveRightEigenvector
        require(right.size == operator.size && right.all { it.isFinite() && it > 0.0 })

        val rates = Array(operator.size) { DoubleArray(operator.size) }
        for (source in 0 until operator.size) {
            for (target in 0 until operator.size) {
                if (source == target) continue
                rates[source][target] = operator[source, target] * right[target] / right[source]
            }
            rates[source][source] = -rates[source].sum()
        }

        var consistencyResidual = 0.0
        for (state in 0 until operator.size) {
            consistencyResidual = max(
                consistencyResidual,
                abs(rates[state][state] - (operator[state, state] - eigenpair.eigenvalue)),
            )
        }
        val driven = DrivenGenerator(rates)
        return Evidence(
            value = driven,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "Only the principal-eigenvector Doob transform is exposed as a Gillespie-compatible generator.",
            assumptions = eigenpairEvidence.assumptions,
            diagnostics = listOf(
                NumericalDiagnostic("Doob diagonal/eigen-equation consistency", consistencyResidual, tolerance),
            ),
        )
    }
}
