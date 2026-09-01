package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.BicalutamideArContext
import dev.hrtkaffee.ar.model.BicalutamideInput
import dev.hrtkaffee.ar.model.BicalutamideModel
import dev.hrtkaffee.ar.model.BicalutamideRigorousPipeline
import dev.hrtkaffee.ar.rigor.exact.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BicalutamideModelTest {
    @Test
    fun labelSingleDoseAnchorsAreRecoveredAndCssIsAnExternalResidual() {
        val peak = BicalutamideModel.singleDoseActiveRConcentrationUgMl(50.0, 31.3)
        assertEquals(BicalutamideModel.LABEL_SINGLE_DOSE_CMAX_UG_ML, peak, 1e-12)
        val result = BicalutamideModel.simulate(BicalutamideInput(days = 42))
        assertEquals(31.3, result.calibration.singleDosePeakTimeHours, 0.0)
        assertTrue(result.calibration.steadyStateStandardizedResidual < 0.5)
    }

    @Test
    fun zeroDoseLeavesAndrogensAndSameAndrogenSignalAtBaseline() {
        val input = BicalutamideInput(doseMg = 0.0, days = 42)
        val result = BicalutamideModel.simulate(input)
        assertEquals(0.0, result.endpoint.activeRTotalUgMl, 0.0)
        assertEquals(0.0, result.endpoint.bicalutamideOccupancyFraction, 0.0)
        assertEquals(0.0, result.endpoint.directSuppressionFraction, 1e-12)
        assertEquals(input.freeTissueTestosteroneNm, result.endpoint.freeTissueTestosteroneNm, 0.0)
        assertEquals(input.freeTissueDhtNm, result.endpoint.freeTissueDhtNm, 0.0)
    }

    @Test
    fun occupanciesPartitionTheReceptorAndFixedModeDoesNotLowerAndrogenAmount() {
        val input = BicalutamideInput(days = 84, hpgFeedbackGain = 0.0)
        val result = BicalutamideModel.simulate(input)
        result.curve.forEach { point ->
            assertEquals(
                1.0,
                point.freeArFraction + point.testosteroneOccupancyFraction +
                    point.dhtOccupancyFraction + point.bicalutamideOccupancyFraction,
                1e-12,
            )
            assertEquals(input.freeTissueTestosteroneNm, point.freeTissueTestosteroneNm, 0.0)
            assertEquals(input.freeTissueDhtNm, point.freeTissueDhtNm, 0.0)
        }
    }

    @Test
    fun fixedWildTypeCompetitionIsDoseMonotone() {
        val suppressions = listOf(0.0, 10.0, 25.0, 50.0, 100.0, 200.0).map { dose ->
            BicalutamideModel.simulate(BicalutamideInput(doseMg = dose, days = 84))
                .endpoint.directSuppressionFraction
        }
        assertEquals(suppressions.sorted(), suppressions)
        assertTrue(suppressions.last() > suppressions.first())
    }

    @Test
    fun feedbackIsVisibleButNotConfusedWithDirectSameAndrogenCounterfactual() {
        val fixed = BicalutamideModel.simulate(BicalutamideInput(days = 84))
        val feedback = BicalutamideModel.simulate(
            BicalutamideInput(days = 84, hpgFeedbackGain = 1.0),
        )
        assertTrue(feedback.endpoint.androgenMultiplier > 1.0)
        assertTrue(feedback.endpoint.freeTissueTestosteroneNm > fixed.endpoint.freeTissueTestosteroneNm)
        assertTrue(feedback.endpoint.sameAndrogenCounterfactualActivationFraction >
            fixed.endpoint.sameAndrogenCounterfactualActivationFraction)
    }

    @Test
    fun w741lIsExplicitlyAnUncalibratedAgonistUpperBound() {
        val result = BicalutamideModel.simulate(
            BicalutamideInput(
                days = 84,
                arContext = BicalutamideArContext.W741L_AGONIST_UPPER_BOUND,
            ),
        )
        assertTrue(result.endpoint.directSuppressionFraction < 0.0)
        assertTrue(result.boundaryMessage.contains("定性激动上界"))
        assertFalse(result.isPkReferenceRegimen.not())
    }

    @Test
    fun microscopicTableFeedsExactGeneratorDensityLimitAndTopology() {
        val artifacts = BicalutamideRigorousPipeline.prepare()
        assertTrue(artifacts.exactGenerator.isIrreducible())
        assertTrue(artifacts.baseReactionNetwork.reactions.all { reaction ->
            reaction.rate > Rational.ZERO && reaction.reverseReactionId != null
        })
        assertTrue(artifacts.densityLimitSymbol.reactions.isNotEmpty())
        assertTrue(artifacts.chainComplex.audit().value.reactionCycleBasis.isNotEmpty())
        assertEquals("1Z95", artifacts.structuralAnchor.structurePdbId)
        assertFalse(artifacts.structuralAnchor.directWildTypeComplexObserved)
    }
}
