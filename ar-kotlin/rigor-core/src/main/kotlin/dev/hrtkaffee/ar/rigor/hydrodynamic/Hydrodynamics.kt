package dev.hrtkaffee.ar.rigor.hydrodynamic

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import kotlin.math.abs
import kotlin.math.max

enum class BoundaryCondition {
    PERIODIC,
    NO_FLUX,
}

class SpatialDensity(initial: Array<DoubleArray>) {
    private val values: Array<DoubleArray> = Array(initial.size) { initial[it].copyOf() }
    val speciesCount: Int = values.size
    val cellCount: Int = values.firstOrNull()?.size ?: 0

    init {
        require(speciesCount > 0 && cellCount > 1)
        require(values.all { it.size == cellCount })
        require(values.all { row -> row.all(Double::isFinite) })
    }

    operator fun get(species: Int, cell: Int): Double = values[species][cell]

    fun cell(cell: Int): DoubleArray = DoubleArray(speciesCount) { species -> values[species][cell] }

    fun toArrays(): Array<DoubleArray> = Array(speciesCount) { values[it].copyOf() }
}

data class SpatialStepResult(
    val density: SpatialDensity,
    val cflRatio: Double,
    val conservationResidual: Double,
    val negativityMagnitude: Double,
)

class ConservativeFiniteVolume1D(
    private val cellWidth: Double,
    diffusion: DoubleArray,
    private val boundaryCondition: BoundaryCondition,
    private val reaction: (DoubleArray) -> DoubleArray,
    conservationWeights: DoubleArray,
) {
    private val diffusion: DoubleArray = diffusion.copyOf()
    private val conservationWeights: DoubleArray = conservationWeights.copyOf()

    init {
        require(cellWidth.isFinite() && cellWidth > 0.0)
        require(this.diffusion.isNotEmpty() && this.diffusion.all { it.isFinite() && it >= 0.0 })
        require(this.conservationWeights.size == this.diffusion.size)
        require(this.conservationWeights.all(Double::isFinite))
    }

    /** Explicit finite-volume diffusion plus local reaction, with a stated one-dimensional CFL check. */
    fun step(
        current: SpatialDensity,
        timeStep: Double,
        residualTolerance: Double = 1e-10,
    ): Evidence<SpatialStepResult> {
        require(current.speciesCount == diffusion.size)
        require(timeStep.isFinite() && timeStep > 0.0)
        require(residualTolerance > 0.0)
        val maxDiffusion = diffusion.maxOrNull() ?: 0.0
        val cflRatio = if (maxDiffusion == 0.0) 0.0 else 2.0 * maxDiffusion * timeStep / (cellWidth * cellWidth)
        require(cflRatio <= 1.0) { "Explicit diffusion CFL condition violated: ratio=$cflRatio" }

        val next = current.toArrays()
        for (cell in 0 until current.cellCount) {
            val localReaction = reaction(current.cell(cell))
            require(localReaction.size == current.speciesCount && localReaction.all(Double::isFinite))
            for (species in 0 until current.speciesCount) {
                val left = neighbor(current, species, cell, -1)
                val center = current[species, cell]
                val right = neighbor(current, species, cell, 1)
                val laplacian = (left - 2.0 * center + right) / (cellWidth * cellWidth)
                next[species][cell] = center + timeStep *
                    (diffusion[species] * laplacian + localReaction[species])
            }
        }

        val nextDensity = SpatialDensity(next)
        val before = conservedQuantity(current)
        val after = conservedQuantity(nextDensity)
        val conservationResidual = abs(after - before) / max(1.0, abs(before))
        val negativityMagnitude = next.maxOf { row ->
            row.maxOf { value -> if (value < 0.0) -value else 0.0 }
        }
        return Evidence(
            value = SpatialStepResult(nextDensity, cflRatio, conservationResidual, negativityMagnitude),
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The finite-volume step reports CFL, positivity, and weighted conservation diagnostics.",
            diagnostics = listOf(
                NumericalDiagnostic("weighted conservation residual", conservationResidual, residualTolerance),
                NumericalDiagnostic("negative-density magnitude", negativityMagnitude, residualTolerance),
            ),
        )
    }

    private fun neighbor(
        density: SpatialDensity,
        species: Int,
        cell: Int,
        offset: Int,
    ): Double {
        val candidate = cell + offset
        return when {
            candidate in 0 until density.cellCount -> density[species, candidate]
            boundaryCondition == BoundaryCondition.PERIODIC -> {
                val wrapped = (candidate + density.cellCount) % density.cellCount
                density[species, wrapped]
            }
            else -> density[species, cell]
        }
    }

    private fun conservedQuantity(density: SpatialDensity): Double =
        (0 until density.cellCount).sumOf { cell ->
            (0 until density.speciesCount).sumOf { species ->
                conservationWeights[species] * density[species, cell] * cellWidth
            }
        }
}

data class HydrodynamicConditions(
    val spatialGeneratorEstablished: Boolean,
    val diffusiveScalingEstablished: Boolean,
    val localEquilibriumEstablished: Boolean,
    val tightnessEstablished: Boolean,
    val spatialGeneratorWitness: String,
    val diffusiveScalingWitness: String,
    val localEquilibriumWitness: String,
    val tightnessWitness: String,
)

data class HydrodynamicLimitStatement(
    val pde: String,
    val topology: String,
)

fun hydrodynamicLimitClaim(conditions: HydrodynamicConditions): Evidence<HydrodynamicLimitStatement> {
    val assumptions = listOf(
        Assumption(
            AssumptionIds.SPATIAL_GENERATOR,
            "A microscopic spatial interacting-particle generator has been specified.",
            if (conditions.spatialGeneratorEstablished) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
            conditions.spatialGeneratorWitness,
        ),
        Assumption(
            AssumptionIds.DIFFUSIVE_SCALING,
            "Migration uses the declared L² diffusive scaling while local populations use the K scaling.",
            if (conditions.diffusiveScalingEstablished) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
            conditions.diffusiveScalingWitness,
        ),
        Assumption(
            AssumptionIds.LOCAL_EQUILIBRIUM,
            "Local equilibrium/replacement estimates hold for the spatial particle system.",
            if (conditions.localEquilibriumEstablished) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
            conditions.localEquilibriumWitness,
        ),
        Assumption(
            AssumptionIds.HYDRODYNAMIC_TIGHTNESS,
            "The empirical density fields are tight and all limit points solve the weak PDE.",
            if (conditions.tightnessEstablished) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
            conditions.tightnessWitness,
        ),
    )
    require(assumptions.all { it.status == AssumptionStatus.CHECKED }) {
        "A hydrodynamic limit is not certified by a PDE discretization alone"
    }
    return Evidence(
        value = HydrodynamicLimitStatement(
            pde = "∂tρ = DΔρ + Σr νr βr(ρ)",
            topology = "weak empirical-density convergence as lattice size L and local scale K tend to infinity",
        ),
        kind = EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
        claim = "The spatial particle system converges to the reaction–diffusion PDE.",
        assumptions = assumptions,
    )
}
