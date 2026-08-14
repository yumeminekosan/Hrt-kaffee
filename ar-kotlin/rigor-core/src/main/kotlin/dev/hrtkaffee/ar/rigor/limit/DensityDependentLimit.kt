package dev.hrtkaffee.ar.rigor.limit

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class DensityReaction(
    stoichiometry: IntArray,
    val label: String,
    val beta: (DoubleArray) -> Double,
) {
    val stoichiometry: IntArray = stoichiometry.copyOf()

    init {
        require(this.stoichiometry.isNotEmpty())
        require(label.isNotBlank())
    }
}

class DensityDependentModel(
    val dimension: Int,
    val reactions: List<DensityReaction>,
) {
    init {
        require(dimension > 0 && reactions.isNotEmpty())
        require(reactions.all { it.stoichiometry.size == dimension })
    }

    fun drift(density: DoubleArray): DoubleArray {
        require(density.size == dimension)
        val drift = DoubleArray(dimension)
        reactions.forEach { reaction ->
            val intensity = reaction.beta(density.copyOf())
            require(intensity.isFinite() && intensity >= 0.0) {
                "Macroscopic jump intensities must be finite and nonnegative"
            }
            for (coordinate in 0 until dimension) {
                drift[coordinate] += reaction.stoichiometry[coordinate] * intensity
            }
        }
        return drift
    }

    /** H(x,p)=Σr βr(x)(exp(p·νr)−1), derived from the same jump channels. */
    fun hamiltonian(density: DoubleArray, momentum: DoubleArray): Double {
        require(density.size == dimension && momentum.size == dimension)
        return reactions.sumOf { reaction ->
            val intensity = reaction.beta(density.copyOf())
            require(intensity.isFinite() && intensity >= 0.0)
            val pairing = momentum.indices.sumOf { index ->
                momentum[index] * reaction.stoichiometry[index]
            }
            intensity * (exp(pairing) - 1.0)
        }
    }

    fun hamiltonianGradient(density: DoubleArray, momentum: DoubleArray): DoubleArray {
        require(density.size == dimension && momentum.size == dimension)
        val gradient = DoubleArray(dimension)
        reactions.forEach { reaction ->
            val intensity = reaction.beta(density.copyOf())
            require(intensity.isFinite() && intensity >= 0.0)
            val pairing = momentum.indices.sumOf { index ->
                momentum[index] * reaction.stoichiometry[index]
            }
            val weighted = intensity * exp(pairing)
            for (coordinate in 0 until dimension) {
                gradient[coordinate] += weighted * reaction.stoichiometry[coordinate]
            }
        }
        return gradient
    }

    fun hamiltonianHessian(density: DoubleArray, momentum: DoubleArray): Array<DoubleArray> {
        require(density.size == dimension && momentum.size == dimension)
        val hessian = Array(dimension) { DoubleArray(dimension) }
        reactions.forEach { reaction ->
            val intensity = reaction.beta(density.copyOf())
            require(intensity.isFinite() && intensity >= 0.0)
            val pairing = momentum.indices.sumOf { index ->
                momentum[index] * reaction.stoichiometry[index]
            }
            val weighted = intensity * exp(pairing)
            for (row in 0 until dimension) {
                for (column in 0 until dimension) {
                    hessian[row][column] += weighted *
                        reaction.stoichiometry[row] * reaction.stoichiometry[column]
                }
            }
        }
        return hessian
    }
}

data class KurtzConditions(
    val densityDependentScalingChecked: Boolean,
    val locallyLipschitzRatesChecked: Boolean,
    val compactContainmentEstablished: Boolean,
    val initialConvergenceEstablished: Boolean,
    val witnesses: Map<String, String>,
) {
    fun assumptions(): List<Assumption> = listOf(
        assumption(
            AssumptionIds.DENSITY_DEPENDENT,
            "The microscopic rates have the form Nβr(X/N)+o(N) on the selected domain.",
            densityDependentScalingChecked,
        ),
        assumption(
            AssumptionIds.LOCALLY_LIPSCHITZ,
            "Every βr and the induced drift are locally Lipschitz on the selected domain.",
            locallyLipschitzRatesChecked,
        ),
        assumption(
            AssumptionIds.COMPACT_CONTAINMENT,
            "The scaled paths satisfy compact containment on the requested time interval.",
            compactContainmentEstablished,
        ),
        assumption(
            AssumptionIds.INITIAL_CONVERGENCE,
            "The scaled initial conditions converge in probability.",
            initialConvergenceEstablished,
        ),
    )

    private fun assumption(id: String, statement: String, checked: Boolean): Assumption = Assumption(
        id = id,
        statement = statement,
        status = if (checked) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
        witness = witnesses[id] ?: "No witness registered.",
    )
}

data class KurtzLimitStatement(
    val driftFormula: String,
    val convergenceMode: String,
)

fun kurtzFluidLimitClaim(conditions: KurtzConditions): Evidence<KurtzLimitStatement> {
    val assumptions = conditions.assumptions()
    require(assumptions.all { it.status == AssumptionStatus.CHECKED }) {
        "Kurtz convergence cannot be certified while a named assumption is unproved"
    }
    return Evidence(
        value = KurtzLimitStatement(
            driftFormula = "b(x)=Σr νr βr(x)",
            convergenceMode = "uniform on compact time intervals, in probability",
        ),
        kind = EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
        claim = "The density-dependent jump process converges to the reaction-rate ODE.",
        assumptions = assumptions,
    )
}

data class FluidPoint(val time: Double, val density: DoubleArray)

data class FluidTrajectory(val points: List<FluidPoint>)

object FluidIntegrator {
    fun integrateWithStepDoubling(
        model: DensityDependentModel,
        initial: DoubleArray,
        horizon: Double,
        coarseSteps: Int,
        tolerance: Double,
    ): Evidence<FluidTrajectory> {
        require(initial.size == model.dimension)
        require(horizon.isFinite() && horizon > 0.0)
        require(coarseSteps > 0 && tolerance > 0.0)
        val coarse = rk4(model, initial, horizon, coarseSteps)
        val fine = rk4(model, initial, horizon, coarseSteps * 2)
        val coarseFinal = coarse.points.last().density
        val fineFinal = fine.points.last().density
        val stepDoublingIndicator = coarseFinal.indices.maxOf { index ->
            abs(coarseFinal[index] - fineFinal[index]) / 15.0
        }
        return Evidence(
            value = fine,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "RK4 output carries a step-doubling discretization indicator; it is not the Kurtz proof.",
            diagnostics = listOf(
                NumericalDiagnostic("RK4 step-doubling indicator", stepDoublingIndicator, tolerance),
            ),
        )
    }

    private fun rk4(
        model: DensityDependentModel,
        initial: DoubleArray,
        horizon: Double,
        steps: Int,
    ): FluidTrajectory {
        val step = horizon / steps
        var state = initial.copyOf()
        val points = mutableListOf(FluidPoint(0.0, state.copyOf()))
        repeat(steps) { stepIndex ->
            val k1 = model.drift(state)
            val k2 = model.drift(addScaled(state, k1, step / 2.0))
            val k3 = model.drift(addScaled(state, k2, step / 2.0))
            val k4 = model.drift(addScaled(state, k3, step))
            state = DoubleArray(model.dimension) { coordinate ->
                state[coordinate] + step *
                    (k1[coordinate] + 2.0 * k2[coordinate] + 2.0 * k3[coordinate] + k4[coordinate]) / 6.0
            }
            require(state.all(Double::isFinite))
            points += FluidPoint((stepIndex + 1) * step, state.copyOf())
        }
        return FluidTrajectory(points)
    }

    private fun addScaled(left: DoubleArray, right: DoubleArray, scale: Double): DoubleArray =
        DoubleArray(left.size) { index -> left[index] + scale * right[index] }
}

data class LegendreFenchelResult(
    val value: Double,
    val optimizer: DoubleArray,
    val gradientResidual: Double,
    val iterations: Int,
    val converged: Boolean,
)

object LegendreFenchelSolver {
    fun solve(
        model: DensityDependentModel,
        density: DoubleArray,
        velocity: DoubleArray,
        tolerance: Double = 1e-10,
        maximumIterations: Int = 80,
    ): Evidence<LegendreFenchelResult> {
        require(density.size == model.dimension && velocity.size == model.dimension)
        require(tolerance > 0.0 && maximumIterations > 0)
        var momentum = DoubleArray(model.dimension)
        var residual = Double.POSITIVE_INFINITY
        var iterations = 0
        var converged = false

        while (iterations < maximumIterations) {
            val gradient = model.hamiltonianGradient(density, momentum)
            val defect = DoubleArray(model.dimension) { index -> velocity[index] - gradient[index] }
            residual = euclideanNorm(defect)
            if (residual <= tolerance) {
                converged = true
                break
            }
            val newtonDirection = solveLinearSystem(
                model.hamiltonianHessian(density, momentum),
                defect,
            ) ?: break

            val oldObjective = dualObjective(model, density, velocity, momentum)
            var scale = 1.0
            var accepted = false
            while (scale >= 1e-8) {
                val candidate = DoubleArray(model.dimension) { index ->
                    momentum[index] + scale * newtonDirection[index]
                }
                val candidateObjective = dualObjective(model, density, velocity, candidate)
                if (candidateObjective.isFinite() && candidateObjective >= oldObjective) {
                    momentum = candidate
                    accepted = true
                    break
                }
                scale /= 2.0
            }
            if (!accepted) break
            iterations += 1
        }

        val value = dualObjective(model, density, velocity, momentum)
        val result = LegendreFenchelResult(value, momentum.copyOf(), residual, iterations, converged)
        return Evidence(
            value = result,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "L(x,v)=sup_p[p·v−H(x,p)] is evaluated by damped Newton iteration.",
            diagnostics = listOf(
                NumericalDiagnostic("Legendre dual gradient residual", residual, tolerance),
            ),
        )
    }

    private fun dualObjective(
        model: DensityDependentModel,
        density: DoubleArray,
        velocity: DoubleArray,
        momentum: DoubleArray,
    ): Double = momentum.indices.sumOf { momentum[it] * velocity[it] } -
        model.hamiltonian(density, momentum)

    private fun solveLinearSystem(matrix: Array<DoubleArray>, right: DoubleArray): DoubleArray? {
        val size = right.size
        val augmented = Array(size) { row -> DoubleArray(size + 1) { column ->
            if (column == size) right[row] else matrix[row][column]
        } }
        for (column in 0 until size) {
            val pivot = (column until size).maxBy { abs(augmented[it][column]) }
            if (abs(augmented[pivot][column]) < 1e-14) return null
            val temporary = augmented[column]
            augmented[column] = augmented[pivot]
            augmented[pivot] = temporary
            val pivotValue = augmented[column][column]
            for (entry in column until size + 1) augmented[column][entry] /= pivotValue
            for (row in 0 until size) {
                if (row == column) continue
                val factor = augmented[row][column]
                for (entry in column until size + 1) {
                    augmented[row][entry] -= factor * augmented[column][entry]
                }
            }
        }
        return DoubleArray(size) { augmented[it][size] }
    }

    private fun euclideanNorm(vector: DoubleArray): Double =
        sqrt(vector.sumOf { it * it })
}

object HamiltonJacobi {
    fun residual(
        timeDerivative: Double,
        density: DoubleArray,
        valueGradient: DoubleArray,
        model: DensityDependentModel,
    ): Double = timeDerivative + model.hamiltonian(density, valueGradient)
}
