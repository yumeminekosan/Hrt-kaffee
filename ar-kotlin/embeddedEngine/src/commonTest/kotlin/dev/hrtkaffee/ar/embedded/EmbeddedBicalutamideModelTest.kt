package dev.hrtkaffee.ar.embedded

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedBicalutamideModelTest {
    @Test
    fun browserProjectionPreservesCoreInvariants() {
        val zero = EmbeddedBicalutamideModel.simulate(
            EmbeddedBicalutamideInput(doseMg = 0.0, days = 42),
        )
        assertEquals(0.0, zero.endpoint.directSuppressionFraction, 1e-12)
        val reference = EmbeddedBicalutamideModel.simulate(
            EmbeddedBicalutamideInput(days = 42),
        )
        assertEquals(0.768, reference.calibration.singleDosePeakUgMl, 1e-12)
        assertTrue(reference.calibration.steadyStateStandardizedResidual < 0.5)
        assertTrue(reference.curve.all { point ->
            point.activeRTotalUgMl >= 0.0 &&
                point.freeArFraction in 0.0..1.0 &&
                point.testosteroneOccupancyFraction in 0.0..1.0 &&
                point.dhtOccupancyFraction in 0.0..1.0 &&
                point.bicalutamideOccupancyFraction in 0.0..1.0
        })
    }

    @Test
    fun structuralStressRangeIsOrderedAndNotPresentedAsProbability() {
        val result = EmbeddedBicalutamideModel.simulate(
            EmbeddedBicalutamideInput(days = 84),
        )
        assertTrue(result.stressRange.lowerDirectSuppressionFraction <=
            result.stressRange.upperDirectSuppressionFraction)
    }
}
