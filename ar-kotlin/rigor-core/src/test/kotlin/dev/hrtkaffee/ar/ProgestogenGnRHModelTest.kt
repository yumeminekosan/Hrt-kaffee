package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.ProgestogenGnRHModel
import dev.hrtkaffee.ar.model.ProgestogenGnRHRegimen
import dev.hrtkaffee.ar.model.ProgestogenLigand
import dev.hrtkaffee.ar.model.ProgestogenGnRHRigorousPipeline
import dev.hrtkaffee.ar.rigor.EvidenceKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgestogenGnRHModelTest {
    private val model = ProgestogenGnRHModel()

    @Test
    fun zeroDoseLeavesPrAndPulseGeneratorAtBaseline() {
        ProgestogenLigand.entries.forEach { ligand ->
            val result = model.simulate(ProgestogenGnRHRegimen(ligand, 0.0, 24.0, 14))
            assertTrue(result.finalPoint.plasmaConcentrationNm == 0.0)
            assertTrue(result.finalPoint.prOccupancyFraction == 0.0)
            assertTrue(result.finalPoint.gnrhPulseSuppressionFraction == 0.0)
        }
    }

    @Test
    fun oralP4ProfileTracksTheLabelCmaxAndShowsDelayedFeedback() {
        val result = model.simulate(
            ProgestogenGnRHRegimen(ProgestogenLigand.PROGESTERONE, 100.0, 24.0, 5),
        )
        val peakConcentration = result.curve.maxOf { it.plasmaConcentrationNgPerMl }
        assertTrue(peakConcentration in 14.0..21.0)
        assertTrue(result.peakPrOccupancyFraction > result.peakGnRHPulseSuppressionFraction)
        assertTrue(result.peakGnRHPulseSuppressionFraction in 0.0..0.55)
    }

    @Test
    fun p4ExposureAndCoverageWindowRespondToDoseAndRemainPhysical() {
        val lowDose = model.simulate(
            ProgestogenGnRHRegimen(ProgestogenLigand.PROGESTERONE, 100.0, 24.0, 14),
        )
        val highDose = model.simulate(
            ProgestogenGnRHRegimen(ProgestogenLigand.PROGESTERONE, 400.0, 24.0, 14),
        )
        assertTrue(highDose.peakPrOccupancyFraction > lowDose.peakPrOccupancyFraction)
        assertTrue(highDose.peakGnRHPulseSuppressionFraction > lowDose.peakGnRHPulseSuppressionFraction)

        val estimate = model.estimateCoverageAfterLastDose(
            ProgestogenGnRHRegimen(ProgestogenLigand.PROGESTERONE, 100.0, 24.0, 14),
            0.10,
        )
        assertTrue(estimate.reachesThreshold)
        assertTrue(estimate.peakSuppressionAfterLastDoseFraction in 0.0..1.0)
        assertTrue(estimate.timeUntilFinalBelowThresholdHours in 0.0..estimate.searchHorizonHours)

        val zeroDose = model.estimateCoverageAfterLastDose(
            ProgestogenGnRHRegimen(ProgestogenLigand.PROGESTERONE, 0.0, 24.0, 14),
            0.10,
        )
        assertFalse(zeroDose.reachesThreshold)
        assertTrue(zeroDose.timeUntilFinalBelowThresholdHours == 0.0)
    }

    @Test
    fun inputCeilingStaysFiniteAndIsMarkedAsAnExtrapolation() {
        val result = model.simulate(
            ProgestogenGnRHRegimen(ProgestogenLigand.PROGESTERONE, 500.0, 4.0, 365),
        )
        assertFalse(result.regimen.isPopulationReferenceDomain)
        assertTrue(result.curve.all { point ->
            point.plasmaConcentrationNm.isFinite() &&
                point.plasmaConcentrationNgPerMl.isFinite() &&
                point.prOccupancyFraction.isFinite() &&
                point.gnrhPulseSuppressionFraction.isFinite() &&
                point.prOccupancyFraction in 0.0..1.0 &&
                point.gnrhPulseSuppressionFraction in 0.0..1.0
        })
    }

    @Test
    fun allProfilesRemainPhysicalAndSyntheticOutputsAreFlaggedAsExtrapolations() {
        ProgestogenLigand.entries.forEach { ligand ->
            val result = model.simulate(
                ProgestogenGnRHRegimen(ligand, ligand.defaultDoseMg, 24.0, 14),
            )
            assertTrue(result.curve.size in 2..482)
            assertTrue(result.curve.all { point ->
                point.plasmaConcentrationNm >= 0.0 &&
                    point.plasmaConcentrationNgPerMl >= 0.0 &&
                    point.prOccupancyFraction in 0.0..1.0 &&
                    point.gnrhPulseSuppressionFraction in 0.0..1.0 &&
                    point.gnrhPulseActivityFraction in 0.0..1.0
            })
            if (ligand != ProgestogenLigand.PROGESTERONE) {
                assertFalse(result.regimen.isPopulationReferenceDomain)
            }
        }
    }

    @Test
    fun refinedSolverAndStructuralAssignmentAreExplicit() {
        val regimen = ProgestogenGnRHRegimen(
            ProgestogenLigand.PROGESTERONE,
            200.0,
            24.0,
            14,
        )
        val certificate = model.certify(regimen)
        assertTrue(certificate.diagnostics.all { it.passed })
        val artifacts = ProgestogenGnRHRigorousPipeline.prepare()
        assertTrue(artifacts.exactGenerator.isIrreducible())
        assertTrue(artifacts.structuralAnchor.directProgesteroneComplexObserved)
        assertFalse(artifacts.structuralAnchor.directGnRHReceptorBindingAsserted)
        assertTrue(artifacts.chainComplex.audit().kind == EvidenceKind.EXACT_IDENTITY)
    }
}
