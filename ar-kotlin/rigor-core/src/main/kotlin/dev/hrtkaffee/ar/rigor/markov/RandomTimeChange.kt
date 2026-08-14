package dev.hrtkaffee.ar.rigor.markov

import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import kotlin.math.ln
import kotlin.random.Random

data class ReactionHoldingInterval(
    val state: PopulationState,
    val startTime: Double,
    val endTime: Double,
) {
    init {
        require(startTime.isFinite() && endTime.isFinite())
        require(startTime >= 0.0 && endTime >= startTime)
    }

    val duration: Double get() = endTime - startTime
}

data class ReactionJump(
    val time: Double,
    val reactionIndex: Int,
    val source: PopulationState,
    val target: PopulationState,
)

/** Holding intervals encode a right-continuous, left-limited pure-jump path. */
data class SimulatedReactionPath(
    val initialState: PopulationState,
    val finalState: PopulationState,
    val horizon: Double,
    val intervals: List<ReactionHoldingInterval>,
    val jumps: List<ReactionJump>,
) {
    init {
        require(horizon.isFinite() && horizon >= 0.0)
        require(intervals.zipWithNext().all { (left, right) ->
            left.endTime == right.startTime
        })
        require(jumps.zipWithNext().all { (left, right) -> left.time <= right.time })
        require(jumps.all { it.time in 0.0..horizon })
        if (horizon > 0.0) {
            require(intervals.isNotEmpty())
            require(intervals.first().startTime == 0.0)
            require(intervals.last().endTime == horizon)
        }
    }
}

/**
 * Direct-reaction Gillespie sampler. Reaction labels are retained so the random-time-change
 * reaction clocks Y_r(∫a_r ds) and their compensated counts can be audited afterwards.
 */
class ReactionNetworkGillespieSimulator(private val random: Random) {
    fun simulate(
        network: ReactionNetwork,
        initialState: PopulationState,
        horizon: Double,
        maximumJumps: Int = 1_000_000,
    ): SimulatedReactionPath {
        require(initialState.counts.size == network.species.size)
        require(horizon.isFinite() && horizon >= 0.0)
        require(maximumJumps > 0)

        var time = 0.0
        var state = initialState
        var jumpCount = 0
        val intervals = mutableListOf<ReactionHoldingInterval>()
        val jumps = mutableListOf<ReactionJump>()

        while (time < horizon) {
            val propensities = network.reactions.map { it.propensity(state).toDouble() }
            require(propensities.all { it.isFinite() && it >= 0.0 })
            val exitRate = propensities.sum()
            if (exitRate == 0.0) {
                intervals += ReactionHoldingInterval(state, time, horizon)
                time = horizon
                break
            }

            val holdingTime = -ln(random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / exitRate
            val nextTime = time + holdingTime
            if (nextTime >= horizon) {
                intervals += ReactionHoldingInterval(state, time, horizon)
                time = horizon
                break
            }

            intervals += ReactionHoldingInterval(state, time, nextTime)
            var threshold = random.nextDouble() * exitRate
            var selected = propensities.indexOfLast { it > 0.0 }
            for (reactionIndex in propensities.indices) {
                threshold -= propensities[reactionIndex]
                if (threshold <= 0.0) {
                    selected = reactionIndex
                    break
                }
            }
            require(selected >= 0)
            val target = requireNotNull(network.reactions[selected].apply(state))
            jumps += ReactionJump(nextTime, selected, state, target)
            state = target
            time = nextTime
            jumpCount += 1
            require(jumpCount <= maximumJumps) { "Maximum jump cap exceeded" }
        }

        return SimulatedReactionPath(initialState, state, horizon, intervals, jumps)
    }
}

data class ReactionCountIdentity(
    val reactionCounts: List<Int>,
    val observedStateChange: List<Int>,
    val stoichiometricClockSum: List<Int>,
)

data class CompensatedReactionCounts(
    val reactionCounts: List<Int>,
    val integratedIntensities: List<Double>,
    val martingaleTerminalValues: List<Double>,
    val predictableQuadraticVariations: List<Double>,
)

object RandomTimeChange {
    /** X(t)−X(0)=Σr νr Nr(t), checked exactly in the integer chain group. */
    fun stateEquation(
        network: ReactionNetwork,
        path: SimulatedReactionPath,
    ): Evidence<ReactionCountIdentity> {
        val counts = MutableList(network.reactions.size) { 0 }
        path.jumps.forEach { jump ->
            require(jump.reactionIndex in network.reactions.indices)
            require(network.reactions[jump.reactionIndex].apply(jump.source) == jump.target)
            counts[jump.reactionIndex] += 1
        }
        val observed = path.initialState.counts.indices.map { species ->
            path.finalState[species] - path.initialState[species]
        }
        val clockSum = path.initialState.counts.indices.map { species ->
            network.reactions.indices.sumOf { reaction ->
                network.reactions[reaction].stoichiometricChange()[species] * counts[reaction]
            }
        }
        require(observed == clockSum)
        return Evidence(
            value = ReactionCountIdentity(counts, observed, clockSum),
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "The sampled càdlàg path satisfies X(t)−X(0)=ΣrνrNr(t) exactly in integers.",
        )
    }

    /** Nr(t)−∫₀ᵗ ar(Xs)ds and its predictable bracket ∫₀ᵗ ar(Xs)ds. */
    fun compensatedCounts(
        network: ReactionNetwork,
        path: SimulatedReactionPath,
    ): CompensatedReactionCounts {
        val counts = MutableList(network.reactions.size) { 0 }
        path.jumps.forEach { counts[it.reactionIndex] += 1 }
        val hazards = network.reactions.indices.map { reaction ->
            path.intervals.sumOf { interval ->
                network.reactions[reaction].propensity(interval.state).toDouble() * interval.duration
            }
        }
        return CompensatedReactionCounts(
            reactionCounts = counts,
            integratedIntensities = hazards,
            martingaleTerminalValues = counts.indices.map { counts[it] - hazards[it] },
            predictableQuadraticVariations = hazards,
        )
    }
}
