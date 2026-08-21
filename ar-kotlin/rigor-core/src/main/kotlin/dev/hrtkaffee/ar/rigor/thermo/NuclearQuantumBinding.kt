package dev.hrtkaffee.ar.rigor.thermo

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.Reaction
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

data class ThermalDeBroglieScale(
    val massDalton: Double,
    val temperatureKelvin: Double,
    val wavelengthPicometres: Double,
) {
    init {
        require(massDalton.isFinite() && massDalton > 0.0)
        require(temperatureKelvin.isFinite() && temperatureKelvin > 0.0)
        require(wavelengthPicometres.isFinite() && wavelengthPicometres > 0.0)
    }
}

/**
 * Thermal de Broglie convention λ_th=h/sqrt(2πmk_B T).
 *
 * This is a length-scale diagnostic only. It cannot, by itself, determine a
 * binding free energy, an activation barrier, or a reaction-rate multiplier.
 */
object ThermalDeBroglie {
    private const val PLANCK_JOULE_SECONDS = 6.626_070_15e-34
    private const val BOLTZMANN_JOULES_PER_KELVIN = 1.380_649e-23
    private const val ATOMIC_MASS_KILOGRAMS = 1.660_539_068_92e-27
    private const val PICOMETRES_PER_METRE = 1.0e12

    fun evaluate(massDalton: Double, temperatureKelvin: Double): ThermalDeBroglieScale {
        require(massDalton.isFinite() && massDalton > 0.0)
        require(temperatureKelvin.isFinite() && temperatureKelvin > 0.0)
        val massKilograms = massDalton * ATOMIC_MASS_KILOGRAMS
        val wavelengthMetres = PLANCK_JOULE_SECONDS /
            sqrt(2.0 * PI * massKilograms * BOLTZMANN_JOULES_PER_KELVIN * temperatureKelvin)
        return ThermalDeBroglieScale(
            massDalton = massDalton,
            temperatureKelvin = temperatureKelvin,
            wavelengthPicometres = wavelengthMetres * PICOMETRES_PER_METRE,
        )
    }
}

/**
 * Externally identified nuclear-quantum corrections relative to a declared
 * classical reference model. These values must come from a method such as
 * path-integral free-energy sampling or an audited quantum-rate calculation;
 * they are never inferred from λ_th alone.
 */
data class QuantumFreeEnergyShift(
    val temperatureKelvin: Double,
    val bindingFreeEnergyShiftKilojoulesPerMole: Double,
    val forwardActivationFreeEnergyShiftKilojoulesPerMole: Double,
    val provenance: String,
) {
    init {
        require(temperatureKelvin.isFinite() && temperatureKelvin > 0.0)
        require(bindingFreeEnergyShiftKilojoulesPerMole.isFinite())
        require(forwardActivationFreeEnergyShiftKilojoulesPerMole.isFinite())
        require(provenance.isNotBlank())
    }
}

data class QuantumRateProjection(
    val bindingEquilibriumMultiplier: Double,
    val forwardBindingRateMultiplier: Double,
    val reverseUnbindingRateMultiplier: Double,
)

object QuantumBindingBridge {
    private const val GAS_CONSTANT_KILOJOULES_PER_MOLE_KELVIN = 0.008_314_462_618_153_24

    /**
     * Projects supplied ΔΔG values into thermodynamic/rate multipliers.
     * The reverse factor follows from K=k_forward/k_reverse, while the forward
     * factor uses a transition-state-style exponential correction.
     */
    fun project(shift: QuantumFreeEnergyShift): Evidence<QuantumRateProjection> {
        val thermalEnergy = GAS_CONSTANT_KILOJOULES_PER_MOLE_KELVIN * shift.temperatureKelvin
        val equilibriumMultiplier = exp(-shift.bindingFreeEnergyShiftKilojoulesPerMole / thermalEnergy)
        val forwardMultiplier = exp(-shift.forwardActivationFreeEnergyShiftKilojoulesPerMole / thermalEnergy)
        val reverseMultiplier = forwardMultiplier / equilibriumMultiplier
        require(listOf(equilibriumMultiplier, forwardMultiplier, reverseMultiplier).all {
            it.isFinite() && it > 0.0
        })

        return Evidence(
            value = QuantumRateProjection(
                bindingEquilibriumMultiplier = equilibriumMultiplier,
                forwardBindingRateMultiplier = forwardMultiplier,
                reverseUnbindingRateMultiplier = reverseMultiplier,
            ),
            kind = EvidenceKind.ILLUSTRATIVE_PARAMETERIZATION,
            claim = "Supplied nuclear-quantum free-energy shifts are projected into CTMC rate multipliers; the thermal wavelength alone is not used as a binding model.",
            assumptions = listOf(
                Assumption(
                    id = AssumptionIds.QUANTUM_FREE_ENERGY_INPUT,
                    statement = "The binding and activation free-energy shifts were identified outside this CTMC.",
                    status = AssumptionStatus.DECLARED,
                    witness = shift.provenance,
                ),
                Assumption(
                    id = AssumptionIds.MARKOVIAN_QUANTUM_COARSE_GRAINING,
                    statement = "Eliminated electronic and nuclear quantum degrees of freedom leave a memoryless jump model at the selected state resolution.",
                    status = AssumptionStatus.DECLARED,
                    witness = "The coarse-graining timescale and recrossing assumptions must be checked for the selected molecular system.",
                ),
            ),
        )
    }
}

/**
 * Exact rational multipliers are the only quantum inputs admitted into the
 * exact generator. Converting a floating projection to these factors is an
 * explicit calibration decision with provenance, never an implicit cast.
 */
data class ExactQuantumRateCalibration(
    val reactionRateMultipliers: Map<String, Rational>,
    val provenance: String,
) {
    init {
        require(reactionRateMultipliers.isNotEmpty())
        require(reactionRateMultipliers.keys.all { it.isNotBlank() })
        require(reactionRateMultipliers.values.all { it > Rational.ZERO })
        require(provenance.isNotBlank())
    }

    fun applyTo(reactions: List<Reaction>): List<Reaction> {
        val knownIds = reactions.map(Reaction::id).toSet()
        require(reactionRateMultipliers.keys.all(knownIds::contains)) {
            "Quantum calibration named a reaction outside the selected network"
        }
        return reactions.map { reaction ->
            reaction.copy(
                rate = reaction.rate * (reactionRateMultipliers[reaction.id] ?: Rational.ONE),
            )
        }
    }
}
