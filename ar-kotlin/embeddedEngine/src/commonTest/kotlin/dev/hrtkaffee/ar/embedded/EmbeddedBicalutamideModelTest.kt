package dev.hrtkaffee.ar.embedded

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun estradiolBridgeKeepsTheMissingAndrogenGateInTheBrowser() {
        val result = EmbeddedBicalutamideEstradiolBridge.evaluate(
            EmbeddedBicalutamideEstradiolBridgeInput(estradiolAveragePgMl = 100.0),
        )
        assertFalse(result.isDoseIdentifiable)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.gateMessage.contains("不能识别"))
    }

    @Test
    fun browserBridgeFindsZeroWhenResidualAndrogenAlreadyMeetsTheTarget() {
        val result = EmbeddedBicalutamideEstradiolBridge.evaluate(
            EmbeddedBicalutamideEstradiolBridgeInput(
                estradiolAveragePgMl = 100.0,
                androgenEvidence =
                    EmbeddedBicalutamideAndrogenEvidence.LAB_ANCHORED_TISSUE_EQUIVALENTS,
                currentFreeTissueTestosteroneNm = 0.02,
                currentFreeTissueDhtNm = 0.0018,
                days = 84,
            ),
        )
        assertEquals(0.0, result.centralMinimumEquivalentDoseMg)
        assertEquals(0.0, result.conservativeMinimumEquivalentDoseMg)
    }

    @Test
    fun estradiolExposureDoesNotBypassTheResidualAndrogenGate() {
        val input = EmbeddedBicalutamideEstradiolBridgeInput(
            estradiolAveragePgMl = 50.0,
            androgenEvidence =
                EmbeddedBicalutamideAndrogenEvidence.LAB_ANCHORED_TISSUE_EQUIVALENTS,
            days = 84,
        )
        val lower = EmbeddedBicalutamideEstradiolBridge.evaluate(input)
        val higher = EmbeddedBicalutamideEstradiolBridge.evaluate(
            input.copy(estradiolAveragePgMl = 300.0),
        )
        assertEquals(lower.centralMinimumEquivalentDoseMg, higher.centralMinimumEquivalentDoseMg)
        assertEquals(
            lower.conservativeMinimumEquivalentDoseMg,
            higher.conservativeMinimumEquivalentDoseMg,
        )
        assertEquals(lower.candidates, higher.candidates)
    }
}
