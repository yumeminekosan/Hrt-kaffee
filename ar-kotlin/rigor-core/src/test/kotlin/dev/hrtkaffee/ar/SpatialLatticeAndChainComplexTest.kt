package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.hydrodynamic.BoundaryCondition
import dev.hrtkaffee.ar.rigor.hydrodynamic.PeriodicSpatialLattice
import dev.hrtkaffee.ar.rigor.hydrodynamic.ReactionDiffusionFiniteVolume
import dev.hrtkaffee.ar.rigor.hydrodynamic.SpatialDensity
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.Reaction
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.Species
import dev.hrtkaffee.ar.rigor.topology.Simplex
import dev.hrtkaffee.ar.rigor.topology.SimplicialChain
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialLatticeAndChainComplexTest {
    @Test
    fun periodicLatticeLiftCreatesAnExactDiffusivelyScaledParticleGenerator() {
        val local = reversibleConversion()
        val lattice = PeriodicSpatialLattice.lift(
            localNetwork = local,
            siteCount = 3,
            particlesPerSiteScale = 1,
            diffusion = listOf(Rational.ONE, Rational.of(2)),
        )
        assertEquals(18, lattice.network.reactions.size)
        assertEquals(
            Rational.of(9),
            lattice.network.reactions.single { it.id == "hop_0_0_plus" }.rate,
        )
        val initial = PeriodicSpatialLattice.scaleInitialState(
            localStates = List(3) { PopulationState(listOf(1, 0)) },
            particlesPerSiteScale = 1,
        )
        val generator = ExactGenerator.fromNetwork(lattice.network, initial)
        assertTrue(generator.isIrreducible())
        repeat(generator.size) { row ->
            assertEquals(Rational.ZERO, generator.matrix.row(row).fold(Rational.ZERO, Rational::plus))
        }
    }

    @Test
    fun pdeReactionTermComesFromTheSameLocalReactionTable() {
        val solver = ReactionDiffusionFiniteVolume.fromNetwork(
            localNetwork = reversibleConversion(),
            cellWidth = 1.0,
            diffusion = doubleArrayOf(1.0, 0.5),
            boundaryCondition = BoundaryCondition.PERIODIC,
            conservationWeights = doubleArrayOf(1.0, 1.0),
        )
        val step = solver.step(
            current = SpatialDensity(
                arrayOf(
                    doubleArrayOf(1.0, 0.0, 0.0),
                    doubleArrayOf(0.0, 1.0, 0.0),
                ),
            ),
            timeStep = 0.01,
        )
        assertTrue(step.diagnostics.all { it.passed })
    }

    @Test
    fun simplicialBoundarySquaresToZeroAndStoichiometryFindsExactKernels() {
        val triangle = Simplex(listOf("A", "B", "C"))
        assertEquals(listOf("A", "C"), triangle.face(1).vertices)
        assertTrue(SimplicialChain.of(triangle).boundary().boundary().isZero())

        val report = StoichiometricChainComplex.from(reversibleConversion()).audit().value
        assertEquals(listOf(Rational.ONE, Rational.ONE), report.conservationLawBasis.single())
        assertEquals(listOf(Rational.ONE, Rational.ONE), report.reactionCycleBasis.single())
    }

    private fun reversibleConversion(): ReactionNetwork = ReactionNetwork(
        species = listOf(Species("A", "A"), Species("B", "B")),
        reactions = listOf(
            Reaction("forward", "A → B", listOf(1, 0), listOf(0, 1), Rational.ONE, "reverse"),
            Reaction("reverse", "B → A", listOf(0, 1), listOf(1, 0), Rational.ONE, "forward"),
        ),
    )
}
