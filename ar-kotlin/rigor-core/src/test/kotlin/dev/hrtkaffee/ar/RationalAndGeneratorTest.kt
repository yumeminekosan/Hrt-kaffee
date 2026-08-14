package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.ArIntervention
import dev.hrtkaffee.ar.model.ArMicroscopicNetwork
import dev.hrtkaffee.ar.model.ArRigorousPipeline
import dev.hrtkaffee.ar.model.BasisPoints
import dev.hrtkaffee.ar.rigor.exact.ExactMatrix
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.limit.DensityScaledReactionFamily
import dev.hrtkaffee.ar.rigor.markov.ExactGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RationalAndGeneratorTest {
    @Test
    fun rationalArithmeticAndDecimalParsingRemainExact() {
        assertEquals(Rational.of(1, 2), Rational.of(2, 4))
        assertEquals(Rational.of(1, 8), Rational.decimal("0.125"))
        assertEquals(Rational.of(3, 2), Rational.decimal("1.5"))
        assertEquals(Rational.of(5, 6), Rational.of(1, 2) + Rational.of(1, 3))
    }

    @Test
    fun exactGaussianEliminationHasZeroAlgebraicResidual() {
        val matrix = ExactMatrix.of(
            listOf(
                listOf(Rational.of(2), Rational.ONE),
                listOf(Rational.ONE, Rational.of(-1)),
            ),
        )
        val right = listOf(Rational.of(5), Rational.ONE)
        val solution = matrix.solve(right)
        assertEquals(listOf(Rational.of(2), Rational.ONE), solution)
        assertEquals(right, matrix * solution)
    }

    @Test
    fun generatorRowsSumExactlyToZero() {
        val generator = twoStateGenerator()
        assertTrue(generator.isIrreducible())
        repeat(generator.size) { row ->
            assertEquals(Rational.ZERO, generator.matrix.row(row).fold(Rational.ZERO, Rational::plus))
        }
    }

    @Test
    fun arNetworkUsesSeparateDirectAndUpstreamChannels() {
        val system = ArMicroscopicNetwork.create(
            ArIntervention(BasisPoints(5_000), BasisPoints(5_000)),
        )
        val directReaction = system.network.reactions.single { it.id == "a_bind" }
        val upstreamReaction = system.network.reactions.single { it.id == "five_ar_forward" }
        assertEquals(Rational.ONE, directReaction.rate)
        assertEquals(Rational.of(1, 4), upstreamReaction.rate)

        val generator = ExactGenerator.fromNetwork(system.network, system.initialState)
        assertTrue(generator.size > 1)
        generator.matrix.toLists().forEach { row ->
            assertEquals(Rational.ZERO, row.fold(Rational.ZERO, Rational::plus))
        }
    }

    @Test
    fun pipelineKeepsOneReactionTableAcrossGeneratorAndDensitySymbol() {
        val artifacts = ArRigorousPipeline.prepare(
            ArIntervention(BasisPoints(2_500), BasisPoints(5_000)),
        )
        assertEquals(
            artifacts.microscopicNetwork.reactions.size,
            artifacts.densityLimitSymbol.reactions.size,
        )
        assertEquals(
            artifacts.microscopicNetwork.species.size,
            artifacts.densityLimitSymbol.dimension,
        )
        assertEquals(
            artifacts.microscopicNetwork.reactions.size,
            artifacts.chainComplex.reactionCount,
        )
    }

    @Test
    fun densityScalingMakesBinaryRatesOrderNAndLeavesUnaryRatesUnchanged() {
        val base = ArMicroscopicNetwork.create(
            ArIntervention(BasisPoints(2_500), BasisPoints(5_000)),
        ).network
        val scaled = DensityScaledReactionFamily.networkAtSize(base, 10)
        assertEquals(
            Rational.of(1, 40),
            scaled.reactions.single { it.id == "t_bind" }.rate,
        )
        assertEquals(
            Rational.of(1, 4),
            scaled.reactions.single { it.id == "five_ar_forward" }.rate,
        )
    }

    companion object {
        fun twoStateGenerator(): ExactGenerator = ExactGenerator.fromRateMatrix(
            listOf(
                listOf(Rational.of(-2), Rational.of(2)),
                listOf(Rational.of(3), Rational.of(-3)),
            ),
        )
    }
}
