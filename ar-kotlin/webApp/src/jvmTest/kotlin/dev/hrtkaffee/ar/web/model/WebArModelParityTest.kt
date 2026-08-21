package dev.hrtkaffee.ar.web.model

import dev.hrtkaffee.ar.model.ArEquilibriumParameters as JvmParameters
import dev.hrtkaffee.ar.model.ArIntervention as JvmIntervention
import dev.hrtkaffee.ar.model.ArSuppressionModel as JvmModel
import dev.hrtkaffee.ar.model.BasisPoints as JvmBasisPoints
import dev.hrtkaffee.ar.model.FinasterideKineticModel as JvmFinasterideModel
import dev.hrtkaffee.ar.model.FinasterideRegimen as JvmFinasterideRegimen
import dev.hrtkaffee.ar.rigor.thermo.ThermalDeBroglie as JvmThermalDeBroglie
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

    @Test
    fun browserThermalWavelengthMatchesTheJvmQuantumBoundary() {
        val masses = listOf(1.0, 2.0, 12.0, 32.0, 128.0)
        val temperatures = listOf(20.0, 77.0, 298.15, 310.0, 400.0)

        for (mass in masses) {
            for (temperature in temperatures) {
                val web = WebThermalDeBroglie.evaluate(mass, temperature)
                val jvm = JvmThermalDeBroglie.evaluate(mass, temperature)
                assertEquals(jvm.wavelengthPicometres, web.wavelengthPicometres, 1e-12)
            }
        }
    }

    @Test
    fun browserFinasterideProjectionMatchesJvmCoreAcrossDoseAndTimeGrid() {
        val webModel = FinasterideKineticModel()
        val jvmModel = JvmFinasterideModel()
        val doses = listOf(0.0, 0.05, 0.2, 1.0, 5.0, 15.0)
        val horizons = listOf(1, 7, 14, 42)

        for (dose in doses) {
            for (days in horizons) {
                val web = webModel.simulate(FinasterideRegimen(dose, days))
                val jvm = jvmModel.simulate(JvmFinasterideRegimen(dose, days))

                assertEquals(jvm.curve.size, web.curve.size)
                assertEquals(jvm.finalPoint.serumDhtFraction, web.finalPoint.serumDhtFraction, 1e-12)
                assertEquals(jvm.finalPoint.type2OccupancyFraction, web.finalPoint.type2OccupancyFraction, 1e-12)
                assertEquals(jvm.finalPoint.type1InhibitionFraction, web.finalPoint.type1InhibitionFraction, 1e-12)
                assertEquals(jvm.peakDhtSuppressionFraction, web.peakDhtSuppressionFraction, 1e-12)
            }
        }
    }
}
