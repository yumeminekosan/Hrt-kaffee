package dev.hrtkaffee.ar.embedded

import dev.hrtkaffee.ar.model.BicalutamideArContext
import dev.hrtkaffee.ar.model.BicalutamideAndrogenEvidence
import dev.hrtkaffee.ar.model.BicalutamideEstradiolBridge
import dev.hrtkaffee.ar.model.BicalutamideEstradiolBridgeInput
import dev.hrtkaffee.ar.model.BicalutamideInput
import dev.hrtkaffee.ar.model.BicalutamideModel
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbeddedBicalutamideParityTest {
    @Test
    fun browserAndAuditedJvmProjectionsAgreeAcrossTheInputGrid() {
        listOf(0.0, 25.0, 50.0, 100.0, 200.0).forEach { dose ->
            listOf(1, 14, 84).forEach { days ->
                listOf(0.0, 1.0).forEach { feedback ->
                    val embeddedInput = EmbeddedBicalutamideInput(
                        doseMg = dose,
                        days = days,
                        freeTissueTestosteroneNm = 0.2,
                        freeTissueDhtNm = 0.018,
                        tissueUnboundPartition = 0.75,
                        hpgFeedbackGain = feedback,
                    )
                    val auditedInput = BicalutamideInput(
                        doseMg = dose,
                        days = days,
                        freeTissueTestosteroneNm = 0.2,
                        freeTissueDhtNm = 0.018,
                        tissueUnboundPartition = 0.75,
                        hpgFeedbackGain = feedback,
                        arContext = BicalutamideArContext.WILD_TYPE,
                    )
                    val embedded = EmbeddedBicalutamideModel.simulate(embeddedInput)
                    val audited = BicalutamideModel.simulate(auditedInput)
                    assertEquals(
                        audited.endpoint.activeRTotalUgMl,
                        embedded.endpoint.activeRTotalUgMl,
                        1e-12,
                    )
                    assertEquals(
                        audited.endpoint.directSuppressionFraction,
                        embedded.endpoint.directSuppressionFraction,
                        1e-12,
                    )
                    assertEquals(
                        audited.endpoint.relativeActivationVsPretreatment,
                        embedded.endpoint.relativeActivationVsPretreatment,
                        1e-12,
                    )
                    assertEquals(
                        audited.endpoint.androgenMultiplier,
                        embedded.endpoint.androgenMultiplier,
                        1e-12,
                    )
                }
            }
        }
    }

    @Test
    fun mutationUpperBoundParityIsExact() {
        val embedded = EmbeddedBicalutamideModel.simulate(
            EmbeddedBicalutamideInput(
                days = 84,
                arContext = EmbeddedBicalutamideArContext.W741L_AGONIST_UPPER_BOUND,
            ),
        )
        val audited = BicalutamideModel.simulate(
            BicalutamideInput(
                days = 84,
                arContext = BicalutamideArContext.W741L_AGONIST_UPPER_BOUND,
            ),
        )
        assertEquals(
            audited.endpoint.directSuppressionFraction,
            embedded.endpoint.directSuppressionFraction,
            1e-12,
        )
    }

    @Test
    fun estradiolBridgeParityIsExactAcrossResidualAndrogenTargets() {
        listOf(0.5, 1.0).forEach { residualMultiplier ->
            listOf(0.25, 0.5, 0.75).forEach { target ->
                val embedded = EmbeddedBicalutamideEstradiolBridge.evaluate(
                    EmbeddedBicalutamideEstradiolBridgeInput(
                        estradiolAveragePgMl = 89.0,
                        androgenEvidence =
                            EmbeddedBicalutamideAndrogenEvidence.LAB_ANCHORED_TISSUE_EQUIVALENTS,
                        currentFreeTissueTestosteroneNm = 0.2 * residualMultiplier,
                        currentFreeTissueDhtNm = 0.018 * residualMultiplier,
                        targetRelativeArSignal = target,
                        days = 84,
                        tissueUnboundPartition = 0.75,
                    ),
                )
                val audited = BicalutamideEstradiolBridge.evaluate(
                    BicalutamideEstradiolBridgeInput(
                        estradiolAveragePgMl = 89.0,
                        androgenEvidence =
                            BicalutamideAndrogenEvidence.LAB_ANCHORED_TISSUE_EQUIVALENTS,
                        currentFreeTissueTestosteroneNm = 0.2 * residualMultiplier,
                        currentFreeTissueDhtNm = 0.018 * residualMultiplier,
                        targetRelativeArSignal = target,
                        days = 84,
                        tissueUnboundPartition = 0.75,
                    ),
                )
                assertEquals(
                    audited.centralMinimumEquivalentDoseMg,
                    embedded.centralMinimumEquivalentDoseMg,
                )
                assertEquals(
                    audited.conservativeMinimumEquivalentDoseMg,
                    embedded.conservativeMinimumEquivalentDoseMg,
                )
                assertEquals(audited.candidates.size, embedded.candidates.size)
                audited.candidates.zip(embedded.candidates).forEach { (expected, actual) ->
                    assertEquals(expected.doseMg, actual.doseMg, 0.0)
                    assertEquals(
                        expected.centralWorstRelativeArSignal,
                        actual.centralWorstRelativeArSignal,
                        1e-12,
                    )
                    assertEquals(
                        expected.conservativeWorstRelativeArSignal,
                        actual.conservativeWorstRelativeArSignal,
                        1e-12,
                    )
                }
            }
        }
    }
}
