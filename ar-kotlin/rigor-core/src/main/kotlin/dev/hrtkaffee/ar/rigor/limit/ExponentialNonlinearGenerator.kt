package dev.hrtkaffee.ar.rigor.limit

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import kotlin.math.abs
import kotlin.math.exp

data class ExponentialGeneratorComparison(
    val systemSize: Int,
    val finiteNonlinearGenerator: Double,
    val limitingHamiltonian: Double,
    val exactScaledIntensityErrors: List<Rational>,
)

/**
 * For the linear exponential test f_p(x)=p·x, evaluate
 *
 * H_N f_p(x)=N⁻¹ exp(−Nf_p(x)) L_N exp(Nf_p)(x)
 *           =Σr [qᶰ_r(Nx)/N] (exp(p·νr)−1).
 *
 * This is the generator's exponential nonlinear transform; no Fourier transform occurs.
 */
object ExponentialNonlinearGenerator {
    fun compareLinearTest(
        baseNetwork: ReactionNetwork,
        baseDensityState: PopulationState,
        systemSize: Int,
        momentum: DoubleArray,
        tolerance: Double,
    ): Evidence<ExponentialGeneratorComparison> {
        require(baseDensityState.counts.size == baseNetwork.species.size)
        require(systemSize > 0)
        require(momentum.size == baseNetwork.species.size && momentum.all(Double::isFinite))
        require(tolerance.isFinite() && tolerance >= 0.0)

        val scaledNetwork = DensityScaledReactionFamily.networkAtSize(baseNetwork, systemSize)
        val scaledState = DensityScaledReactionFamily.scaleInitialState(baseDensityState, systemSize)
        val inverseSize = Rational.ONE / Rational.of(systemSize)
        val finiteIntensities = scaledNetwork.reactions.map { reaction ->
            reaction.propensity(scaledState) * inverseSize
        }
        val limitingIntensities = baseNetwork.reactions.map { reaction ->
            reaction.reactants.indices.fold(reaction.rate) { intensity, species ->
                intensity * Rational.of(baseDensityState[species]).pow(reaction.reactants[species])
            }
        }
        val exactErrors = finiteIntensities.indices.map { index ->
            (finiteIntensities[index] - limitingIntensities[index]).abs()
        }
        val finiteValue = baseNetwork.reactions.indices.sumOf { index ->
            val pairing = momentum.indices.sumOf { coordinate ->
                momentum[coordinate] * baseNetwork.reactions[index].stoichiometricChange()[coordinate]
            }
            finiteIntensities[index].toDouble() * (exp(pairing) - 1.0)
        }
        val density = DoubleArray(baseNetwork.species.size) { baseDensityState[it].toDouble() }
        val limitValue = ReactionNetworkLimit.from(baseNetwork).hamiltonian(density, momentum)
        val residual = abs(finiteValue - limitValue)

        return Evidence(
            value = ExponentialGeneratorComparison(
                systemSize = systemSize,
                finiteNonlinearGenerator = finiteValue,
                limitingHamiltonian = limitValue,
                exactScaledIntensityErrors = exactErrors,
            ),
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "The finite-N exponential nonlinear generator is compared with its jump Hamiltonian symbol.",
            assumptions = listOf(
                Assumption(
                    AssumptionIds.DENSITY_DEPENDENT,
                    "The finite family uses qᶰr=k_r N^(1−m_r)∏s(x_s)_(a_rs).",
                    AssumptionStatus.CHECKED,
                    "Every finite scaled intensity and its leading polynomial intensity were evaluated as exact rationals.",
                ),
            ),
            diagnostics = listOf(
                NumericalDiagnostic("finite-N Hamiltonian residual", residual, tolerance),
            ),
        )
    }
}
