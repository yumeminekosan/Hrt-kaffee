package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.ArIntervention
import dev.hrtkaffee.ar.model.ArMicroscopicNetwork
import dev.hrtkaffee.ar.model.ArRigorousPipeline
import dev.hrtkaffee.ar.model.BasisPoints
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.thermo.ExactQuantumRateCalibration
import dev.hrtkaffee.ar.rigor.thermo.QuantumBindingBridge
import dev.hrtkaffee.ar.rigor.thermo.QuantumFreeEnergyShift
import dev.hrtkaffee.ar.rigor.thermo.ThermalDeBroglie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NuclearQuantumBindingTest {
    @Test
    fun thermalWavelengthHasTheExpectedInverseSquareRootScaling() {
        val reference = ThermalDeBroglie.evaluate(massDalton = 1.0, temperatureKelvin = 300.0)
        val fourTimesMass = ThermalDeBroglie.evaluate(massDalton = 4.0, temperatureKelvin = 300.0)
        val fourTimesTemperature = ThermalDeBroglie.evaluate(massDalton = 1.0, temperatureKelvin = 1_200.0)

        assertEquals(reference.wavelengthPicometres / 2.0, fourTimesMass.wavelengthPicometres, 1e-12)
        assertEquals(reference.wavelengthPicometres / 2.0, fourTimesTemperature.wavelengthPicometres, 1e-12)
    }

    @Test
    fun freeEnergyInputProjectsToConsistentForwardAndReverseFactors() {
        val projection = QuantumBindingBridge.project(
            QuantumFreeEnergyShift(
                temperatureKelvin = 310.0,
                bindingFreeEnergyShiftKilojoulesPerMole = -1.0,
                forwardActivationFreeEnergyShiftKilojoulesPerMole = -2.0,
                provenance = "audited path-integral free-energy calculation",
            ),
        )

        assertEquals(EvidenceKind.ILLUSTRATIVE_PARAMETERIZATION, projection.kind)
        assertTrue(projection.value.bindingEquilibriumMultiplier > 1.0)
        assertTrue(projection.value.forwardBindingRateMultiplier > projection.value.bindingEquilibriumMultiplier)
        assertEquals(
            projection.value.forwardBindingRateMultiplier,
            projection.value.bindingEquilibriumMultiplier * projection.value.reverseUnbindingRateMultiplier,
            1e-12,
        )
    }

    @Test
    fun exactQuantumCalibrationChangesOnlyNamedCtmcRatesAndKeepsProvenance() {
        val intervention = ArIntervention(BasisPoints(4_200), BasisPoints(5_800))
        val calibration = ExactQuantumRateCalibration(
            reactionRateMultipliers = mapOf("a_bind" to Rational.of(2)),
            provenance = "rationalized external quantum-rate result",
        )
        val baseline = ArMicroscopicNetwork.create(intervention)
        val calibrated = ArMicroscopicNetwork.create(intervention, calibration)
        val baselineRates = baseline.network.reactions.associate { it.id to it.rate }
        val calibratedRates = calibrated.network.reactions.associate { it.id to it.rate }

        assertEquals(baselineRates.getValue("a_bind") * Rational.of(2), calibratedRates.getValue("a_bind"))
        assertEquals(baselineRates.getValue("dht_bind"), calibratedRates.getValue("dht_bind"))
        assertSame(calibration, ArRigorousPipeline.prepare(intervention, quantumRateCalibration = calibration).quantumRateCalibration)

        assertFailsWith<IllegalArgumentException> {
            ArMicroscopicNetwork.create(
                intervention,
                ExactQuantumRateCalibration(
                    reactionRateMultipliers = mapOf("not_a_reaction" to Rational.ONE),
                    provenance = "invalid fixture",
                ),
            )
        }
    }
}
