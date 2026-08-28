package dev.hrtkaffee.ar

import dev.hrtkaffee.ar.model.TransdermalEstradiolRigorousPipeline
import dev.hrtkaffee.ar.rigor.EvidenceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransdermalEstradiolFlowIntegrationTest {
    @Test
    fun oneReactionTableFeedsGeneratorFluidSymbolAndChainComplex() {
        val artifacts = TransdermalEstradiolRigorousPipeline.prepare()
        assertEquals(16, artifacts.network.reactions.size)
        assertEquals(artifacts.network.reactions.size, artifacts.densityLimit.reactions.size)
        assertTrue(artifacts.generator.states.isNotEmpty())
        assertTrue(artifacts.generator.isIrreducible())
        assertEquals(EvidenceKind.EXACT_IDENTITY, artifacts.chainComplex.audit().kind)
    }

    @Test
    fun everyMicroscopicChannelHasItsDeclaredReverse() {
        val network = TransdermalEstradiolRigorousPipeline.prepare().network
        val ids = network.reactions.map { it.id }.toSet()
        assertTrue(network.reactions.all { reaction -> reaction.reverseId in ids })
    }
}
