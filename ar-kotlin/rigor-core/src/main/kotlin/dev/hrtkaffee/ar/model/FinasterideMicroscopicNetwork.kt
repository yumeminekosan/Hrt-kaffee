package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.DensityScaledReactionFamily
import dev.hrtkaffee.ar.rigor.limit.ReactionNetworkLimit
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.Species
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex
import dev.hrtkaffee.ar.rigor.thermo.ExactQuantumRateCalibration

data class FinasterideMicroscopicSystem(
    val network: ReactionNetwork,
    val initialState: PopulationState,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
)

data class FinasterideStructuralAnchor(
    val target: String,
    val pdbId: String,
    val resolutionAngstrom: Rational,
    val inhibitedState: String,
    val catalyticResidues: List<String>,
) {
    companion object {
        fun pdb7Bw1(): FinasterideStructuralAnchor = FinasterideStructuralAnchor(
            target = "human SRD5A2",
            pdbId = "7BW1",
            resolutionAngstrom = Rational.of(14, 5),
            inhibitedState = "enzyme-bound NADP-dihydrofinasteride adduct",
            catalyticResidues = listOf("E57", "Y91"),
        )
    }
}

/**
 * Finite molecule-count realization of the same absorption, saturable enzyme
 * binding and T→DHT channels used by the population PK/PD projection.
 *
 * E2_FIN denotes the coarse-grained inhibited SRD5A2 state structurally
 * assigned to the enzyme-bound NADP-dihydrofinasteride adduct (PDB 7BW1).
 * It is not an androgen-receptor complex.
 */
object FinasterideMicroscopicNetwork {
    private const val GUT_FIN = 0
    private const val FREE_FIN = 1
    private const val ELIM_FIN = 2
    private const val E2 = 3
    private const val E2_FIN = 4
    private const val E1 = 5
    private const val E1_FIN = 6
    private const val T = 7
    private const val DHT = 8
    private const val SPECIES_COUNT = 9

    fun create(
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): FinasterideMicroscopicSystem {
        val species = listOf(
            Species("FIN_GUT", "finasteride in absorption compartment"),
            Species("FIN", "free finasteride"),
            Species("FIN_OUT", "eliminated finasteride reservoir count"),
            Species("SRD5A2", "free steroid 5-alpha-reductase type 2"),
            Species("SRD5A2_FIN", "SRD5A2-bound NADP-dihydrofinasteride inhibited state"),
            Species("SRD5A1", "free steroid 5-alpha-reductase type 1"),
            Species("SRD5A1_FIN", "finasteride-bound SRD5A1 state"),
            Species("T", "testosterone"),
            Species("DHT", "dihydrotestosterone"),
        )
        val uncalibratedReactions = listOf(
            reaction("absorb", "FIN_GUT → FIN", terms(GUT_FIN to 1), terms(FREE_FIN to 1), Rational.of(187, 100), "redistribute"),
            reaction("redistribute", "FIN → FIN_GUT reference return", terms(FREE_FIN to 1), terms(GUT_FIN to 1), Rational.of(1, 100), "absorb"),
            reaction("eliminate", "FIN → FIN_OUT", terms(FREE_FIN to 1), terms(ELIM_FIN to 1), Rational.of(177, 1_000), "reservoir_return"),
            reaction("reservoir_return", "FIN_OUT → FIN reference return", terms(ELIM_FIN to 1), terms(FREE_FIN to 1), Rational.of(1, 1_000), "eliminate"),
            reaction("e2_bind", "FIN + SRD5A2 → SRD5A2·NADP-DHF", terms(FREE_FIN to 1, E2 to 1), terms(E2_FIN to 1), Rational.of(29_300, 1_000_000), "e2_release"),
            reaction("e2_release", "SRD5A2·NADP-DHF → FIN + SRD5A2", terms(E2_FIN to 1), terms(FREE_FIN to 1, E2 to 1), Rational.of(185, 10_000), "e2_bind"),
            reaction("e1_bind", "FIN + SRD5A1 → SRD5A1·FIN", terms(FREE_FIN to 1, E1 to 1), terms(E1_FIN to 1), Rational.of(1, 100), "e1_release"),
            reaction("e1_release", "SRD5A1·FIN → FIN + SRD5A1", terms(E1_FIN to 1), terms(FREE_FIN to 1, E1 to 1), Rational.of(11, 5), "e1_bind"),
            reaction("e2_reduce", "T + SRD5A2 → DHT + SRD5A2", terms(T to 1, E2 to 1), terms(DHT to 1, E2 to 1), Rational.of(3), "e2_reverse"),
            reaction("e2_reverse", "DHT + SRD5A2 → T + SRD5A2 reference", terms(DHT to 1, E2 to 1), terms(T to 1, E2 to 1), Rational.ONE, "e2_reduce"),
            reaction("e1_reduce", "T + SRD5A1 → DHT + SRD5A1", terms(T to 1, E1 to 1), terms(DHT to 1, E1 to 1), Rational.ONE, "e1_reverse"),
            reaction("e1_reverse", "DHT + SRD5A1 → T + SRD5A1 reference", terms(DHT to 1, E1 to 1), terms(T to 1, E1 to 1), Rational.ONE, "e1_reduce"),
        )
        val reactions = quantumRateCalibration?.applyTo(uncalibratedReactions) ?: uncalibratedReactions
        return FinasterideMicroscopicSystem(
            network = ReactionNetwork(species, reactions),
            initialState = PopulationState(listOf(2, 0, 0, 2, 0, 1, 0, 2, 0)),
            quantumRateCalibration = quantumRateCalibration,
        )
    }

    private fun reaction(
        id: String,
        label: String,
        reactants: List<Int>,
        products: List<Int>,
        rate: Rational,
        reverseId: String,
    ): Reaction = Reaction(id, label, reactants, products, rate, reverseId)

    private fun terms(vararg nonZero: Pair<Int, Int>): List<Int> =
        MutableList(SPECIES_COUNT) { 0 }.apply {
            nonZero.forEach { (index, count) -> this[index] = count }
        }
}

data class FinasterideRigorousArtifacts(
    val systemSize: Int,
    val baseReactionNetwork: ReactionNetwork,
    val microscopicNetwork: ReactionNetwork,
    val microscopicInitialState: PopulationState,
    val exactGenerator: ExactGenerator,
    val densityLimitSymbol: DensityDependentModel,
    val chainComplex: StoichiometricChainComplex,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
    val structuralAnchor: FinasterideStructuralAnchor,
)

/** One network instance feeds Q, the density symbol and the exact chain complex. */
object FinasterideRigorousPipeline {
    fun prepare(
        systemSize: Int = 1,
        maximumStates: Int = 20_000,
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): FinasterideRigorousArtifacts {
        require(systemSize > 0)
        val microscopic = FinasterideMicroscopicNetwork.create(quantumRateCalibration)
        val scaledNetwork = DensityScaledReactionFamily.networkAtSize(microscopic.network, systemSize)
        val scaledInitial = DensityScaledReactionFamily.scaleInitialState(microscopic.initialState, systemSize)
        return FinasterideRigorousArtifacts(
            systemSize = systemSize,
            baseReactionNetwork = microscopic.network,
            microscopicNetwork = scaledNetwork,
            microscopicInitialState = scaledInitial,
            exactGenerator = ExactGenerator.fromNetwork(scaledNetwork, scaledInitial, maximumStates),
            densityLimitSymbol = ReactionNetworkLimit.from(microscopic.network),
            chainComplex = StoichiometricChainComplex.from(microscopic.network),
            quantumRateCalibration = microscopic.quantumRateCalibration,
            structuralAnchor = FinasterideStructuralAnchor.pdb7Bw1(),
        )
    }
}
