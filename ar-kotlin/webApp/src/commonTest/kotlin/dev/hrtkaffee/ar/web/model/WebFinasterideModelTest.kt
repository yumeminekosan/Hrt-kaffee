package dev.hrtkaffee.ar.web.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebFinasterideModelTest {
    private val model = FinasterideKineticModel()

    @Test
    fun curveIsBoundedAndEndsAtTheRequestedHorizon() {
        val result = model.simulate(FinasterideRegimen(dailyDoseMg = 15.0, days = 14))

        assertFalse(result.regimen.isRepeatedDoseReferenceDomain)
        assertTrue(result.curve.size in 2..481)
        assertTrue(result.curve.zipWithNext().all { (left, right) -> left.timeHours < right.timeHours })
        assertTrue(result.finalPoint.timeHours == 14.0 * 24.0)
        assertTrue(result.curve.all { point ->
            point.serumDhtFraction in 0.0..1.0 &&
                point.type1InhibitionFraction in 0.0..1.0 &&
                point.type2OccupancyFraction in 0.0..1.0
        })
    }

    @Test
    fun doseResponseSaturatesAtType2BeforeType1() {
        val low = model.simulate(FinasterideRegimen(dailyDoseMg = 0.2, days = 14))
        val high = model.simulate(FinasterideRegimen(dailyDoseMg = 5.0, days = 14))

        assertTrue(low.peakType2OccupancyFraction > 0.9)
        assertTrue(high.peakType2OccupancyFraction > low.peakType2OccupancyFraction)
        assertTrue(high.finalPoint.type1InhibitionFraction > low.finalPoint.type1InhibitionFraction)
    }
}
