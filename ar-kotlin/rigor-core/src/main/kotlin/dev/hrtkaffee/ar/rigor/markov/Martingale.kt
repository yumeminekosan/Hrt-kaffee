package dev.hrtkaffee.ar.rigor.markov

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import kotlin.math.sqrt
import kotlin.random.Random

data class MartingaleEstimate(
    val samples: Int,
    val mean: Double,
    val standardError: Double,
    val confidence95: ClosedFloatingPointRange<Double>,
)

object DynkinMartingale {
    /** Exact uniform bound on Γf(x)=Σy qxy(f(y)−f(x))² over the finite state space. */
    fun carréDuChampBound(
        generator: ExactGenerator,
        testFunction: List<Rational>,
    ): Evidence<Rational> {
        require(testFunction.size == generator.size)
        val bound = (0 until generator.size).maxOf { source ->
            (0 until generator.size).fold(Rational.ZERO) { total, target ->
                if (source == target) {
                    total
                } else {
                    val difference = testFunction[target] - testFunction[source]
                    total + generator.matrix[source, target] * difference.pow(2)
                }
            }
        }
        return Evidence(
            value = bound,
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "The finite-state carré-du-champ bound is exact; on horizon t it bounds E[M_t²] by t·bound.",
            assumptions = generator.structuralAssumptions().filter { it.status != AssumptionStatus.FAILED },
        )
    }

    fun terminalValue(
        generator: ExactGenerator,
        testFunction: List<Rational>,
        path: SimulatedPath,
    ): Double {
        require(testFunction.size == generator.size)
        val drift = generator.applyTo(testFunction)
        val compensator = path.intervals.sumOf { interval ->
            drift[interval.state].toDouble() * interval.duration
        }
        return testFunction[path.finalState].toDouble() -
            testFunction[path.initialState].toDouble() - compensator
    }

    fun estimateMean(
        generator: ExactGenerator,
        testFunction: List<Rational>,
        initialState: Int,
        horizon: Double,
        trajectories: Int,
        seed: Int,
    ): Evidence<MartingaleEstimate> {
        require(trajectories >= 2)
        val simulator = GillespieSimulator(Random(seed))
        val kernel = ExactGeneratorKernel(generator)
        val values = DoubleArray(trajectories) {
            terminalValue(
                generator,
                testFunction,
                simulator.simulate(kernel, initialState, horizon),
            )
        }
        val mean = values.average()
        val sampleVariance = values.sumOf { (it - mean) * (it - mean) } / (trajectories - 1)
        val standardError = sqrt(sampleVariance / trajectories)
        val halfWidth = 1.96 * standardError

        val boundedTestFunction = Assumption(
            AssumptionIds.BOUNDED_RATES,
            "The finite-state test function and generator rates are bounded, so the Dynkin local martingale is integrable.",
            AssumptionStatus.CHECKED,
            "The generator and test-function vectors were explicitly enumerated.",
        )
        return Evidence(
            value = MartingaleEstimate(
                samples = trajectories,
                mean = mean,
                standardError = standardError,
                confidence95 = (mean - halfWidth)..(mean + halfWidth),
            ),
            kind = EvidenceKind.MONTE_CARLO_ESTIMATE,
            claim = "The empirical terminal Dynkin martingale mean is reported with a 95% normal interval.",
            assumptions = listOf(boundedTestFunction),
        )
    }
}
