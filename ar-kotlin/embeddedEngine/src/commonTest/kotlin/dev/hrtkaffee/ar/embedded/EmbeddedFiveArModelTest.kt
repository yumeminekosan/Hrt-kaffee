package dev.hrtkaffee.ar.embedded

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedFiveArModelTest {
    @Test
    fun zeroDoseLeavesDhtAndBothIsoenzymesAtBaseline() {
        FiveArDrug.entries.forEach { drug ->
            val result = EmbeddedFiveArModel.simulate(drug, dailyDoseMg = 0.0, days = 14)
            assertEquals(0.0, result.endpoint.dhtSuppressionFraction, 1e-12)
            assertEquals(0.0, result.endpoint.type1InhibitionFraction, 1e-12)
            assertEquals(0.0, result.endpoint.type2InhibitionFraction, 1e-12)
        }
    }

    @Test
    fun dutasterideReferenceDoseTracksRegulatoryTimePoints() {
        val week1 = EmbeddedFiveArModel.simulate(FiveArDrug.DUTASTERIDE, 0.5, 7)
        val week2 = EmbeddedFiveArModel.simulate(FiveArDrug.DUTASTERIDE, 0.5, 14)
        val week24 = EmbeddedFiveArModel.simulate(FiveArDrug.DUTASTERIDE, 0.5, 168)
        assertTrue(week1.endpoint.dhtSuppressionFraction in 0.82..0.88)
        assertTrue(week2.endpoint.dhtSuppressionFraction in 0.87..0.93)
        assertTrue(week24.endpoint.dhtSuppressionFraction in 0.91..0.97)
    }

    @Test
    fun bothCurvesAreBoundedAndExtrapolationIsVisible() {
        FiveArDrug.entries.forEach { drug ->
            val result = EmbeddedFiveArModel.simulate(drug, 15.0, 14)
            assertFalse(result.isReferenceDomain)
            assertTrue(result.curve.size in 2..482)
            assertTrue(result.curve.all { point ->
                point.dhtSuppressionFraction in 0.0..1.0 &&
                    point.type1InhibitionFraction in 0.0..1.0 &&
                    point.type2InhibitionFraction in 0.0..1.0 &&
                    point.concentrationNm >= 0.0
            })
        }
    }
}
