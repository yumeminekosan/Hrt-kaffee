package dev.hrtkaffee.ar.embedded

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedTransdermalEstradiolModelTest {
    @Test
    fun zeroDeliveryLeavesOnlyBaselineAndNoSkinMass() {
        val result = EmbeddedTransdermalEstradiolModel.simulate(
            TransdermalEstradiolInput(0.0, 5.0, 84.0, 7),
        )
        assertTrue(abs(result.endpoint.totalEstradiolPgMl - 11.7) < 1e-9)
        assertTrue(result.endpoint.skinDepotMicrograms == 0.0)
        assertTrue(result.cumulativeAbsorbedMicrograms == 0.0)
    }

    @Test
    fun labelledFiftyMicrogramPatchTracksPopulationAverage() {
        val result = EmbeddedTransdermalEstradiolModel.simulate(
            TransdermalEstradiolInput(50.0, 5.0, 84.0, 14),
        )
        assertTrue(result.isReferenceDomain)
        assertTrue(result.cAverageLastIntervalPgMl in 48.0..66.0)
        assertTrue(result.cMaxPgMl in 50.0..85.0)
        assertTrue(result.endpoint.freeEstradiolPgMl in 0.0..result.endpoint.totalEstradiolPgMl)
    }

    @Test
    fun doseSeriesIsOrderedAndRemainsPhysical() {
        val results = listOf(25.0, 37.5, 50.0, 75.0, 100.0).map { rate ->
            EmbeddedTransdermalEstradiolModel.simulate(
                TransdermalEstradiolInput(
                    rate,
                    EmbeddedTransdermalEstradiolModel.labelledAreaCm2(rate),
                    84.0,
                    14,
                ),
            )
        }
        assertTrue(results.zipWithNext().all { (low, high) ->
            high.cAverageLastIntervalPgMl > low.cAverageLastIntervalPgMl
        })
        assertTrue(results.flatMap { it.curve }.all { point ->
            point.totalEstradiolPgMl.isFinite() &&
                point.freeEstradiolPgMl.isFinite() &&
                point.skinDepotMicrograms.isFinite() &&
                point.dermalFluxMicrogramsPerCm2Hour.isFinite() &&
                point.erAlphaOccupancyFraction in 0.0..1.0 &&
                point.erBetaOccupancyFraction in 0.0..1.0 &&
                point.gperEngagementFraction in 0.0..1.0
        })
    }

    @Test
    fun rk4StepHalvingIsConverged() {
        val input = TransdermalEstradiolInput(75.0, 7.5, 84.0, 14)
        val coarse = EmbeddedTransdermalEstradiolModel.simulate(input, 0.1)
        val refined = EmbeddedTransdermalEstradiolModel.simulate(input, 0.05)
        assertTrue(abs(coarse.endpoint.totalEstradiolPgMl - refined.endpoint.totalEstradiolPgMl) < 0.02)
        assertTrue(abs(coarse.endpoint.skinDepotMicrograms - refined.endpoint.skinDepotMicrograms) < 0.01)
    }

    @Test
    fun extremeSupportedInputIsFiniteAndMarkedAsExtrapolation() {
        val result = EmbeddedTransdermalEstradiolModel.simulate(
            TransdermalEstradiolInput(200.0, 40.0, 24.0, 365, 1.5, 250.0, 60.0),
            0.2,
        )
        assertFalse(result.isReferenceDomain)
        assertTrue(result.curve.all { it.totalEstradiolPgMl.isFinite() })
        assertTrue(result.cumulativeAbsorbedMicrograms.isFinite())
    }
}
