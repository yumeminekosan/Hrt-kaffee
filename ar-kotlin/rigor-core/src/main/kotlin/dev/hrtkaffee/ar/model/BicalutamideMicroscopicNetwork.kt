package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.DensityScaledReactionFamily
import dev.hrtkaffee.ar.rigor.limit.ReactionNetworkLimit
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.Species
import dev.hrtkaffee.ar.rigor.thermo.FormalFreeEnergy
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalance
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalanceReport
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex

data class BicalutamideMicroscopicSystem(
    val network: ReactionNetwork,
    val initialState: PopulationState,
)

data class BicalutamideStructuralAnchor(
    val compound: String,
    val formula: String,
    val molecularWeightGramsPerMole: Rational,
    val structurePdbId: String,
    val resolutionAngstrom: Rational,
    val receptorVariant: String,
    val directWildTypeComplexObserved: Boolean,
    val assignment: String,
) {
    companion object {
        fun rBicalutamide(): BicalutamideStructuralAnchor = BicalutamideStructuralAnchor(
            compound = "R-bicalutamide",
            formula = "C18H14F4N2O4S",
            molecularWeightGramsPerMole = Rational.of(43_037, 100),
            structurePdbId = "1Z95",
            resolutionAngstrom = Rational.of(9, 5),
            receptorVariant = "human AR LBD W741L",
            directWildTypeComplexObserved = false,
            assignment = "mutation-supported resistance/agonism structure; never represented as a wild-type antagonist-bound structure",
        )
    }
}

/**
 * One conservative finite reaction table for PK transfer and T/DHT/R-bicalutamide
 * competition at AR. Dose administration remains an external scheduled jump.
 */
object BicalutamideMicroscopicNetwork {
    private const val BIC_GUT = 0
    private const val BIC_FREE = 1
    private const val BIC_OUT = 2
    private const val T = 3
    private const val DHT = 4
    private const val AR = 5
    private const val T_AR = 6
    private const val DHT_AR = 7
    private const val BIC_AR = 8
    private const val N = 9

    fun create(): BicalutamideMicroscopicSystem {
        val species = listOf(
            Species("R_BIC_GUT", "active R-bicalutamide absorption compartment"),
            Species("R_BIC", "free active R-bicalutamide"),
            Species("R_BIC_OUT", "eliminated R-bicalutamide reservoir"),
            Species("T", "free testosterone"),
            Species("DHT", "free dihydrotestosterone"),
            Species("AR", "unoccupied androgen receptor"),
            Species("T_AR", "testosterone-bound androgen receptor"),
            Species("DHT_AR", "DHT-bound androgen receptor"),
            Species("R_BIC_AR", "R-bicalutamide-bound androgen receptor"),
        )
        val reactions = listOf(
            pair("absorb", BIC_GUT, BIC_FREE, 101, 1_000, "redistribute", 1, 10_000),
            pair("eliminate", BIC_FREE, BIC_OUT, 5, 1_000, "reservoir_return", 1, 100_000),
            binding("t_bind", T, AR, T_AR, 1, 100, "t_release", 1, 500),
            binding("dht_bind", DHT, AR, DHT_AR, 1, 100, "dht_release", 9, 50_000),
            binding("bic_bind", BIC_FREE, AR, BIC_AR, 1, 100, "bic_release", 11, 100),
        ).flatten()
        return BicalutamideMicroscopicSystem(
            network = ReactionNetwork(species, reactions),
            initialState = PopulationState(listOf(2, 0, 0, 2, 2, 3, 0, 0, 0)),
        )
    }

    private fun pair(
        forwardId: String,
        from: Int,
        to: Int,
        forwardNumerator: Long,
        forwardDenominator: Long,
        reverseId: String,
        reverseNumerator: Long,
        reverseDenominator: Long,
    ): List<Reaction> = listOf(
        reaction(
            forwardId,
            terms(from to 1),
            terms(to to 1),
            Rational.of(forwardNumerator, forwardDenominator),
            reverseId,
        ),
        reaction(
            reverseId,
            terms(to to 1),
            terms(from to 1),
            Rational.of(reverseNumerator, reverseDenominator),
            forwardId,
        ),
    )

    private fun binding(
        forwardId: String,
        ligand: Int,
        receptor: Int,
        complex: Int,
        forwardNumerator: Long,
        forwardDenominator: Long,
        reverseId: String,
        reverseNumerator: Long,
        reverseDenominator: Long,
    ): List<Reaction> = listOf(
        reaction(
            forwardId,
            terms(ligand to 1, receptor to 1),
            terms(complex to 1),
            Rational.of(forwardNumerator, forwardDenominator),
            reverseId,
        ),
        reaction(
            reverseId,
            terms(complex to 1),
            terms(ligand to 1, receptor to 1),
            Rational.of(reverseNumerator, reverseDenominator),
            forwardId,
        ),
    )

    private fun reaction(
        id: String,
        reactants: List<Int>,
        products: List<Int>,
        rate: Rational,
        reverseId: String,
    ): Reaction = Reaction(id, id, reactants, products, rate, reverseId)

    private fun terms(vararg entries: Pair<Int, Int>): List<Int> =
        MutableList(N) { 0 }.apply {
            entries.forEach { (index, count) -> this[index] = count }
        }
}

data class BicalutamideRigorousArtifacts(
    val systemSize: Int,
    val baseReactionNetwork: ReactionNetwork,
    val microscopicNetwork: ReactionNetwork,
    val microscopicInitialState: PopulationState,
    val exactGenerator: ExactGenerator,
    val densityLimitSymbol: DensityDependentModel,
    val chainComplex: StoichiometricChainComplex,
    val structuralAnchor: BicalutamideStructuralAnchor,
) {
    fun auditLocalDetailedBalance(
        stateFreeEnergies: List<FormalFreeEnergy>,
        reservoirFactor: (source: Int, target: Int) -> Rational = { _, _ -> Rational.ONE },
    ): Evidence<LocalDetailedBalanceReport> = LocalDetailedBalance.audit(
        generator = exactGenerator,
        stateFreeEnergies = stateFreeEnergies,
        reservoirFactor = reservoirFactor,
    )
}

/** The same reaction table feeds Q, the density symbol and the chain complex. */
object BicalutamideRigorousPipeline {
    fun prepare(
        systemSize: Int = 1,
        maximumStates: Int = 20_000,
    ): BicalutamideRigorousArtifacts {
        require(systemSize > 0)
        val microscopic = BicalutamideMicroscopicNetwork.create()
        val scaledNetwork = DensityScaledReactionFamily.networkAtSize(
            microscopic.network,
            systemSize,
        )
        val scaledInitial = DensityScaledReactionFamily.scaleInitialState(
            microscopic.initialState,
            systemSize,
        )
        return BicalutamideRigorousArtifacts(
            systemSize = systemSize,
            baseReactionNetwork = microscopic.network,
            microscopicNetwork = scaledNetwork,
            microscopicInitialState = scaledInitial,
            exactGenerator = ExactGenerator.fromNetwork(
                scaledNetwork,
                scaledInitial,
                maximumStates,
            ),
            densityLimitSymbol = ReactionNetworkLimit.from(microscopic.network),
            chainComplex = StoichiometricChainComplex.from(microscopic.network),
            structuralAnchor = BicalutamideStructuralAnchor.rBicalutamide(),
        )
    }
}
