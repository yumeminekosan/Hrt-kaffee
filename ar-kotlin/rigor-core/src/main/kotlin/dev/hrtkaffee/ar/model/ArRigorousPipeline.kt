package dev.hrtkaffee.ar.model

import dev.hrtkaffee.ar.rigor.limit.DensityDependentModel
import dev.hrtkaffee.ar.rigor.limit.DensityScaledReactionFamily
import dev.hrtkaffee.ar.rigor.limit.ReactionNetworkLimit
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.thermo.FormalFreeEnergy
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalance
import dev.hrtkaffee.ar.rigor.thermo.LocalDetailedBalanceReport
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex
import dev.hrtkaffee.ar.rigor.thermo.ExactQuantumRateCalibration

data class ArRigorousArtifacts(
    val intervention: ArIntervention,
    val systemSize: Int,
    val baseReactionNetwork: ReactionNetwork,
    val microscopicNetwork: ReactionNetwork,
    val microscopicInitialState: PopulationState,
    val exactGenerator: ExactGenerator,
    val densityLimitSymbol: DensityDependentModel,
    val chainComplex: StoichiometricChainComplex,
    val panelResult: ArSuppressionResult,
    val quantumRateCalibration: ExactQuantumRateCalibration?,
) {
    fun auditLocalDetailedBalance(
        stateFreeEnergies: List<FormalFreeEnergy>,
        reservoirFactor: (source: Int, target: Int) -> Rational = { _, _ -> Rational.ONE },
    ): Evidence<LocalDetailedBalanceReport> = LocalDetailedBalance.audit(
        generator = exactGenerator,
        stateFreeEnergies = stateFreeEnergies,
        reservoirFactor = reservoirFactor,
    )
}

/** Single entry point that prevents each mathematical layer from inventing a different reaction table. */
object ArRigorousPipeline {
    fun prepare(
        intervention: ArIntervention,
        systemSize: Int = 1,
        maximumStates: Int = 20_000,
        equilibriumParameters: ArEquilibriumParameters = ArEquilibriumParameters.illustrative(),
        quantumRateCalibration: ExactQuantumRateCalibration? = null,
    ): ArRigorousArtifacts {
        require(systemSize > 0)
        val microscopic = ArMicroscopicNetwork.create(intervention, quantumRateCalibration)
        val scaledNetwork = DensityScaledReactionFamily.networkAtSize(microscopic.network, systemSize)
        val scaledInitial = DensityScaledReactionFamily.scaleInitialState(microscopic.initialState, systemSize)
        return ArRigorousArtifacts(
            intervention = intervention,
            systemSize = systemSize,
            baseReactionNetwork = microscopic.network,
            microscopicNetwork = scaledNetwork,
            microscopicInitialState = scaledInitial,
            exactGenerator = ExactGenerator.fromNetwork(
                scaledNetwork,
                scaledInitial,
                maximumStates,
            ),
            densityLimitSymbol = ReactionNetworkLimit.from(microscopic.network),
            chainComplex = StoichiometricChainComplex.from(microscopic.network),
            panelResult = ArSuppressionModel(equilibriumParameters).evaluate(intervention),
            quantumRateCalibration = microscopic.quantumRateCalibration,
        )
    }
}
