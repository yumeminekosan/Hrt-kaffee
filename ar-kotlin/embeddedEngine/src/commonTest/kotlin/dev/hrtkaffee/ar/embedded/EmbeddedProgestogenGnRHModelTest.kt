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
            1,
        )
        assertTrue(result.curve.maxOf { it.plasmaConcentrationNgPerMl } in 14.0..21.0)
        assertTrue(result.peakPrOccupancyFraction > result.peakGnRHPulseSuppressionFraction)
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
