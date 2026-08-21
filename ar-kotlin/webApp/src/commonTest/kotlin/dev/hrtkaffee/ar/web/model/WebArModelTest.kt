package dev.hrtkaffee.ar.web.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebArModelTest {
    private val model = ArSuppressionModel(ArEquilibriumParameters.illustrative())

    @Test
    fun zeroInterventionReturnsControlWorld() {
        val result = model.evaluate(ArIntervention(BasisPoints(0), BasisPoints(0)))

        assertEquals(Rational.ONE, result.signalRelativeToControl)
        assertEquals(Rational.ZERO, result.directShapleyContribution)
        assertEquals(Rational.ZERO, result.upstreamShapleyContribution)
        assertEquals(Rational.ZERO, result.nonAdditivity)
    }

    @Test
    fun shapleyContributionsExactlyCloseCombinedSuppression() {
        val result = model.evaluate(ArIntervention(BasisPoints(4_200), BasisPoints(5_800)))
        val combinedSuppression = Rational.ONE - result.signalRelativeToControl

        assertEquals(
            combinedSuppression,
            result.directShapleyContribution + result.upstreamShapleyContribution,
        )
        assertTrue(result.signalRelativeToControl in Rational.ZERO..Rational.ONE)
    }

    @Test
    fun arithmeticIsNormalizedBeforeFormatting() {
        assertEquals("1/2", Rational.of(5_000, 10_000).toString())
        assertEquals("-3/4", Rational.of(9, -12).toString())
        assertEquals(Rational.of(5, 6), Rational.of(2, 3) + Rational.of(1, 6))
    }
}
