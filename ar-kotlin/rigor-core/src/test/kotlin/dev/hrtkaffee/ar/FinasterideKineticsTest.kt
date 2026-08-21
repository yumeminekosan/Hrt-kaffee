package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.FinasterideKineticModel
import dev.hrtkaffee.ar.model.FinasteridePkPdParameters
import dev.hrtkaffee.ar.model.FinasterideRegimen
import dev.hrtkaffee.ar.model.FinasterideRigorousPipeline
import dev.hrtkaffee.ar.rigor.EvidenceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinasterideKineticsTest {
    private val model = FinasterideKineticModel()

    @Test
    fun zeroDoseLeavesDhtAndEnzymeOccupancyAtBaseline() {
        val result = model.simulate(FinasterideRegimen(dailyDoseMg = 0.0, days = 14))

        assertEquals(1.0, result.finalPoint.serumDhtFraction, 1e-12)
        assertEquals(0.0, result.finalPoint.type2OccupancyFraction, 1e-12)
        assertEquals(0.0, result.finalPoint.type1InhibitionFraction, 1e-12)
    }

    @Test
    fun identifiedParametersRecoverThePublishedEffectiveType2Kd() {
        val parameters = FinasteridePkPdParameters.suzuki2010()

        assertEquals(0.0085671549, parameters.effectiveType2DissociationNm, 1e-10)
    }

    @Test
    fun repeatedDoseProducesSaturableType2BindingAndDhtSuppression() {
        val low = model.simulate(FinasterideRegimen(dailyDoseMg = 0.05, days = 14))
        val standard = model.simulate(FinasterideRegimen(dailyDoseMg = 1.0, days = 14))
        val high = model.simulate(FinasterideRegimen(dailyDoseMg = 5.0, days = 14))

        assertTrue(low.finalPoint.serumDhtSuppressionFraction > 0.1)
        assertTrue(standard.finalPoint.serumDhtSuppressionFraction > low.finalPoint.serumDhtSuppressionFraction)
        assertTrue(high.peakType2OccupancyFraction > 0.99)
        assertTrue(high.finalPoint.serumDhtSuppressionFraction < 0.9)
    }

    @Test
    fun fifteenMilligramsIsCalculatedButFlaggedOutsideRepeatedDoseReferenceDomain() {
        val regimen = FinasterideRegimen(dailyDoseMg = 15.0, days = 14)
        val result = model.simulate(regimen)

        assertFalse(regimen.isRepeatedDoseReferenceDomain)
        assertTrue(result.finalPoint.serumDhtSuppressionFraction in 0.0..1.0)
        assertTrue(result.finalPoint.type2OccupancyFraction in 0.0..1.0)
    }

    @Test
    fun stepHalvingProducesANumericalCertificate() {
        val evidence = model.certify(
            FinasterideRegimen(dailyDoseMg = 1.0, days = 14, integrationStepHours = 0.05),
        )

        assertEquals(EvidenceKind.NUMERICAL_CERTIFICATE, evidence.kind)
        assertTrue(evidence.diagnostics.single().passed)
    }

    @Test
    fun microscopicNetworkSeparatesFiveAlphaReductaseFromAndrogenReceptor() {
        val artifacts = FinasterideRigorousPipeline.prepare()
        val speciesIds = artifacts.baseReactionNetwork.species.map { it.id }
        val reactionIds = artifacts.baseReactionNetwork.reactions.map { it.id }

        assertTrue("SRD5A2_FIN" in speciesIds)
        assertFalse(speciesIds.any { it == "AR" || it.endsWith("_AR") })
        assertTrue("e2_bind" in reactionIds)
        assertEquals("7BW1", artifacts.structuralAnchor.pdbId)
        assertEquals(listOf("E57", "Y91"), artifacts.structuralAnchor.catalyticResidues)
        assertEquals(reactionIds.size, artifacts.densityLimitSymbol.reactions.size)
        assertTrue(artifacts.chainComplex.conservationLaws().isNotEmpty())
        assertTrue(artifacts.exactGenerator.isIrreducible())
    }
}
