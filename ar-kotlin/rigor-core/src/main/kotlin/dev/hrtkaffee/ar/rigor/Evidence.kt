package dev.hrtkaffee.ar.rigor

enum class EvidenceKind {
    EXACT_IDENTITY,
    THEOREM_UNDER_ASSUMPTIONS,
    NUMERICAL_CERTIFICATE,
    MONTE_CARLO_ESTIMATE,
    ILLUSTRATIVE_PARAMETERIZATION,
}

enum class AssumptionStatus {
    CHECKED,
    DECLARED,
    FAILED,
}

data class Assumption(
    val id: String,
    val statement: String,
    val status: AssumptionStatus,
    val witness: String,
)

data class NumericalDiagnostic(
    val name: String,
    val value: Double,
    val tolerance: Double,
) {
    init {
        require(value.isFinite() && value >= 0.0)
        require(tolerance.isFinite() && tolerance >= 0.0)
    }

    val passed: Boolean get() = value <= tolerance
}

data class Evidence<out T>(
    val value: T,
    val kind: EvidenceKind,
    val claim: String,
    val assumptions: List<Assumption> = emptyList(),
    val diagnostics: List<NumericalDiagnostic> = emptyList(),
) {
    init {
        require(claim.isNotBlank())
        require(assumptions.none { it.status == AssumptionStatus.FAILED }) {
            "A failed assumption cannot certify a claim"
        }
        if (kind == EvidenceKind.NUMERICAL_CERTIFICATE) {
            require(diagnostics.isNotEmpty()) { "Numerical claims require a residual or error indicator" }
        }
        if (kind == EvidenceKind.EXACT_IDENTITY) {
            require(diagnostics.isEmpty()) { "Exact identities must not depend on floating-point diagnostics" }
        }
    }
}

class AssumptionRegistry(assumptions: Iterable<Assumption> = emptyList()) {
    private val values = linkedMapOf<String, Assumption>()

    init {
        assumptions.forEach(::register)
    }

    fun register(assumption: Assumption) {
        require(values.putIfAbsent(assumption.id, assumption) == null) {
            "Assumption ${assumption.id} was registered twice"
        }
    }

    fun requireUsable(vararg ids: String): List<Assumption> = ids.map { id ->
        val assumption = values[id] ?: error("Unregistered assumption: $id")
        require(assumption.status != AssumptionStatus.FAILED) { assumption.witness }
        assumption
    }

    fun snapshot(): List<Assumption> = values.values.toList()
}

object AssumptionIds {
    const val FINITE_STATE = "A1_FINITE_STATE"
    const val BOUNDED_RATES = "A2_BOUNDED_RATES"
    const val IRREDUCIBLE = "A3_IRREDUCIBLE"
    const val POSITIVE_REVERSE_RATES = "A4_POSITIVE_REVERSE_RATES"
    const val DENSITY_DEPENDENT = "A5_DENSITY_DEPENDENT_SCALING"
    const val LOCALLY_LIPSCHITZ = "A6_LOCALLY_LIPSCHITZ_DRIFT"
    const val COMPACT_CONTAINMENT = "A7_COMPACT_CONTAINMENT"
    const val INITIAL_CONVERGENCE = "A8_INITIAL_CONVERGENCE"
    const val PRINCIPAL_EIGENPAIR = "A9_PRINCIPAL_EIGENPAIR"
    const val LOCAL_EQUILIBRIUM = "A10_LOCAL_EQUILIBRIUM"
    const val HYDRODYNAMIC_TIGHTNESS = "A11_HYDRODYNAMIC_TIGHTNESS"
    const val QUASI_STEADY_DHT = "A12_QUASI_STEADY_DHT"
    const val FIXED_T_RESERVOIR = "A13_FIXED_T_RESERVOIR"
    const val LOCAL_DETAILED_BALANCE = "A14_LOCAL_DETAILED_BALANCE"
    const val EXPONENTIAL_TIGHTNESS = "A15_EXPONENTIAL_TIGHTNESS"
    const val GOOD_RATE_FUNCTION = "A16_GOOD_RATE_FUNCTION"
    const val ACTION_COERCIVITY = "A17_ACTION_COERCIVITY"
    const val METASTABLE_SCALE_SEPARATION = "A18_METASTABLE_SCALE_SEPARATION"
    const val SPATIAL_GENERATOR = "A19_SPATIAL_GENERATOR"
    const val DIFFUSIVE_SCALING = "A20_DIFFUSIVE_SCALING"
    const val QUANTUM_FREE_ENERGY_INPUT = "A21_QUANTUM_FREE_ENERGY_INPUT"
    const val MARKOVIAN_QUANTUM_COARSE_GRAINING = "A22_MARKOVIAN_QUANTUM_COARSE_GRAINING"
    const val FINASTERIDE_COARSE_GRAINED_BINDING = "A23_FINASTERIDE_COARSE_GRAINED_BINDING"
    const val FINASTERIDE_POPULATION_PARAMETERS = "A24_FINASTERIDE_POPULATION_PARAMETERS"
    const val DUTASTERIDE_DUAL_ENZYME_INACTIVATION = "A25_DUTASTERIDE_DUAL_ENZYME_INACTIVATION"
    const val DUTASTERIDE_POPULATION_PARAMETERS = "A26_DUTASTERIDE_POPULATION_PARAMETERS"
}
