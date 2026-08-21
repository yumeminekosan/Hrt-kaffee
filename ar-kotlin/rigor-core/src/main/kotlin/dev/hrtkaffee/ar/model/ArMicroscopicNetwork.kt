package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.Species
import dev.hrtkaffee.ar.rigor.thermo.ExactQuantumRateCalibration

data class ArMicroscopicSystem(
    val network: ReactionNetwork,
    val initialState: PopulationState,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
)

/** The same intervention controls used by the panel parameterize a finite chemical master equation. */
object ArMicroscopicNetwork {
    private const val T = 0
    private const val DHT = 1
    private const val AR = 2
    private const val T_AR = 3
    private const val DHT_AR = 4
    private const val ANTAGONIST = 5
    private const val ANTAGONIST_AR = 6
    private const val SPECIES_COUNT = 7

    fun create(
        intervention: ArIntervention,
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): ArMicroscopicSystem {
        val residualFiveAlphaActivity =
            Rational.ONE - intervention.fiveAlphaReductaseInhibition.asFraction()
        val antagonistMolecules =
            (intervention.directArCompetition.value + 2_499) / 2_500

        val species = listOf(
            Species("T", "testosterone"),
            Species("DHT", "dihydrotestosterone"),
            Species("AR", "free androgen receptor"),
            Species("T_AR", "testosterone-bound AR"),
            Species("DHT_AR", "DHT-bound AR"),
            Species("A", "direct AR competitor"),
            Species("A_AR", "competitor-bound AR"),
        )

        val uncalibratedReactions = listOf(
            reaction("t_bind", "T + AR → T·AR", terms(T to 1, AR to 1), terms(T_AR to 1), Rational.of(1, 4), "t_unbind"),
            reaction("t_unbind", "T·AR → T + AR", terms(T_AR to 1), terms(T to 1, AR to 1), Rational.of(1, 2), "t_bind"),
            reaction("dht_bind", "DHT + AR → DHT·AR", terms(DHT to 1, AR to 1), terms(DHT_AR to 1), Rational.ONE, "dht_unbind"),
            reaction("dht_unbind", "DHT·AR → DHT + AR", terms(DHT_AR to 1), terms(DHT to 1, AR to 1), Rational.of(1, 2), "dht_bind"),
            reaction("a_bind", "A + AR → A·AR", terms(ANTAGONIST to 1, AR to 1), terms(ANTAGONIST_AR to 1), Rational.ONE, "a_unbind"),
            reaction("a_unbind", "A·AR → A + AR", terms(ANTAGONIST_AR to 1), terms(ANTAGONIST to 1, AR to 1), Rational.ONE, "a_bind"),
            reaction(
                "five_ar_forward",
                "T → DHT through residual 5αR activity",
                terms(T to 1),
                terms(DHT to 1),
                Rational.of(1, 2) * residualFiveAlphaActivity,
                "five_ar_reverse",
            ),
            reaction("five_ar_reverse", "DHT → T reference return", terms(DHT to 1), terms(T to 1), Rational.ONE, "five_ar_forward"),
        )
        val reactions = quantumRateCalibration?.applyTo(uncalibratedReactions) ?: uncalibratedReactions

        return ArMicroscopicSystem(
            network = ReactionNetwork(species, reactions),
            initialState = PopulationState(listOf(4, 2, 3, 0, 0, antagonistMolecules, 0)),
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
