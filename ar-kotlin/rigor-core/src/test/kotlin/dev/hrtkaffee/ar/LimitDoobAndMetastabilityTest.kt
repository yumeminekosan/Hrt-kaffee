package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.large_deviation.GeneralizedDoobTransform
import dev.hrtkaffee.ar.rigor.large_deviation.PrincipalEigenSolver
import dev.hrtkaffee.ar.rigor.large_deviation.TiltedOperator
import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.DensityReaction
import dev.hrtkaffee.ar.rigor.limit.FluidIntegrator
import dev.hrtkaffee.ar.rigor.limit.KurtzConditions
import dev.hrtkaffee.ar.rigor.limit.LegendreFenchelSolver
import dev.hrtkaffee.ar.rigor.limit.kurtzFluidLimitClaim
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.GillespieSimulator
import dev.hrtkaffee.ar.rigor.metastability.FirstPassageSolver
import dev.hrtkaffee.ar.rigor.metastability.QuasiStationarySolver
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LimitDoobAndMetastabilityTest {
    @Test
    fun hamiltonianAndLegendreFenchelMeetAtTypicalVelocity() {
        val model = oneDimensionalBiasedWalk()
        val density = doubleArrayOf(0.0)
        val zeroMomentum = doubleArrayOf(0.0)
        assertEquals(0.0, model.hamiltonian(density, zeroMomentum), 1e-15)
        assertEquals(1.0, model.hamiltonianGradient(density, zeroMomentum).single(), 1e-15)

        val dual = LegendreFenchelSolver.solve(model, density, doubleArrayOf(1.0))
        assertTrue(dual.value.converged)
        assertEquals(0.0, dual.value.value, 1e-12)
        assertTrue(dual.diagnostics.all { it.passed })
    }

    @Test
    fun fluidTrajectoryIsNumericalEvidenceNotTheKurtzProof() {
        val model = oneDimensionalBiasedWalk()
        val trajectory = FluidIntegrator.integrateWithStepDoubling(
            model = model,
            initial = doubleArrayOf(0.0),
            horizon = 1.0,
            coarseSteps = 8,
            tolerance = 1e-12,
        )
        assertEquals(EvidenceKind.NUMERICAL_CERTIFICATE, trajectory.kind)
        assertEquals(1.0, trajectory.value.points.last().density.single(), 1e-12)

        val checked = listOf(
            AssumptionIds.DENSITY_DEPENDENT,
            AssumptionIds.LOCALLY_LIPSCHITZ,
            AssumptionIds.COMPACT_CONTAINMENT,
            AssumptionIds.INITIAL_CONVERGENCE,
        ).associateWith { "analytical witness for test model" }
        val theorem = kurtzFluidLimitClaim(
            KurtzConditions(true, true, true, true, checked),
        )
        assertEquals(EvidenceKind.THEOREM_UNDER_ASSUMPTIONS, theorem.kind)
    }

    @Test
    fun tiltedOperatorRequiresDoobTransformBeforeSimulation() {
        val generator = threeStateRing()
        val increments = List(3) { source ->
            List(3) { target ->
                when {
                    target == (source + 1) % 3 -> 1.0
                    source == (target + 1) % 3 -> -1.0
                    else -> 0.0
                }
            }
        }
        val tilted = TiltedOperator.currentTilt(generator, increments, tilt = 0.2)
        val eigenpair = PrincipalEigenSolver.solve(tilted, generator.isIrreducible())
        assertTrue(eigenpair.diagnostics.all { it.passed })
        val driven = GeneralizedDoobTransform.build(tilted, eigenpair)
        assertTrue(driven.diagnostics.all { it.passed })
        repeat(driven.value.stateCount) { row ->
            assertEquals(0.0, driven.value.row(row).sum(), 1e-10)
        }
        val path = GillespieSimulator(Random(20260814)).simulate(
            generator = driven.value,
            initialState = 0,
            horizon = 1.0,
        )
        assertEquals(1.0, path.horizon)
    }

    @Test
    fun firstPassageAndKilledGeneratorTimescalesAgreeForOneStateBasin() {
        val generator = RationalAndGeneratorTest.twoStateGenerator()
        val hitting = FirstPassageSolver.meanHittingTimes(generator, setOf(1)).value
        assertEquals(Rational.of(1, 2), hitting[0])
        val quasiStationary = QuasiStationarySolver.solve(generator, setOf(0))
        assertEquals(2.0, quasiStationary.value.escapeRate, 1e-10)
        assertEquals(0.5, quasiStationary.value.meanLifetime, 1e-10)
        assertTrue(quasiStationary.diagnostics.all { it.passed })
    }

    private fun oneDimensionalBiasedWalk(): DensityDependentModel = DensityDependentModel(
        dimension = 1,
        reactions = listOf(
            DensityReaction(intArrayOf(1), "birth") { 2.0 },
            DensityReaction(intArrayOf(-1), "death") { 1.0 },
        ),
    )

    private fun threeStateRing(): ExactGenerator = ExactGenerator.fromRateMatrix(
        listOf(
            listOf(Rational.of(-3), Rational.of(2), Rational.ONE),
            listOf(Rational.ONE, Rational.of(-3), Rational.of(2)),
            listOf(Rational.of(2), Rational.ONE, Rational.of(-3)),
        ),
    )
}
