package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.DutasterideRigorousPipeline
import dev.hrtkaffee.ar.model.FinasterideRigorousPipeline
import dev.hrtkaffee.ar.model.ProgestogenGnRHRigorousPipeline
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.NumericalDiagnostic
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.hydrodynamic.BoundaryCondition
import dev.hrtkaffee.ar.rigor.hydrodynamic.PeriodicSpatialLattice
import dev.hrtkaffee.ar.rigor.hydrodynamic.ReactionDiffusionFiniteVolume
import dev.hrtkaffee.ar.rigor.large_deviation.GeneralizedDoobTransform
import dev.hrtkaffee.ar.rigor.large_deviation.PrincipalEigenpair
import dev.hrtkaffee.ar.rigor.large_deviation.TiltedOperator
import dev.hrtkaffee.ar.rigor.limit.ExponentialNonlinearGenerator
import dev.hrtkaffee.ar.rigor.limit.HamiltonJacobi
import dev.hrtkaffee.ar.rigor.limit.KurtzConditions
import dev.hrtkaffee.ar.rigor.limit.LegendreFenchelSolver
import dev.hrtkaffee.ar.rigor.limit.ReactionNetworkLimit
import dev.hrtkaffee.ar.rigor.limit.kurtzFluidLimitClaim
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import dev.hrtkaffee.ar.rigor.markov.GillespieSimulator
import dev.hrtkaffee.ar.rigor.markov.PopulationState
import dev.hrtkaffee.ar.rigor.markov.RandomTimeChange
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork
import dev.hrtkaffee.ar.rigor.markov.ReactionNetworkGillespieSimulator
import dev.hrtkaffee.ar.rigor.metastability.SamplePathLargeDeviationConditions
import dev.hrtkaffee.ar.rigor.metastability.metastableExitClaim
import dev.hrtkaffee.ar.rigor.metastability.quasipotentialClaim
import dev.hrtkaffee.ar.rigor.metastability.samplePathLargeDeviationClaim
import dev.hrtkaffee.ar.rigor.topology.StoichiometricChainComplex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiveAlphaReductaseFlowIntegrationTest {
    @Test
    fun eachEmbeddedEndocrineModuleFeedsTheWholeHiddenConstruction() {
        val finasteride = FinasterideRigorousPipeline.prepare()
        val dutasteride = DutasterideRigorousPipeline.prepare()
        val progestogen = ProgestogenGnRHRigorousPipeline.prepare()
        auditFlow(
            finasteride.baseReactionNetwork,
            finasteride.microscopicInitialState,
            finasteride.exactGenerator,
        )
        auditFlow(
            dutasteride.baseReactionNetwork,
            dutasteride.microscopicInitialState,
            dutasteride.exactGenerator,
        )
        auditFlow(
            progestogen.baseReactionNetwork,
            progestogen.microscopicInitialState,
            progestogen.exactGenerator,
        )
    }

    private fun auditFlow(
        network: ReactionNetwork,
        initialState: PopulationState,
        generator: ExactGenerator,
    ) {
        assertTrue(network.reactions.all { reaction ->
            reaction.rate > Rational.ZERO &&
                reaction.reverseReactionId != null &&
                network.reactions.any { it.id == reaction.reverseReactionId }
        })
        assertTrue(generator.isIrreducible())

        val reactionPath = ReactionNetworkGillespieSimulator(Random(17)).simulate(
            network = network,
            initialState = initialState,
            horizon = 0.05,
        )
        assertEquals(
            EvidenceKind.EXACT_IDENTITY,
            RandomTimeChange.stateEquation(network, reactionPath).kind,
        )
        assertTrue(
            RandomTimeChange.compensatedCounts(network, reactionPath)
                .predictableQuadraticVariations.all { it >= 0.0 },
        )

        val nonlinear = ExponentialNonlinearGenerator.compareLinearTest(
            baseNetwork = network,
            baseDensityState = initialState,
            systemSize = 20,
            momentum = DoubleArray(network.species.size),
            tolerance = 1e-12,
        )
        assertTrue(nonlinear.diagnostics.all { it.passed })

        val densityModel = ReactionNetworkLimit.from(network)
        val density = initialState.counts.map(Int::toDouble).toDoubleArray()
        val zeroMomentum = DoubleArray(network.species.size)
        assertEquals(0.0, densityModel.hamiltonian(density, zeroMomentum), 1e-12)
        val typicalVelocity = densityModel.drift(density)
        val legendre = LegendreFenchelSolver.solve(
            densityModel,
            density,
            typicalVelocity,
        )
        assertTrue(legendre.value.converged)
        assertEquals(0.0, legendre.value.value, 1e-12)
        assertEquals(
            0.0,
            HamiltonJacobi.residual(0.0, density, zeroMomentum, densityModel),
            1e-12,
        )

        val kurtzWitnesses = listOf(
            AssumptionIds.DENSITY_DEPENDENT,
            AssumptionIds.LOCALLY_LIPSCHITZ,
            AssumptionIds.COMPACT_CONTAINMENT,
            AssumptionIds.INITIAL_CONVERGENCE,
        ).associateWith { "finite conservative reaction-network witness" }
        assertEquals(
            EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
            kurtzFluidLimitClaim(
                KurtzConditions(true, true, true, true, kurtzWitnesses),
            ).kind,
        )

        val ldp = samplePathLargeDeviationClaim(
            SamplePathLargeDeviationConditions(
                densityScalingEstablished = true,
                exponentialTightnessEstablished = true,
                goodRateFunctionEstablished = true,
                witnesses = mapOf(
                    AssumptionIds.DENSITY_DEPENDENT to "exact density-scaled family",
                    AssumptionIds.EXPONENTIAL_TIGHTNESS to "finite conserved state-space witness",
                    AssumptionIds.GOOD_RATE_FUNCTION to "finite jump-channel convexity witness",
                ),
            ),
        )
        val quasipotential = quasipotentialClaim(
            ldp,
            actionCoercivityEstablished = true,
            witness = "finite conserved state-space compactness witness",
        )
        assertEquals(
            EvidenceKind.THEOREM_UNDER_ASSUMPTIONS,
            metastableExitClaim(
                quasipotential,
                scaleSeparationEstablished = true,
                witness = "declared basin mixing/escape separation for the test network",
            ).kind,
        )

        val increments = List(generator.size) { List(generator.size) { 0.0 } }
        val tilted = TiltedOperator.currentTilt(generator, increments, tilt = 0.0)
        val exactConstantEigenpair = Evidence(
            value = PrincipalEigenpair(
                eigenvalue = 0.0,
                positiveRightEigenvector = DoubleArray(generator.size) { 1.0 },
                residualInfinityNorm = 0.0,
                iterations = 0,
            ),
            kind = EvidenceKind.NUMERICAL_CERTIFICATE,
            claim = "Q1=0 supplies the zero-tilt principal eigenpair.",
            diagnostics = listOf(NumericalDiagnostic("constant eigenvector residual", 0.0, 1e-12)),
        )
        val driven = GeneralizedDoobTransform.build(tilted, exactConstantEigenpair)
        assertTrue(driven.diagnostics.all { it.passed })
        assertEquals(
            0.05,
            GillespieSimulator(Random(23)).simulate(driven.value, 0, 0.05).horizon,
        )

        val spatial = PeriodicSpatialLattice.lift(
            localNetwork = network,
            siteCount = 3,
            particlesPerSiteScale = 1,
            diffusion = List(network.species.size) { Rational.ONE },
        )
        assertTrue(spatial.network.reactions.size > network.reactions.size * 3)
        ReactionDiffusionFiniteVolume.fromNetwork(
            localNetwork = network,
            cellWidth = 1.0,
            diffusion = DoubleArray(network.species.size) { 1.0 },
            boundaryCondition = BoundaryCondition.PERIODIC,
            conservationWeights = DoubleArray(network.species.size),
        )

        val topology = StoichiometricChainComplex.from(network).audit()
        assertEquals(EvidenceKind.EXACT_IDENTITY, topology.kind)
        assertTrue(topology.value.conservationLawBasis.isNotEmpty())
        assertTrue(topology.value.reactionCycleBasis.isNotEmpty())
    }
}
