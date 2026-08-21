package dev.hrtkaffee.ar.embedded

import dev.hrtkaffee.ar.model.DutasterideKineticModel
import dev.hrtkaffee.ar.model.DutasterideRegimen
import dev.hrtkaffee.ar.model.FinasterideKineticModel
import dev.hrtkaffee.ar.model.FinasterideRegimen
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbeddedFiveArParityTest {
    @Test
    fun embeddedFinasterideProjectionMatchesAuditedJvmModel() {
        val model = FinasterideKineticModel()
        listOf(0.0, 0.2, 1.0, 5.0, 15.0).forEach { dose ->
            listOf(1, 14, 42).forEach { days ->
                val embedded = EmbeddedFiveArModel.simulate(FiveArDrug.FINASTERIDE, dose, days)
                val audited = model.simulate(FinasterideRegimen(dose, days))
                assertEquals(
                    audited.finalPoint.serumDhtSuppressionFraction,
                    embedded.endpoint.dhtSuppressionFraction,
                    1e-12,
                )
                assertEquals(
                    audited.finalPoint.type1InhibitionFraction,
                    embedded.endpoint.type1InhibitionFraction,
                    1e-12,
                )
                assertEquals(
                    audited.finalPoint.type2OccupancyFraction,
                    embedded.endpoint.type2InhibitionFraction,
                    1e-12,
                )
            }
        }
    }

    @Test
    fun embeddedDutasterideProjectionMatchesAuditedJvmModel() {
        val model = DutasterideKineticModel()
        listOf(0.0, 0.1, 0.5, 2.5, 5.0, 15.0).forEach { dose ->
            listOf(1, 7, 14, 42).forEach { days ->
                val embedded = EmbeddedFiveArModel.simulate(FiveArDrug.DUTASTERIDE, dose, days)
                val audited = model.simulate(DutasterideRegimen(dose, days))
                assertEquals(
                    audited.finalPoint.serumDhtSuppressionFraction,
                    embedded.endpoint.dhtSuppressionFraction,
                    1e-12,
                )
                assertEquals(
                    audited.finalPoint.type1InhibitionFraction,
                    embedded.endpoint.type1InhibitionFraction,
                    1e-12,
                )
                assertEquals(
                    audited.finalPoint.type2InhibitionFraction,
                    embedded.endpoint.type2InhibitionFraction,
                    1e-12,
                )
            }
        }
    }
}
