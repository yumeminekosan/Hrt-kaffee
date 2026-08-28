package dev.hrtkaffee.ar.embedded

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedProgestogenGnRHModelTest {
    @Test
    fun zeroDoseLeavesPrAndGnRHPulseStateAtBaseline() {
        EmbeddedProgestogen.entries.forEach { ligand ->
            val result = EmbeddedProgestogenGnRHModel.simulate(ligand, 0.0, 24.0, 14)
            assertTrue(result.endpoint.plasmaConcentrationNm == 0.0)
            assertTrue(result.endpoint.prOccupancyFraction == 0.0)
            assertTrue(result.endpoint.gnrhPulseSuppressionFraction == 0.0)
        }
    }

    @Test
    fun oralP4CmaxTracksLabelAndFeedbackIsDelayed() {
        val result = EmbeddedProgestogenGnRHModel.simulate(
            EmbeddedProgestogen.PROGESTERONE,
            100.0,
            24.0,
            5,
        )
        assertTrue(result.curve.maxOf { it.plasmaConcentrationNgPerMl } in 14.0..21.0)
        assertTrue(result.peakPrOccupancyFraction > result.peakGnRHPulseSuppressionFraction)
    }

    @Test
    fun p4ExposureAndCoverageWindowRespondToDoseAndStayBounded() {
        val lowDose = EmbeddedProgestogenGnRHModel.simulate(
            EmbeddedProgestogen.PROGESTERONE,
            100.0,
            24.0,
            14,
        )
        val highDose = EmbeddedProgestogenGnRHModel.simulate(
            EmbeddedProgestogen.PROGESTERONE,
            400.0,
            24.0,
            14,
        )
        assertTrue(highDose.peakPrOccupancyFraction > lowDose.peakPrOccupancyFraction)
        assertTrue(highDose.peakGnRHPulseSuppressionFraction > lowDose.peakGnRHPulseSuppressionFraction)

        val estimate = EmbeddedProgestogenGnRHModel.estimateCoverageAfterLastDose(
            EmbeddedProgestogen.PROGESTERONE,
            100.0,
            24.0,
            14,
            0.10,
        )
        assertTrue(estimate.reachesThreshold)
        assertTrue(estimate.peakSuppressionAfterLastDoseFraction in 0.0..1.0)
        assertTrue(estimate.timeUntilFinalBelowThresholdHours in 0.0..estimate.searchHorizonHours)

        val zeroDose = EmbeddedProgestogenGnRHModel.estimateCoverageAfterLastDose(
            EmbeddedProgestogen.PROGESTERONE,
            0.0,
            24.0,
            14,
            0.10,
        )
        assertFalse(zeroDose.reachesThreshold)
        assertTrue(zeroDose.timeUntilFinalBelowThresholdHours == 0.0)
    }

    @Test
    fun browserInputCeilingStaysFiniteAndSignalsExtrapolation() {
        val result = EmbeddedProgestogenGnRHModel.simulate(
            EmbeddedProgestogen.PROGESTERONE,
            500.0,
            4.0,
            365,
        )
        assertFalse(result.isReferenceDomain)
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
    fun syntheticProfilesAreBoundedAndExplicitlyExtrapolated() {
        EmbeddedProgestogen.entries.forEach { ligand ->
            val result = EmbeddedProgestogenGnRHModel.simulate(
                ligand,
                ligand.defaultDoseMg,
                24.0,
                14,
            )
            assertTrue(result.curve.all { point ->
                point.plasmaConcentrationNm >= 0.0 &&
                    point.prOccupancyFraction in 0.0..1.0 &&
                    point.gnrhPulseSuppressionFraction in 0.0..1.0
            })
            if (ligand != EmbeddedProgestogen.PROGESTERONE) {
                assertFalse(result.isReferenceDomain)
            }
        }
    }
}
