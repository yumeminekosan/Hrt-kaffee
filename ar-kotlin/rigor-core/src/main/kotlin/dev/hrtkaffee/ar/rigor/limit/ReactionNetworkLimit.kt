package dev.hrtkaffee.ar.rigor.limit

import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.exact.Rational
import kotlin.math.pow

/**
 * Leading density-dependent mass-action symbol from the same microscopic reactions.
 * The caller must separately establish the N-scaling, compact containment, and initial convergence.
 */
object ReactionNetworkLimit {
    fun from(network: ReactionNetwork): DensityDependentModel = DensityDependentModel(
        dimension = network.species.size,
        reactions = network.reactions.map { reaction ->
            DensityReaction(
                stoichiometry = reaction.stoichiometricChange().toIntArray(),
                label = reaction.label,
                beta = { density ->
                    reaction.reactants.indices.fold(reaction.rate.toDouble()) { intensity, species ->
                        intensity * density[species].pow(reaction.reactants[species])
                    }
                },
            )
        },
    )
}

/**
 * Exact density-dependent family qᶰ_r(x)=k_r N^(1-m_r)∏s(x_s)_(a_rs).
 * This prevents binary reactions from accidentally becoming O(N²).
 */
object DensityScaledReactionFamily {
    fun networkAtSize(base: ReactionNetwork, systemSize: Int): ReactionNetwork {
        require(systemSize > 0)
        val size = Rational.of(systemSize)
        return ReactionNetwork(
            species = base.species,
            reactions = base.reactions.map { reaction ->
                val molecularity = reaction.reactants.sum()
                val scale = when {
                    molecularity == 0 -> size
                    molecularity == 1 -> Rational.ONE
                    else -> Rational.ONE / size.pow(molecularity - 1)
                }
                reaction.copy(rate = reaction.rate * scale)
            },
        )
    }

    fun scaleInitialState(base: PopulationState, systemSize: Int): PopulationState {
        require(systemSize > 0)
        return PopulationState(base.counts.map { count -> Math.multiplyExact(count, systemSize) })
    }
}
