package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.BicalutamideAndrogenEvidence
import dev.hrtkaffee.ar.model.BicalutamideEstradiolBridge
import dev.hrtkaffee.ar.model.BicalutamideEstradiolBridgeInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BicalutamideEstradiolBridgeTest {
    @Test
    fun estradiolExposureAloneCannotIdentifyAnIndividualDose() {
        val result = BicalutamideEstradiolBridge.evaluate(
            BicalutamideEstradiolBridgeInput(estradiolAveragePgMl = 100.0),
        )
        assertFalse(result.isDoseIdentifiable)
        assertNull(result.estradiolOnlyRelativeArSignal)
        assertNull(result.centralMinimumEquivalentDoseMg)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.gateMessage.contains("不能识别"))
    }

    @Test
    fun residualAndrogenThatAlreadyMeetsTheThresholdReturnsZeroMilligrams() {
        val result = BicalutamideEstradiolBridge.evaluate(
            labInput(
                currentFreeTissueTestosteroneNm = 0.02,
                currentFreeTissueDhtNm = 0.0018,
            ),
        )
        assertTrue(result.isDoseIdentifiable)
        assertEquals(0.0, result.centralMinimumEquivalentDoseMg)
        assertEquals(0.0, result.conservativeMinimumEquivalentDoseMg)
        assertTrue(result.estradiolOnlyRelativeArSignal!! < 0.5)
    }

    @Test
    fun lessThanTheReferenceDoseCanPassButConservativeDoseIsNeverLower() {
        val result = BicalutamideEstradiolBridge.evaluate(labInput())
        val central = assertNotNull(result.centralMinimumEquivalentDoseMg)
        val conservative = assertNotNull(result.conservativeMinimumEquivalentDoseMg)
        assertTrue(central > 0.0)
        assertTrue(central < 50.0)
        assertTrue(conservative >= central)
        assertTrue(conservative <= 50.0)
    }

    @Test
    fun doseCandidatesAreMonotoneAcrossTheDisplayedGrid() {
        val result = BicalutamideEstradiolBridge.evaluate(labInput())
        val central = result.candidates.map { it.centralWorstRelativeArSignal }
        val conservative = result.candidates.map { it.conservativeWorstRelativeArSignal }
        assertEquals(central.sortedDescending(), central)
        assertEquals(conservative.sortedDescending(), conservative)
        assertTrue(result.candidates.all {
            it.conservativeWorstRelativeArSignal + 1e-12 >= it.centralWorstRelativeArSignal
        })
    }

    @Test
    fun estradiolAverageIsContextOnlyAndCannotSecretlyChangeTheThreshold() {
        val lowerExposure = BicalutamideEstradiolBridge.evaluate(
            labInput().copy(estradiolAveragePgMl = 50.0),
        )
        val higherExposure = BicalutamideEstradiolBridge.evaluate(
            labInput().copy(estradiolAveragePgMl = 300.0),
        )
        assertEquals(
            lowerExposure.centralMinimumEquivalentDoseMg,
            higherExposure.centralMinimumEquivalentDoseMg,
        )
        assertEquals(
            lowerExposure.conservativeMinimumEquivalentDoseMg,
            higherExposure.conservativeMinimumEquivalentDoseMg,
        )
        assertEquals(lowerExposure.candidates, higherExposure.candidates)
    }

    private fun labInput(
        currentFreeTissueTestosteroneNm: Double = 0.2,
        currentFreeTissueDhtNm: Double = 0.018,
    ): BicalutamideEstradiolBridgeInput = BicalutamideEstradiolBridgeInput(
        estradiolAveragePgMl = 100.0,
        androgenEvidence = BicalutamideAndrogenEvidence.LAB_ANCHORED_TISSUE_EQUIVALENTS,
        currentFreeTissueTestosteroneNm = currentFreeTissueTestosteroneNm,
        currentFreeTissueDhtNm = currentFreeTissueDhtNm,
        days = 84,
    )
}
