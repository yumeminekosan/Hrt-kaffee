package dev.hrtkaffee.ar.rigor.metastability

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.LegendreFenchelSolver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class SamplePathLargeDeviationConditions(
    val densityScalingEstablished: Boolean,
    val exponentialTightnessEstablished: Boolean,
    val goodRateFunctionEstablished: Boolean,
    val witnesses: Map<String, String>,
)

data class SamplePathLargeDeviationStatement(
    val speed: String,
    val action: String,
    val topology: String,
)

fun samplePathLargeDeviationClaim(
    conditions: SamplePathLargeDeviationConditions,
): Evidence<SamplePathLargeDeviationStatement> {
    fun assumption(id: String, statement: String, checked: Boolean): Assumption = Assumption(
        id = id,
        statement = statement,
        status = if (checked) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
        witness = conditions.witnesses[id] ?: "No analytical witness registered.",
    )
    val assumptions = listOf(
        assumption(
            AssumptionIds.DENSITY_DEPENDENT,
            "The microscopic family has the declared density-dependent scaling.",
            conditions.densityScalingEstablished,
        ),
        assumption(
            AssumptionIds.EXPONENTIAL_TIGHTNESS,
            "The path laws are exponentially tight on the selected interval.",
            conditions.exponentialTightnessEstablished,
        ),
        assumption(
            AssumptionIds.GOOD_RATE_FUNCTION,
            "The local Legendre transform generates a lower-semicontinuous good path rate function.",
            conditions.goodRateFunctionEstablished,
        ),
    )
    require(assumptions.all { it.status == AssumptionStatus.CHECKED }) {
        "A sample-path LDP cannot be certified while a named analytical assumption is missing"
    }
    return Evidence(
        value = SamplePathLargeDeviationStatement(
            speed = "N",
            action = "I[φ]=∫ L(φ(t), φ̇(t))dt",
            topology = "càdlàg path space on the declared finite time interval",
        ),
        kind = EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
        claim = "The density process obeys the sample-path large-deviation principle with the jump-process action.",
        assumptions = assumptions,
    )
}

data class QuasipotentialStatement(
    val definition: String,
    val exitScale: String,
)

fun quasipotentialClaim(
    pathLdp: Evidence<SamplePathLargeDeviationStatement>,
    actionCoercivityEstablished: Boolean,
    witness: String,
): Evidence<QuasipotentialStatement> {
    require(pathLdp.kind == EvidenceKind.THEOREM_UNDER_ASSUMPTIONS)
    val coercivity = Assumption(
        id = AssumptionIds.ACTION_COERCIVITY,
        statement = "The action is coercive enough for the relevant endpoint/time infimum and basin problem.",
        status = if (actionCoercivityEstablished) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
        witness = witness,
    )
    require(coercivity.status == AssumptionStatus.CHECKED) {
        "A quasipotential theorem requires a compactness/coercivity witness"
    }
    return Evidence(
        value = QuasipotentialStatement(
            definition = "V(a,b)=inf_{T>0} inf_{φ(0)=a,φ(T)=b} I_T[φ]",
            exitScale = "E τ_B ≍ exp(N inf_{∂B}V) only after metastable hypotheses are checked",
        ),
        kind = EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
        claim = "The quasipotential is the time-and-path infimum of the sample-path action.",
        assumptions = pathLdp.assumptions + coercivity,
    )
}

data class ActionPathPoint(val time: Double, val density: DoubleArray)

data class PiecewiseLinearAction(
    val value: Double,
    val maximumDualResidual: Double,
)

object PathAction {
    fun evaluate(
        model: DensityDependentModel,
        points: List<ActionPathPoint>,
        dualTolerance: Double = 1e-9,
    ): Evidence<PiecewiseLinearAction> {
        require(points.size >= 2)
        require(points.all { it.time.isFinite() && it.density.size == model.dimension })
        require(points.zipWithNext().all { (left, right) -> right.time > left.time })
        require(dualTolerance > 0.0)

        var action = 0.0
        var maximumResidual = 0.0
        points.zipWithNext().forEach { (left, right) ->
            val duration = right.time - left.time
            val midpoint = DoubleArray(model.dimension) { coordinate ->
                (left.density[coordinate] + right.density[coordinate]) / 2.0
            }
            val velocity = DoubleArray(model.dimension) { coordinate ->
                (right.density[coordinate] - left.density[coordinate]) / duration
            }
            val dual = LegendreFenchelSolver.solve(
                model = model,
                density = midpoint,
                velocity = velocity,
                tolerance = dualTolerance,
            )
            maximumResidual = max(maximumResidual, dual.value.gradientResidual)
            action += duration * dual.value.value
        }
        val negativeMagnitude = max(0.0, -action)
        return Evidence(
            value = PiecewiseLinearAction(action, maximumResidual),
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The piecewise-linear path action is a quadrature/dual-solver value, not a proof of a quasipotential.",
            diagnostics = listOf(
                NumericalDiagnostic("maximum Legendre dual residual", maximumResidual, dualTolerance),
                NumericalDiagnostic("negative action magnitude", negativeMagnitude, dualTolerance),
            ),
        )
    }
}

data class MinimumActionPathResult(
    val points: List<ActionPathPoint>,
    val action: Double,
    val stationarityResidual: Double,
    val iterations: Int,
    val converged: Boolean,
)

/**
 * Fixed-duration, fixed-mesh steepest descent. Passing its residual certifies only a
 * stationary discrete candidate; it does not certify the global path/time infimum.
 */
object FixedTimeMinimumActionSolver {
    fun solve(
        model: DensityDependentModel,
        start: DoubleArray,
        end: DoubleArray,
        duration: Double,
        segments: Int,
        stationarityTolerance: Double = 1e-6,
        dualTolerance: Double = 1e-9,
        maximumIterations: Int = 200,
        finiteDifferenceStep: Double = 1e-5,
        domain: (DoubleArray) -> Boolean = { point -> point.all { it >= 0.0 } },
    ): Evidence<MinimumActionPathResult> {
        require(start.size == model.dimension && end.size == model.dimension)
        require(start.all(Double::isFinite) && end.all(Double::isFinite))
        require(duration.isFinite() && duration > 0.0)
        require(segments >= 1 && maximumIterations > 0)
        require(stationarityTolerance > 0.0 && dualTolerance > 0.0 && finiteDifferenceStep > 0.0)
        require(domain(start) && domain(end))

        val states = MutableList(segments + 1) { index ->
            val fraction = index.toDouble() / segments
            DoubleArray(model.dimension) { coordinate ->
                start[coordinate] + fraction * (end[coordinate] - start[coordinate])
            }
        }
        fun points(): List<ActionPathPoint> = states.mapIndexed { index, state ->
            ActionPathPoint(duration * index / segments, state.copyOf())
        }
        fun objective(): Double = PathAction.evaluate(model, points(), dualTolerance).value.value

        var action = objective()
        var iterations = 0
        var gradientNorm = Double.POSITIVE_INFINITY
        var converged = segments == 1

        while (!converged && iterations < maximumIterations) {
            val gradient = Array(segments - 1) { DoubleArray(model.dimension) }
            for (pointIndex in 1 until segments) {
                for (coordinate in 0 until model.dimension) {
                    val original = states[pointIndex][coordinate]
                    states[pointIndex][coordinate] = original + finiteDifferenceStep
                    require(domain(states[pointIndex])) { "Finite-difference probe left the declared domain" }
                    val plus = objective()
                    states[pointIndex][coordinate] = original - finiteDifferenceStep
                    require(domain(states[pointIndex])) { "Finite-difference probe left the declared domain" }
                    val minus = objective()
                    states[pointIndex][coordinate] = original
                    gradient[pointIndex - 1][coordinate] =
                        (plus - minus) / (2.0 * finiteDifferenceStep)
                }
            }
            gradientNorm = sqrt(gradient.sumOf { row -> row.sumOf { it * it } })
            if (gradientNorm <= stationarityTolerance) {
                converged = true
                break
            }

            val oldStates = states.map(DoubleArray::copyOf)
            var step = 1.0
            var accepted = false
            while (step >= 1e-8) {
                for (pointIndex in 1 until segments) {
                    for (coordinate in 0 until model.dimension) {
                        states[pointIndex][coordinate] = oldStates[pointIndex][coordinate] -
                            step * gradient[pointIndex - 1][coordinate]
                    }
                }
                if (states.drop(1).dropLast(1).all(domain)) {
                    val candidate = objective()
                    if (candidate.isFinite() && candidate <= action) {
                        action = candidate
                        accepted = true
                        break
                    }
                }
                for (index in states.indices) states[index] = oldStates[index].copyOf()
                step /= 2.0
            }
            if (!accepted) break
            iterations += 1
        }

        if (segments == 1) gradientNorm = 0.0
        val finalAction = PathAction.evaluate(model, points(), dualTolerance)
        val result = MinimumActionPathResult(
            points = points(),
            action = finalAction.value.value,
            stationarityResidual = gradientNorm,
            iterations = iterations,
            converged = converged,
        )
        return Evidence(
            value = result,
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "A fixed-time discrete minimum-action candidate is reported with stationarity and dual residuals.",
            diagnostics = listOf(
                NumericalDiagnostic("discrete action stationarity residual", gradientNorm, stationarityTolerance),
                NumericalDiagnostic(
                    "minimum-action maximum dual residual",
                    finalAction.value.maximumDualResidual,
                    dualTolerance,
                ),
                NumericalDiagnostic("negative minimum-action magnitude", max(0.0, -result.action), dualTolerance),
            ),
        )
    }
}

data class MetastableExitStatement(val asymptoticScale: String)

fun metastableExitClaim(
    quasipotential: Evidence<QuasipotentialStatement>,
    scaleSeparationEstablished: Boolean,
    witness: String,
): Evidence<MetastableExitStatement> {
    require(quasipotential.kind == EvidenceKind.THEOREM_UNDER_ASSUMPTIONS)
    val separation = Assumption(
        id = AssumptionIds.METASTABLE_SCALE_SEPARATION,
        statement = "Mixing inside the basin is asymptotically faster than escape from it.",
        status = if (scaleSeparationEstablished) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
        witness = witness,
    )
    require(separation.status == AssumptionStatus.CHECKED) {
        "Large mean hitting time alone does not certify metastability"
    }
    return Evidence(
        value = MetastableExitStatement("log Eτ/N → inf_{boundary} V"),
        kind = EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
        claim = "Metastable exit scaling follows from the quasipotential only with an internal-mixing/escape separation.",
        assumptions = quasipotential.assumptions + separation,
    )
}
