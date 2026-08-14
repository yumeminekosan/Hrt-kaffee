package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.limit.ExponentialNonlinearGenerator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.RandomTimeChange
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.ReactionNetworkGillespieSimulator
import dev.hrtkaffee.ar.rigor.markov.Species
import dev.hrtkaffee.ar.rigor.thermo.ExactActivity
import dev.hrtkaffee.ar.rigor.thermo.FormalFreeEnergy
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalance
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThermodynamicStructureAndClockTest {
    @Test
    fun activitiesAndLocalDetailedBalanceStayExact() {
        val activity = ExactActivity(
            concentration = Rational.of(2),
            activityCoefficient = Rational.of(3, 2),
        )
        assertEquals(Rational.of(3), activity.relativeActivity)
        assertEquals(Rational.of(3), activity.chemicalPotentialIncrement().argument)

        val generator = RationalAndGeneratorTest.twoStateGenerator()
        val freeEnergies = listOf(
            FormalFreeEnergy(Rational.of(3)),
            FormalFreeEnergy(Rational.of(2)),
        )
        val audit = LocalDetailedBalance.audit(generator, freeEnergies)
        assertTrue(audit.value.satisfied)
        assertEquals(Rational.of(3, 2), freeEnergies[0].changeTo(freeEnergies[1]).argument)
    }

    @Test
    fun directReactionSamplerRetainsTheRandomTimeChangeIdentity() {
        val network = reversibleConversion()
        val path = ReactionNetworkGillespieSimulator(Random(20260814)).simulate(
            network = network,
            initialState = PopulationState(listOf(3, 0)),
            horizon = 2.0,
        )
        val identity = RandomTimeChange.stateEquation(network, path)
        assertEquals(identity.value.observedStateChange, identity.value.stoichiometricClockSum)
        val compensated = RandomTimeChange.compensatedCounts(network, path)
        assertEquals(network.reactions.size, compensated.martingaleTerminalValues.size)
        assertTrue(compensated.predictableQuadraticVariations.all { it >= 0.0 })
    }

    @Test
    fun exponentialNonlinearGeneratorConvergesToJumpHamiltonianWithoutFourierTransform() {
        val network = ReactionNetwork(
            species = listOf(Species("A", "A"), Species("B", "B")),
            reactions = listOf(
                Reaction(
                    id = "dimerize",
                    label = "2A → B",
                    reactants = listOf(2, 0),
                    products = listOf(0, 1),
                    rate = Rational.ONE,
                    reverseReactionId = null,
                ),
            ),
        )
        val comparison = ExponentialNonlinearGenerator.compareLinearTest(
            baseNetwork = network,
            baseDensityState = PopulationState(listOf(2, 0)),
            systemSize = 100,
            momentum = doubleArrayOf(0.3, 0.1),
            tolerance = 0.01,
        )
        assertEquals(Rational.of(1, 50), comparison.value.exactScaledIntensityErrors.single())
        assertTrue(comparison.diagnostics.all { it.passed })
    }

    private fun reversibleConversion(): ReactionNetwork = ReactionNetwork(
        species = listOf(Species("A", "A"), Species("B", "B")),
        reactions = listOf(
            Reaction("forward", "A → B", listOf(1, 0), listOf(0, 1), Rational.of(2), "reverse"),
            Reaction("reverse", "B → A", listOf(0, 1), listOf(1, 0), Rational.ONE, "forward"),
        ),
    )
}
