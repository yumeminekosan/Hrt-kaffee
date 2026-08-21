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
import dev.hrtkaffee.ar.rigor.thermo.ExactQuantumRateCalibration
import dev.hrtkaffee.ar.rigor.thermo.FormalFreeEnergy
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalance
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalanceReport
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex

data class DutasterideMicroscopicSystem(
    val network: ReactionNetwork,
    val initialState: PopulationState,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
)

data class DutasterideStructuralAnchor(
    val targets: List<String>,
    val formula: String,
    val molecularWeightGramsPerMole: Rational,
    val structuralTemplatePdbId: String,
    val templateResolutionAngstrom: Rational,
    val directDutasterideComplexObserved: Boolean,
    val assignment: String,
    val catalyticResidues: List<String>,
) {
    companion object {
        fun dualFiveAlphaReductase(): DutasterideStructuralAnchor = DutasterideStructuralAnchor(
            targets = listOf("human SRD5A1", "human SRD5A2"),
            formula = "C27H30F6N2O2",
            molecularWeightGramsPerMole = Rational.of(52_853, 100),
            structuralTemplatePdbId = "7BW1",
            templateResolutionAngstrom = Rational.of(14, 5),
            directDutasterideComplexObserved = false,
            assignment = "4-azasteroid competitive dual-enzyme inhibited state; a related NADP adduct geometry is inferred from the experimentally observed finasteride core, not claimed as a direct dutasteride structure",
            catalyticResidues = listOf("E57", "Y91"),
        )
    }
}

/**
 * Finite-count dual SRD5A1/SRD5A2 reaction table. DUT-bound species are
 * inhibited enzyme states, never androgen-receptor complexes.
 */
object DutasterideMicroscopicNetwork {
    private const val GUT_DUT = 0
    private const val FREE_DUT = 1
    private const val ELIM_DUT = 2
    private const val E2 = 3
    private const val E2_DUT = 4
    private const val E1 = 5
    private const val E1_DUT = 6
    private const val T = 7
    private const val DHT = 8
    private const val SPECIES_COUNT = 9

    fun create(
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): DutasterideMicroscopicSystem {
        val species = listOf(
            Species("DUT_GUT", "dutasteride in absorption compartment"),
            Species("DUT", "free dutasteride"),
            Species("DUT_OUT", "eliminated dutasteride reservoir count"),
            Species("SRD5A2", "free steroid 5-alpha-reductase type 2"),
            Species("SRD5A2_DUT", "dutasteride-inhibited SRD5A2 state"),
            Species("SRD5A1", "free steroid 5-alpha-reductase type 1"),
            Species("SRD5A1_DUT", "dutasteride-inhibited SRD5A1 state"),
            Species("T", "testosterone"),
            Species("DHT", "dihydrotestosterone"),
        )
        val uncalibrated = listOf(
            reaction("absorb", "DUT_GUT → DUT", terms(GUT_DUT to 1), terms(FREE_DUT to 1), Rational.of(3, 2), "redistribute"),
            reaction("redistribute", "DUT → DUT_GUT reference return", terms(FREE_DUT to 1), terms(GUT_DUT to 1), Rational.of(1, 100), "absorb"),
            reaction("eliminate", "DUT → DUT_OUT", terms(FREE_DUT to 1), terms(ELIM_DUT to 1), Rational.of(33, 40_000), "reservoir_return"),
            reaction("reservoir_return", "DUT_OUT → DUT reference return", terms(ELIM_DUT to 1), terms(FREE_DUT to 1), Rational.of(1, 100_000), "eliminate"),
            reaction("e2_bind", "DUT + SRD5A2 → SRD5A2·DUT", terms(FREE_DUT to 1, E2 to 1), terms(E2_DUT to 1), Rational.of(1, 100), "e2_release"),
            reaction("e2_release", "SRD5A2·DUT → DUT + SRD5A2", terms(E2_DUT to 1), terms(FREE_DUT to 1, E2 to 1), Rational.of(18, 1_000), "e2_bind"),
            reaction("e1_bind", "DUT + SRD5A1 → SRD5A1·DUT", terms(FREE_DUT to 1, E1 to 1), terms(E1_DUT to 1), Rational.of(1, 100), "e1_release"),
            reaction("e1_release", "SRD5A1·DUT → DUT + SRD5A1", terms(E1_DUT to 1), terms(FREE_DUT to 1, E1 to 1), Rational.of(39, 1_000), "e1_bind"),
            reaction("e2_reduce", "T + SRD5A2 → DHT + SRD5A2", terms(T to 1, E2 to 1), terms(DHT to 1, E2 to 1), Rational.of(4), "e2_reverse"),
            reaction("e2_reverse", "DHT + SRD5A2 → T + SRD5A2 reference", terms(DHT to 1, E2 to 1), terms(T to 1, E2 to 1), Rational.ONE, "e2_reduce"),
            reaction("e1_reduce", "T + SRD5A1 → DHT + SRD5A1", terms(T to 1, E1 to 1), terms(DHT to 1, E1 to 1), Rational.ONE, "e1_reverse"),
            reaction("e1_reverse", "DHT + SRD5A1 → T + SRD5A1 reference", terms(DHT to 1, E1 to 1), terms(T to 1, E1 to 1), Rational.ONE, "e1_reduce"),
        )
        val reactions = quantumRateCalibration?.applyTo(uncalibrated) ?: uncalibrated
        return DutasterideMicroscopicSystem(
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

data class DutasterideRigorousArtifacts(
    val systemSize: Int,
    val baseReactionNetwork: ReactionNetwork,
    val microscopicNetwork: ReactionNetwork,
    val microscopicInitialState: PopulationState,
    val exactGenerator: ExactGenerator,
    val densityLimitSymbol: DensityDependentModel,
    val chainComplex: StoichiometricChainComplex,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
    val structuralAnchor: DutasterideStructuralAnchor,
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

/** One dual-enzyme reaction table feeds Q, the density symbol and chain complex. */
object DutasterideRigorousPipeline {
    fun prepare(
        systemSize: Int = 1,
        maximumStates: Int = 20_000,
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): DutasterideRigorousArtifacts {
        require(systemSize > 0)
        val microscopic = DutasterideMicroscopicNetwork.create(quantumRateCalibration)
        val scaledNetwork = DensityScaledReactionFamily.networkAtSize(microscopic.network, systemSize)
        val scaledInitial = DensityScaledReactionFamily.scaleInitialState(
            microscopic.initialState,
            systemSize,
        )
        return DutasterideRigorousArtifacts(
            systemSize = systemSize,
            baseReactionNetwork = microscopic.network,
            microscopicNetwork = scaledNetwork,
            microscopicInitialState = scaledInitial,
            exactGenerator = ExactGenerator.fromNetwork(scaledNetwork, scaledInitial, maximumStates),
            densityLimitSymbol = ReactionNetworkLimit.from(microscopic.network),
            chainComplex = StoichiometricChainComplex.from(microscopic.network),
            quantumRateCalibration = microscopic.quantumRateCalibration,
            structuralAnchor = DutasterideStructuralAnchor.dualFiveAlphaReductase(),
        )
    }
}
