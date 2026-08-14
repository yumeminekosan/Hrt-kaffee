package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.DynkinMartingale
import dev.hrtkaffee.ar.rigor.thermo.CycleAffinity
import dev.hrtkaffee.ar.rigor.thermo.Thermodynamics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThermodynamicsAndMartingaleTest {
    @Test
    fun stationaryLawAndDetailedBalanceAreExact() {
        val generator = RationalAndGeneratorTest.twoStateGenerator()
        val stationary = Thermodynamics.stationaryDistribution(generator).value
        assertEquals(listOf(Rational.of(3, 5), Rational.of(2, 5)), stationary)
        assertTrue(Thermodynamics.detailedBalance(generator, stationary).value.satisfied)
        val entropyProduction = Thermodynamics.entropyProductionRate(generator, stationary)
        assertFalse(entropyProduction.value.hasOneWayStationaryFlow)
        assertEquals(0.0, entropyProduction.value.value, 1e-15)
        assertTrue(entropyProduction.diagnostics.all { it.passed })
    }

    @Test
    fun formalCycleAffinityDoesNotNeedFloatingPointLogs() {
        val generator = RationalAndGeneratorTest.twoStateGenerator()
        val affinity = CycleAffinity.exact(generator, listOf(0, 1, 0)).value
        assertTrue(affinity.isZero())
        assertEquals(Rational.ONE, affinity.argument)
    }

    @Test
    fun finiteStateCarreDuChampBoundIsExact() {
        val generator = RationalAndGeneratorTest.twoStateGenerator()
        val function = listOf(Rational.ZERO, Rational.ONE)
        assertEquals(Rational.of(3), DynkinMartingale.carréDuChampBound(generator, function).value)
    }

    @Test
    fun sampledDynkinMartingaleMeanIsConsistentWithZero() {
        val generator = RationalAndGeneratorTest.twoStateGenerator()
        val estimate = DynkinMartingale.estimateMean(
            generator = generator,
            testFunction = listOf(Rational.ZERO, Rational.ONE),
            initialState = 0,
            horizon = 1.0,
            trajectories = 5_000,
            seed = 20260814,
        ).value
        assertTrue(abs(estimate.mean) <= 4.0 * estimate.standardError + 1e-3)
    }
}
