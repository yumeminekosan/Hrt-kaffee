package dev.hrtkaffee.ar.web.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MolecularBindingTheoryMapTest {
    @Test
    fun everyRequestedArrowIsRepresentedDirectly() {
        val requested = setOf(
            TheoryNodeId.DISCRETE_CTMC to TheoryNodeId.JUMP_STRUCTURE,
            TheoryNodeId.JUMP_STRUCTURE to TheoryNodeId.FLUID_LIMIT,
            TheoryNodeId.JUMP_STRUCTURE to TheoryNodeId.NONLINEAR_GENERATOR,
            TheoryNodeId.NONLINEAR_GENERATOR to TheoryNodeId.HAMILTONIAN_HJ,
            TheoryNodeId.HAMILTONIAN_HJ to TheoryNodeId.METASTABILITY,
            TheoryNodeId.JUMP_STRUCTURE to TheoryNodeId.TILTED_OPERATOR,
            TheoryNodeId.TILTED_OPERATOR to TheoryNodeId.DOOB_TRANSFORM,
            TheoryNodeId.DOOB_TRANSFORM to TheoryNodeId.DRIVEN_GILLESPIE,
            TheoryNodeId.DISCRETE_CTMC to TheoryNodeId.SPATIAL_PARTICLES,
            TheoryNodeId.SPATIAL_PARTICLES to TheoryNodeId.HYDRODYNAMIC_PDE,
            TheoryNodeId.DISCRETE_CTMC to TheoryNodeId.CHAIN_COMPLEX,
        )
        val actual = MolecularBindingTheoryMap.edges.map { it.source to it.target }.toSet()

        assertTrue(actual.containsAll(requested))
    }

    @Test
    fun everyRequestedClassicalStageIsReachableFromTheCtmc() {
        val targets = TheoryNodeId.entries - setOf(
            TheoryNodeId.QUANTUM_SCALE,
            TheoryNodeId.QUANTUM_CALIBRATION,
            TheoryNodeId.DISCRETE_CTMC,
        )

        targets.forEach { target ->
            assertTrue(
                MolecularBindingTheoryMap.isReachable(TheoryNodeId.DISCRETE_CTMC, target),
                "$target must remain connected to the microscopic CTMC",
            )
        }
    }

    @Test
    fun quantumScaleCannotBypassFreeEnergyCalibration() {
        val edges = MolecularBindingTheoryMap.edges
        assertTrue(edges.any {
            it.source == TheoryNodeId.QUANTUM_SCALE && it.target == TheoryNodeId.QUANTUM_CALIBRATION
        })
        assertTrue(edges.any {
            it.source == TheoryNodeId.QUANTUM_CALIBRATION && it.target == TheoryNodeId.DISCRETE_CTMC
        })
        assertFalse(edges.any {
            it.source == TheoryNodeId.QUANTUM_SCALE && it.target == TheoryNodeId.DISCRETE_CTMC
        })
    }

    @Test
    fun webThermalWavelengthUsesInverseSquareRootScaling() {
        val hydrogen = WebThermalDeBroglie.evaluate(1.0, 300.0)
        val fourTimesMass = WebThermalDeBroglie.evaluate(4.0, 300.0)

        assertEquals(hydrogen.wavelengthPicometres / 2.0, fourTimesMass.wavelengthPicometres, 1e-12)
    }
}
