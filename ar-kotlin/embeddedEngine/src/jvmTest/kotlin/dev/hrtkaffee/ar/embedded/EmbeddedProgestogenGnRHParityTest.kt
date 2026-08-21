package dev.hrtkaffee.ar.embedded

import dev.hrtkaffee.ar.model.ProgestogenGnRHModel
import dev.hrtkaffee.ar.model.ProgestogenGnRHRegimen
import dev.hrtkaffee.ar.model.ProgestogenLigand
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbeddedProgestogenGnRHParityTest {
    @Test
    fun browserProjectionMatchesAuditedJvmModel() {
        val model = ProgestogenGnRHModel()
        EmbeddedProgestogen.entries.forEach { embeddedLigand ->
            val auditedLigand = ProgestogenLigand.fromWireId(embeddedLigand.wireId)
            listOf(0.0, embeddedLigand.defaultDoseMg).forEach { dose ->
                listOf(1, 7, 14).forEach { days ->
                    val embedded = EmbeddedProgestogenGnRHModel.simulate(
                        embeddedLigand,
                        dose,
                        24.0,
                        days,
                    )
                    val audited = model.simulate(
                        ProgestogenGnRHRegimen(auditedLigand, dose, 24.0, days),
                    )
                    assertEquals(
                        audited.finalPoint.plasmaConcentrationNm,
                        embedded.endpoint.plasmaConcentrationNm,
                        1e-12,
                    )
                    assertEquals(
                        audited.finalPoint.prOccupancyFraction,
                        embedded.endpoint.prOccupancyFraction,
                        1e-12,
                    )
                    assertEquals(
                        audited.finalPoint.gnrhPulseSuppressionFraction,
                        embedded.endpoint.gnrhPulseSuppressionFraction,
                        1e-12,
                    )
                }
            }
        }
    }
}
