package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.ArEquilibriumParameters
import dev.hrtkaffee.ar.model.ArIntervention
import dev.hrtkaffee.ar.model.ArSuppressionModel
import dev.hrtkaffee.ar.model.BasisPoints
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArSuppressionTest {
    private val model = ArSuppressionModel(ArEquilibriumParameters.illustrative())

    @Test
    fun zeroInterventionMatchesControlExactly() {
        val result = model.evaluate(ArIntervention(BasisPoints(0), BasisPoints(0)))
        assertEquals(result.counterfactuals.control, result.counterfactuals.combined)
        assertEquals(Rational.ONE, result.signalRelativeToControl)
        assertEquals(Rational.ZERO, result.directShapleyContribution)
        assertEquals(Rational.ZERO, result.upstreamShapleyContribution)
    }

    @Test
    fun shapleyContributionsSumToCombinedSignalChangeExactly() {
        val result = model.evaluate(ArIntervention(BasisPoints(4_200), BasisPoints(5_800)))
        val totalSuppression = Rational.ONE - result.signalRelativeToControl
        assertEquals(
            totalSuppression,
            result.directShapleyContribution + result.upstreamShapleyContribution,
        )
        assertEquals(EvidenceKind.EXACT_IDENTITY, result.exactModelEvidence.kind)
        assertEquals(EvidenceKind.ILLUSTRATIVE_PARAMETERIZATION, result.parameterEvidence.kind)
    }

    @Test
    fun eachMechanismCanBeSwitchedOffWithoutMasqueradingAsTheOther() {
        val directOnly = model.evaluate(ArIntervention(BasisPoints(8_000), BasisPoints(0)))
        val upstreamOnly = model.evaluate(ArIntervention(BasisPoints(0), BasisPoints(8_000)))
        assertEquals(Rational.ZERO, directOnly.upstreamShapleyContribution)
        assertEquals(Rational.ZERO, upstreamOnly.directShapleyContribution)
        assertTrue(directOnly.signalRelativeToControl < Rational.ONE)
        assertTrue(upstreamOnly.signalRelativeToControl < Rational.ONE)
    }
}
