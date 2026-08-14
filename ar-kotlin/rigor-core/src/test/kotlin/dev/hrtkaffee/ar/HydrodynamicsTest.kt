package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.hydrodynamic.BoundaryCondition
import dev.hrtkaffee.ar.rigor.hydrodynamic.ConservativeFiniteVolume1D
import dev.hrtkaffee.ar.rigor.hydrodynamic.HydrodynamicConditions
import dev.hrtkaffee.ar.rigor.hydrodynamic.SpatialDensity
import dev.hrtkaffee.ar.rigor.hydrodynamic.hydrodynamicLimitClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HydrodynamicsTest {
    @Test
    fun periodicFiniteVolumeDiffusionPreservesMassAndPositivity() {
        val solver = ConservativeFiniteVolume1D(
            cellWidth = 1.0,
            diffusion = doubleArrayOf(1.0),
            boundaryCondition = BoundaryCondition.PERIODIC,
            reaction = { doubleArrayOf(0.0) },
            conservationWeights = doubleArrayOf(1.0),
        )
        val step = solver.step(
            current = SpatialDensity(arrayOf(doubleArrayOf(1.0, 0.0, 0.0, 0.0))),
            timeStep = 0.1,
        )
        assertEquals(EvidenceKind.NUMERICAL_CERTIFICATE, step.kind)
        assertEquals(0.0, step.value.conservationResidual, 1e-15)
        assertEquals(0.0, step.value.negativityMagnitude, 1e-15)
        assertTrue(step.diagnostics.all { it.passed })
    }

    @Test
    fun pdeDiscretizationAloneCannotCertifyHydrodynamicLimit() {
        assertFailsWith<IllegalArgumentException> {
            hydrodynamicLimitClaim(
                HydrodynamicConditions(
                    spatialGeneratorEstablished = true,
                    diffusiveScalingEstablished = true,
                    localEquilibriumEstablished = true,
                    tightnessEstablished = false,
                    spatialGeneratorWitness = "periodic lattice generator",
                    diffusiveScalingWitness = "L² migration rate",
                    localEquilibriumWitness = "replacement lemma",
                    tightnessWitness = "not established",
                ),
            )
        }
    }

    @Test
    fun namedHydrodynamicAssumptionsProduceConditionalTheoremOnly() {
        val claim = hydrodynamicLimitClaim(
            HydrodynamicConditions(
                spatialGeneratorEstablished = true,
                diffusiveScalingEstablished = true,
                localEquilibriumEstablished = true,
                tightnessEstablished = true,
                spatialGeneratorWitness = "periodic lattice generator checked",
                diffusiveScalingWitness = "L²/K scaling checked",
                localEquilibriumWitness = "replacement estimate checked",
                tightnessWitness = "martingale compactness checked",
            ),
        )
        assertEquals(EvidenceKind.THEOREM_UNDER_ASSUMPTIONS, claim.kind)
    }
}
