package dev.hrtkaffee.ar.web.model

import dev.hrtkaffee.ar.model.ArEquilibriumParameters as JvmParameters
import dev.hrtkaffee.ar.model.ArIntervention as JvmIntervention
import dev.hrtkaffee.ar.model.ArSuppressionModel as JvmModel
import dev.hrtkaffee.ar.model.BasisPoints as JvmBasisPoints
import kotlin.test.Test
import kotlin.test.assertEquals

class WebArModelParityTest {
    @Test
    fun browserProjectionMatchesArbitraryPrecisionJvmModelAcrossControlGrid() {
        val webModel = ArSuppressionModel(ArEquilibriumParameters.illustrative())
        val jvmModel = JvmModel(JvmParameters.illustrative())
        val controlPoints = listOf(0, 1, 1_250, 4_200, 5_000, 5_800, 8_750, 9_999, 10_000)

        for (direct in controlPoints) {
            for (upstream in controlPoints) {
                val web = webModel.evaluate(ArIntervention(BasisPoints(direct), BasisPoints(upstream)))
                val jvm = jvmModel.evaluate(
                    JvmIntervention(JvmBasisPoints(direct), JvmBasisPoints(upstream)),
                )

                assertEquals(jvm.counterfactuals.control.toString(), web.counterfactuals.control.toString())
                assertEquals(jvm.counterfactuals.directOnly.toString(), web.counterfactuals.directOnly.toString())
                assertEquals(jvm.counterfactuals.upstreamOnly.toString(), web.counterfactuals.upstreamOnly.toString())
                assertEquals(jvm.counterfactuals.combined.toString(), web.counterfactuals.combined.toString())
                assertEquals(jvm.signalRelativeToControl.toString(), web.signalRelativeToControl.toString())
                assertEquals(jvm.directShapleyContribution.toString(), web.directShapleyContribution.toString())
                assertEquals(jvm.upstreamShapleyContribution.toString(), web.upstreamShapleyContribution.toString())
                assertEquals(jvm.nonAdditivity.toString(), web.nonAdditivity.toString())
            }
        }
    }
}
