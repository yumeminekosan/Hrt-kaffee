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

data class ProgestogenGnRHMicroscopicSystem(
    val network: ReactionNetwork,
    val initialState: PopulationState,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
)

data class ProgesteroneStructuralAnchor(
    val target: String,
    val formula: String,
    val molecularWeightGramsPerMole: Rational,
    val pdbId: String,
    val resolutionAngstrom: Rational,
    val directProgesteroneComplexObserved: Boolean,
    val directGnRHReceptorBindingAsserted: Boolean,
    val assignment: String,
) {
    companion object {
        fun humanProgesteroneReceptor(): ProgesteroneStructuralAnchor =
            ProgesteroneStructuralAnchor(
                target = "human progesterone receptor ligand-binding domain",
                formula = "C21H30O2",
                molecularWeightGramsPerMole = Rational.of(31_447, 100),
                pdbId = "1A28",
                resolutionAngstrom = Rational.of(9, 5),
                directProgesteroneComplexObserved = true,
                directGnRHReceptorBindingAsserted = false,
                assignment = "progesterone occupies PR; PR signalling changes the GnRH pulse-generator state through neuroendocrine feedback rather than direct GnRH-receptor antagonism",
            )
    }
}

/**
 * Finite-count reaction table for oral ligand movement, ligand–PR binding and
 * a coarse-grained GnRH pulse-generator ready/inhibited state. Every channel
 * has a positive reverse channel so the same table supports local detailed
 * balance, tilted operators, exact Gillespie paths and the density limit.
 */
object ProgestogenGnRHMicroscopicNetwork {
    private const val GUT_PG = 0
    private const val FREE_PG = 1
    private const val OUT_PG = 2
    private const val PR = 3
    private const val PR_PG = 4
    private const val GNRH_READY = 5
    private const val GNRH_INHIBITED = 6
    private const val SPECIES_COUNT = 7

    fun create(
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): ProgestogenGnRHMicroscopicSystem {
        val species = listOf(
            Species("PG_GUT", "progestogen in absorption compartment"),
            Species("PG", "free progestogen ligand"),
            Species("PG_OUT", "eliminated progestogen reservoir count"),
            Species("PR", "free progesterone receptor"),
            Species("PR_PG", "ligand-bound progesterone receptor"),
            Species("GNRH_READY", "pulse-generator ready state"),
            Species("GNRH_INHIBITED", "PR-feedback inhibited pulse-generator state"),
        )
        val uncalibrated = listOf(
            reaction("absorb", "PG_GUT → PG", terms(GUT_PG to 1), terms(FREE_PG to 1), Rational.of(1, 2), "redistribute"),
            reaction("redistribute", "PG → PG_GUT reference return", terms(FREE_PG to 1), terms(GUT_PG to 1), Rational.of(1, 100), "absorb"),
            reaction("eliminate", "PG → PG_OUT", terms(FREE_PG to 1), terms(OUT_PG to 1), Rational.of(7, 10), "reservoir_return"),
            reaction("reservoir_return", "PG_OUT → PG reference return", terms(OUT_PG to 1), terms(FREE_PG to 1), Rational.of(1, 1_000), "eliminate"),
            reaction("pr_bind", "PG + PR → PR·PG", terms(FREE_PG to 1, PR to 1), terms(PR_PG to 1), Rational.of(1, 200), "pr_release"),
            reaction("pr_release", "PR·PG → PG + PR", terms(PR_PG to 1), terms(FREE_PG to 1, PR to 1), Rational.of(9, 200), "pr_bind"),
            reaction("feedback_inhibit", "GNRH_READY + PR·PG → GNRH_INHIBITED + PR·PG", terms(GNRH_READY to 1, PR_PG to 1), terms(GNRH_INHIBITED to 1, PR_PG to 1), Rational.of(2), "feedback_escape"),
            reaction("feedback_escape", "GNRH_INHIBITED + PR·PG → GNRH_READY + PR·PG reference", terms(GNRH_INHIBITED to 1, PR_PG to 1), terms(GNRH_READY to 1, PR_PG to 1), Rational.of(1, 10), "feedback_inhibit"),
            reaction("basal_recover", "GNRH_INHIBITED → GNRH_READY", terms(GNRH_INHIBITED to 1), terms(GNRH_READY to 1), Rational.ONE, "basal_pause"),
            reaction("basal_pause", "GNRH_READY → GNRH_INHIBITED reference", terms(GNRH_READY to 1), terms(GNRH_INHIBITED to 1), Rational.of(1, 100), "basal_recover"),
        )
        val reactions = quantumRateCalibration?.applyTo(uncalibrated) ?: uncalibrated
        return ProgestogenGnRHMicroscopicSystem(
            network = ReactionNetwork(species, reactions),
            initialState = PopulationState(listOf(2, 0, 0, 2, 0, 2, 0)),
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

data class ProgestogenGnRHRigorousArtifacts(
    val systemSize: Int,
    val baseReactionNetwork: ReactionNetwork,
    val microscopicNetwork: ReactionNetwork,
    val microscopicInitialState: PopulationState,
    val exactGenerator: ExactGenerator,
    val densityLimitSymbol: DensityDependentModel,
    val chainComplex: StoichiometricChainComplex,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
    val structuralAnchor: ProgesteroneStructuralAnchor,
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

/** One PR-feedback network feeds Q, the density symbol and the chain complex. */
object ProgestogenGnRHRigorousPipeline {
    fun prepare(
        systemSize: Int = 1,
        maximumStates: Int = 20_000,
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): ProgestogenGnRHRigorousArtifacts {
        require(systemSize > 0)
        val microscopic = ProgestogenGnRHMicroscopicNetwork.create(quantumRateCalibration)
        val scaledNetwork = DensityScaledReactionFamily.networkAtSize(microscopic.network, systemSize)
        val scaledInitial = DensityScaledReactionFamily.scaleInitialState(
            microscopic.initialState,
            systemSize,
        )
        return ProgestogenGnRHRigorousArtifacts(
            systemSize = systemSize,
            baseReactionNetwork = microscopic.network,
            microscopicNetwork = scaledNetwork,
            microscopicInitialState = scaledInitial,
            exactGenerator = ExactGenerator.fromNetwork(scaledNetwork, scaledInitial, maximumStates),
            densityLimitSymbol = ReactionNetworkLimit.from(microscopic.network),
            chainComplex = StoichiometricChainComplex.from(microscopic.network),
            quantumRateCalibration = microscopic.quantumRateCalibration,
            structuralAnchor = ProgesteroneStructuralAnchor.humanProgesteroneReceptor(),
        )
    }
}
