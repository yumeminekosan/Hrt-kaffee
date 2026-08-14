package dev.hrtkaffee.ar.rigor.markov

import kotlin.math.ln
import kotlin.random.Random

/** A stochastic generator is simulable. A tilted operator intentionally does not implement this interface. */
interface StochasticGenerator {
    val stateCount: Int
    fun rate(source: Int, target: Int): Double
}

class ExactGeneratorKernel(private val generator: ExactGenerator) : StochasticGenerator {
    override val stateCount: Int = generator.size

    override fun rate(source: Int, target: Int): Double = generator.matrix[source, target].toDouble()
}

data class HoldingInterval(
    val state: Int,
    val startTime: Double,
    val endTime: Double,
) {
    init {
        require(state >= 0)
        require(startTime.isFinite() && endTime.isFinite())
        require(startTime >= 0.0 && endTime >= startTime)
    }

    val duration: Double get() = endTime - startTime
}

data class SimulatedPath(
    val initialState: Int,
    val finalState: Int,
    val horizon: Double,
    val intervals: List<HoldingInterval>,
)

class GillespieSimulator(private val random: Random) {
    fun simulate(
        generator: StochasticGenerator,
        initialState: Int,
        horizon: Double,
        maximumJumps: Int = 1_000_000,
    ): SimulatedPath {
        require(initialState in 0 until generator.stateCount)
        require(horizon.isFinite() && horizon >= 0.0)
        require(maximumJumps > 0)

        var time = 0.0
        var state = initialState
        var jumps = 0
        val intervals = mutableListOf<HoldingInterval>()

        while (time < horizon) {
            val outgoing = (0 until generator.stateCount)
                .filter { it != state }
                .map { target -> target to generator.rate(state, target) }
                .filter { (_, rate) -> rate > 0.0 }
            require(outgoing.all { (_, rate) -> rate.isFinite() })
            val exitRate = outgoing.sumOf { it.second }
            if (exitRate == 0.0) {
                intervals += HoldingInterval(state, time, horizon)
                time = horizon
                break
            }

            val uniform = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
            val holdingTime = -ln(uniform) / exitRate
            val nextTime = time + holdingTime
            if (nextTime >= horizon) {
                intervals += HoldingInterval(state, time, horizon)
                time = horizon
                break
            }

            intervals += HoldingInterval(state, time, nextTime)
            var threshold = random.nextDouble() * exitRate
            var nextState = outgoing.last().first
            for ((candidate, rate) in outgoing) {
                threshold -= rate
                if (threshold <= 0.0) {
                    nextState = candidate
                    break
                }
            }
            state = nextState
            time = nextTime
            jumps += 1
            require(jumps <= maximumJumps) { "Maximum jump cap exceeded" }
        }

        return SimulatedPath(initialState, state, horizon, intervals)
    }
}
