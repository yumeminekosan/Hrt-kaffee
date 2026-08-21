package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.DutasterideKineticModel
import dev.hrtkaffee.ar.model.DutasterideRegimen
import dev.hrtkaffee.ar.model.DutasterideRigorousPipeline
import dev.hrtkaffee.ar.rigor.EvidenceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DutasterideKineticsTest {
    @Test
    fun zeroDoseIsTheDhtAndEnzymeBaseline() {
        val result = DutasterideKineticModel().simulate(DutasterideRegimen(0.0, 14))
        assertEquals(0.0, result.finalPoint.serumDhtSuppressionFraction, 1e-12)
        assertEquals(0.0, result.finalPoint.type1InhibitionFraction, 1e-12)
        assertEquals(0.0, result.finalPoint.type2InhibitionFraction, 1e-12)
    }

    @Test
    fun referenceDoseMatchesTheDeclaredPopulationCalibration() {
        val model = DutasterideKineticModel()
        val week1 = model.simulate(DutasterideRegimen(0.5, 7))
        val week2 = model.simulate(DutasterideRegimen(0.5, 14))
        val week24 = model.simulate(DutasterideRegimen(0.5, 168))
        assertTrue(week1.finalPoint.serumDhtSuppressionFraction in 0.82..0.88)
        assertTrue(week2.finalPoint.serumDhtSuppressionFraction in 0.87..0.93)
        assertTrue(week24.finalPoint.serumDhtSuppressionFraction in 0.91..0.97)
    }

    @Test
    fun numericalCertificateReportsStepHalvingResidual() {
        val evidence = DutasterideKineticModel().certify(DutasterideRegimen(0.5, 14))
        assertEquals(EvidenceKind.NUMERICAL_CERTIFICATE, evidence.kind)
        assertTrue(evidence.diagnostics.all { it.passed })
    }

    @Test
    fun microscopicNetworkTargetsDualFiveAlphaReductaseAndNeverAr() {
        val artifacts = DutasterideRigorousPipeline.prepare()
        assertTrue(artifacts.exactGenerator.isIrreducible())
        assertTrue(artifacts.baseReactionNetwork.species.none { it.id == "AR" })
        assertEquals(listOf("human SRD5A1", "human SRD5A2"), artifacts.structuralAnchor.targets)
        assertEquals("7BW1", artifacts.structuralAnchor.structuralTemplatePdbId)
        assertFalse(artifacts.structuralAnchor.directDutasterideComplexObserved)
        assertEquals(
            artifacts.baseReactionNetwork.reactions.size,
            artifacts.densityLimitSymbol.reactions.size,
        )
        assertTrue(artifacts.chainComplex.audit().value.conservationLawBasis.isNotEmpty())
    }
}
