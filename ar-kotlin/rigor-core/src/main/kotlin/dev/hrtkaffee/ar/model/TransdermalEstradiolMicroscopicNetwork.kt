package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.DensityScaledReactionFamily
import dev.hrtkaffee.ar.rigor.limit.ReactionNetworkLimit
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.Species
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex

data class TransdermalEstradiolMicroscopicSystem(
    val network: ReactionNetwork,
    val initialState: PopulationState,
)

data class TransdermalEstradiolRigorousArtifacts(
    val network: ReactionNetwork,
    val initialState: PopulationState,
    val generator: ExactGenerator,
    val densityLimit: DensityDependentModel,
    val chainComplex: StoichiometricChainComplex,
)

/**
 * One finite reaction table shared by the exact CTMC generator, Kurtz symbol,
 * tilted/Doob path machinery and stoichiometric chain complex. Patch replacement
 * is an external scheduled jump; the internal transport/binding channels remain
 * reversible so local detailed balance is auditable.
 */
object TransdermalEstradiolMicroscopicNetwork {
    private const val PATCH = 0
    private const val SC = 1
    private const val EPIDERMIS = 2
    private const val DERMIS = 3
    private const val CENTRAL = 4
    private const val PERIPHERAL = 5
    private const val OUT = 6
    private const val SHBG = 7
    private const val SHBG_E2 = 8
    private const val ER = 9
    private const val ER_E2 = 10
    private const val N = 11

    fun create(): TransdermalEstradiolMicroscopicSystem {
        val species = listOf(
            Species("PATCH_E2", "estradiol in the matrix patch"),
            Species("SC_E2", "estradiol in stratum corneum"),
            Species("VE_E2", "estradiol in viable epidermis"),
            Species("DERMIS_E2", "estradiol in dermal depot"),
            Species("CENTRAL_E2", "free estradiol in central plasma"),
            Species("PERIPHERAL_E2", "estradiol in peripheral tissue"),
            Species("OUT_E2", "eliminated estradiol reservoir"),
            Species("SHBG", "unoccupied SHBG carrier site"),
            Species("SHBG_E2", "SHBG-bound estradiol"),
            Species("ER", "unoccupied estrogen receptor"),
            Species("ER_E2", "estradiol-bound estrogen receptor"),
        )
        val reactions = listOf(
            pair("patch_sc", PATCH, SC, 1, 2, "sc_patch", 1, 100),
            pair("sc_ve", SC, EPIDERMIS, 11, 20, "ve_sc", 1, 50),
            pair("ve_dermis", EPIDERMIS, DERMIS, 17, 20, "dermis_ve", 1, 50),
            pair("dermis_central", DERMIS, CENTRAL, 23, 20, "central_dermis", 1, 100),
            pair("central_peripheral", CENTRAL, PERIPHERAL, 11, 200, "peripheral_central", 7, 250),
            pair("central_out", CENTRAL, OUT, 21, 200, "out_central", 1, 1_000),
            binding("shbg_bind", CENTRAL, SHBG, SHBG_E2, 1, 50, "shbg_release", 1, 1),
            binding("er_bind", PERIPHERAL, ER, ER_E2, 1, 10, "er_release", 1, 100),
        ).flatten()
        return TransdermalEstradiolMicroscopicSystem(
            network = ReactionNetwork(species, reactions),
            initialState = PopulationState(listOf(2, 0, 0, 0, 0, 0, 0, 2, 0, 2, 0)),
        )
    }

    private fun pair(
        forwardId: String,
        from: Int,
        to: Int,
        numerator: Long,
        denominator: Long,
        reverseId: String,
        reverseNumerator: Long,
        reverseDenominator: Long,
    ): List<Reaction> = listOf(
        reaction(forwardId, terms(from to 1), terms(to to 1), Rational.of(numerator, denominator), reverseId),
        reaction(reverseId, terms(to to 1), terms(from to 1), Rational.of(reverseNumerator, reverseDenominator), forwardId),
    )

    private fun binding(
        forwardId: String,
        ligand: Int,
        freeSite: Int,
        complex: Int,
        numerator: Long,
        denominator: Long,
        reverseId: String,
        reverseNumerator: Long,
        reverseDenominator: Long,
    ): List<Reaction> = listOf(
        reaction(forwardId, terms(ligand to 1, freeSite to 1), terms(complex to 1), Rational.of(numerator, denominator), reverseId),
        reaction(reverseId, terms(complex to 1), terms(ligand to 1, freeSite to 1), Rational.of(reverseNumerator, reverseDenominator), forwardId),
    )

    private fun reaction(
        id: String,
        reactants: List<Int>,
        products: List<Int>,
        rate: Rational,
        reverseId: String,
    ): Reaction = Reaction(id, id, reactants, products, rate, reverseId)

    private fun terms(vararg entries: Pair<Int, Int>): List<Int> =
        MutableList(N) { 0 }.apply { entries.forEach { (index, count) -> this[index] = count } }
}

object TransdermalEstradiolRigorousPipeline {
    fun prepare(systemSize: Int = 1, maximumStates: Int = 30_000): TransdermalEstradiolRigorousArtifacts {
        require(systemSize > 0)
        val base = TransdermalEstradiolMicroscopicNetwork.create()
        val network = DensityScaledReactionFamily.networkAtSize(base.network, systemSize)
        val initial = DensityScaledReactionFamily.scaleInitialState(base.initialState, systemSize)
        return TransdermalEstradiolRigorousArtifacts(
            network = network,
            initialState = initial,
            generator = ExactGenerator.fromNetwork(network, initial, maximumStates),
            densityLimit = ReactionNetworkLimit.from(base.network),
            chainComplex = StoichiometricChainComplex.from(base.network),
        )
    }
}
