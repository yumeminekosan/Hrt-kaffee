package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.DensityReaction
import dev.hrtkaffee.ar.rigor.metastability.ActionPathPoint
import dev.hrtkaffee.ar.rigor.metastability.FixedTimeMinimumActionSolver
import dev.hrtkaffee.ar.rigor.metastability.PathAction
import dev.hrtkaffee.ar.rigor.metastability.SamplePathLargeDeviationConditions
import dev.hrtkaffee.ar.rigor.metastability.metastableExitClaim
import dev.hrtkaffee.ar.rigor.metastability.quasipotentialClaim
import dev.hrtkaffee.ar.rigor.metastability.samplePathLargeDeviationClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LargeDeviationActionTest {
    @Test
    fun samplePathLdpAndQuasipotentialRemainConditionalTheorems() {
        assertFailsWith<IllegalArgumentException> {
            samplePathLargeDeviationClaim(
                SamplePathLargeDeviationConditions(
                    densityScalingEstablished = true,
                    exponentialTightnessEstablished = false,
                    goodRateFunctionEstablished = true,
                    witnesses = witnesses(),
                ),
            )
        }

        val ldp = samplePathLargeDeviationClaim(
            SamplePathLargeDeviationConditions(
                densityScalingEstablished = true,
                exponentialTightnessEstablished = true,
                goodRateFunctionEstablished = true,
                witnesses = witnesses(),
            ),
        )
        val quasipotential = quasipotentialClaim(ldp, true, "compact sublevel sets checked")
        val exit = metastableExitClaim(quasipotential, true, "mixing/exit ratio tends to zero")
        assertEquals(EvidenceKind.THEOREM_UNDER_ASSUMPTIONS, exit.kind)
    }

    @Test
    fun typicalFluidPathHasZeroActionAndIsAStationaryDiscreteCandidate() {
        val model = biasedWalk()
        val action = PathAction.evaluate(
            model,
            listOf(
                ActionPathPoint(0.0, doubleArrayOf(1.0)),
                ActionPathPoint(1.0, doubleArrayOf(2.0)),
            ),
        )
        assertEquals(0.0, action.value.value, 1e-12)
        assertTrue(action.diagnostics.all { it.passed })

        val candidate = FixedTimeMinimumActionSolver.solve(
            model = model,
            start = doubleArrayOf(1.0),
            end = doubleArrayOf(2.0),
            duration = 1.0,
            segments = 4,
            stationarityTolerance = 1e-5,
        )
        assertTrue(candidate.value.converged)
        assertEquals(0.0, candidate.value.action, 1e-10)
        assertTrue(candidate.diagnostics.all { it.passed })
    }

    private fun biasedWalk(): DensityDependentModel = DensityDependentModel(
        dimension = 1,
        reactions = listOf(
            DensityReaction(intArrayOf(1), "forward") { 2.0 },
            DensityReaction(intArrayOf(-1), "backward") { 1.0 },
        ),
    )

    private fun witnesses(): Map<String, String> = mapOf(
        AssumptionIds.DENSITY_DEPENDENT to "exact scaled reaction family",
        AssumptionIds.EXPONENTIAL_TIGHTNESS to "exponential compact containment estimate",
        AssumptionIds.GOOD_RATE_FUNCTION to "lower semicontinuity and compact sublevels",
    )
}
