package dev.hrtkaffee.ar.rigor.hydrodynamic

import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.limit.DensityScaledReactionFamily
import dev.hrtkaffee.ar.rigor.limit.ReactionNetworkLimit
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.Species

data class SpatialLatticeSystem(
    val network: ReactionNetwork,
    val siteCount: Int,
    val localSpeciesCount: Int,
    val particlesPerSiteScale: Int,
)

/**
 * Periodic reaction–diffusion interacting particle system. Each site receives the same
 * density-scaled local reaction table and each particle hops to both neighbours at rate D L².
 */
object PeriodicSpatialLattice {
    fun lift(
        localNetwork: ReactionNetwork,
        siteCount: Int,
        particlesPerSiteScale: Int,
        diffusion: List<Rational>,
    ): SpatialLatticeSystem {
        require(siteCount >= 3)
        require(particlesPerSiteScale > 0)
        require(diffusion.size == localNetwork.species.size)
        require(diffusion.all { it >= Rational.ZERO })

        val localScaled = DensityScaledReactionFamily.networkAtSize(
            localNetwork,
            particlesPerSiteScale,
        )
        val expandedSpecies = buildList {
            for (site in 0 until siteCount) {
                localNetwork.species.forEach { species ->
                    add(Species("${species.id}@$site", "${species.label} at lattice site $site"))
                }
            }
        }
        val reactions = mutableListOf<Reaction>()
        for (site in 0 until siteCount) {
            localScaled.reactions.forEach { reaction ->
                reactions += Reaction(
                    id = "site_${site}_${reaction.id}",
                    label = "site $site: ${reaction.label}",
                    reactants = embed(reaction.reactants, site, siteCount),
                    products = embed(reaction.products, site, siteCount),
                    rate = reaction.rate,
                    reverseReactionId = reaction.reverseReactionId?.let { "site_${site}_$it" },
                )
            }
        }

        val diffusiveScale = Rational.of(siteCount).pow(2)
        for (site in 0 until siteCount) {
            for (species in localNetwork.species.indices) {
                listOf(-1 to "minus", 1 to "plus").forEach { (direction, name) ->
                    val targetSite = (site + direction + siteCount) % siteCount
                    val reverseName = if (name == "plus") "minus" else "plus"
                    reactions += Reaction(
                        id = "hop_${species}_${site}_$name",
                        label = "${localNetwork.species[species].label}: $site → $targetSite",
                        reactants = siteSpeciesTerm(
                            siteCount = siteCount,
                            localSpeciesCount = localNetwork.species.size,
                            site = site,
                            species = species,
                        ),
                        products = siteSpeciesTerm(
                            siteCount = siteCount,
                            localSpeciesCount = localNetwork.species.size,
                            site = targetSite,
                            species = species,
                        ),
                        rate = diffusion[species] * diffusiveScale,
                        reverseReactionId = "hop_${species}_${targetSite}_$reverseName",
                    )
                }
            }
        }
        return SpatialLatticeSystem(
            network = ReactionNetwork(expandedSpecies, reactions),
            siteCount = siteCount,
            localSpeciesCount = localNetwork.species.size,
            particlesPerSiteScale = particlesPerSiteScale,
        )
    }

    fun scaleInitialState(
        localStates: List<PopulationState>,
        particlesPerSiteScale: Int,
    ): PopulationState {
        require(localStates.size >= 3)
        require(particlesPerSiteScale > 0)
        val speciesCount = localStates.first().counts.size
        require(localStates.all { it.counts.size == speciesCount })
        return PopulationState(
            localStates.flatMap { state ->
                state.counts.map { Math.multiplyExact(it, particlesPerSiteScale) }
            },
        )
    }

    private fun embed(local: List<Int>, site: Int, siteCount: Int): List<Int> =
        MutableList(local.size * siteCount) { 0 }.apply {
            local.indices.forEach { species -> this[site * local.size + species] = local[species] }
        }

    private fun siteSpeciesTerm(
        siteCount: Int,
        localSpeciesCount: Int,
        site: Int,
        species: Int,
    ): List<Int> = MutableList(siteCount * localSpeciesCount) { 0 }.apply {
        this[site * localSpeciesCount + species] = 1
    }
}

/** Builds the PDE discretization's reaction drift from the same local reaction table. */
object ReactionDiffusionFiniteVolume {
    fun fromNetwork(
        localNetwork: ReactionNetwork,
        cellWidth: Double,
        diffusion: DoubleArray,
        boundaryCondition: BoundaryCondition,
        conservationWeights: DoubleArray,
    ): ConservativeFiniteVolume1D {
        val localSymbol = ReactionNetworkLimit.from(localNetwork)
        return ConservativeFiniteVolume1D(
            cellWidth = cellWidth,
            diffusion = diffusion,
            boundaryCondition = boundaryCondition,
            reaction = localSymbol::drift,
            conservationWeights = conservationWeights,
        )
    }
}
