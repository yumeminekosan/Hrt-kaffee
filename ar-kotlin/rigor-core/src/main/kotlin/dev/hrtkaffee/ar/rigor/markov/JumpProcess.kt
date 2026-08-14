package dev.hrtkaffee.ar.rigor.markov

import dev.hrtkaffee.ar.rigor.Assumption
import dev.hrtkaffee.ar.rigor.AssumptionIds
import dev.hrtkaffee.ar.rigor.AssumptionStatus
import dev.hrtkaffee.ar.rigor.exact.ExactMatrix
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.exact.fallingFactorial
import java.math.BigInteger
import java.util.ArrayDeque

data class Species(val id: String, val label: String) {
    init {
        require(id.isNotBlank() && label.isNotBlank())
    }
}

class PopulationState(counts: List<Int>) {
    val counts: List<Int> = counts.toList()

    init {
        require(this.counts.all { it >= 0 }) { "Population counts cannot be negative" }
    }

    operator fun get(speciesIndex: Int): Int = counts[speciesIndex]

    override fun equals(other: Any?): Boolean =
        other is PopulationState && counts == other.counts

    override fun hashCode(): Int = counts.hashCode()

    override fun toString(): String = counts.joinToString(prefix = "[", postfix = "]")
}

/** Stochastic mass action uses falling factorials; the rate constant absorbs volume scaling. */
data class Reaction(
    val id: String,
    val label: String,
    val reactants: List<Int>,
    val products: List<Int>,
    val rate: Rational,
    val reverseReactionId: String?,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank())
        require(reactants.size == products.size && reactants.isNotEmpty())
        require(reactants.all { it >= 0 } && products.all { it >= 0 })
        require(reactants != products) { "A jump must change the state" }
        require(rate >= Rational.ZERO) { "Reaction rates cannot be negative" }
    }

    fun propensity(state: PopulationState): Rational {
        require(state.counts.size == reactants.size)
        var combinatorialFactor = BigInteger.ONE
        reactants.forEachIndexed { index, order ->
            combinatorialFactor *= fallingFactorial(state[index], order)
        }
        return rate * Rational.of(combinatorialFactor, BigInteger.ONE)
    }

    fun apply(state: PopulationState): PopulationState? {
        if (reactants.indices.any { state[it] < reactants[it] }) return null
        return PopulationState(
            reactants.indices.map { index ->
                state[index] - reactants[index] + products[index]
            },
        )
    }

    fun stoichiometricChange(): List<Int> =
        reactants.indices.map { index -> products[index] - reactants[index] }
}

data class ReactionNetwork(
    val species: List<Species>,
    val reactions: List<Reaction>,
) {
    init {
        require(species.isNotEmpty())
        require(species.map(Species::id).distinct().size == species.size)
        require(reactions.isNotEmpty())
        require(reactions.map(Reaction::id).distinct().size == reactions.size)
        require(reactions.all { it.reactants.size == species.size })
        val reactionIds = reactions.map(Reaction::id).toSet()
        require(reactions.all { it.reverseReactionId == null || it.reverseReactionId in reactionIds })
    }

    fun reachableStates(initial: PopulationState, maximumStates: Int = 20_000): List<PopulationState> {
        require(initial.counts.size == species.size)
        require(maximumStates > 0)

        val discovered = linkedSetOf(initial)
        val frontier = ArrayDeque<PopulationState>()
        frontier.add(initial)

        while (frontier.isNotEmpty()) {
            val state = frontier.removeFirst()
            reactions.forEach { reaction ->
                if (reaction.propensity(state) == Rational.ZERO) return@forEach
                val target = reaction.apply(state) ?: return@forEach
                if (discovered.add(target)) {
                    require(discovered.size <= maximumStates) {
                        "Reachable state space exceeded the explicit cap $maximumStates"
                    }
                    frontier.add(target)
                }
            }
        }
        return discovered.toList()
    }
}

/** Row-convention generator: Q[x,y] is the x→y rate and every row sums to zero. */
class ExactGenerator private constructor(
    val states: List<PopulationState>,
    val matrix: ExactMatrix,
) {
    val size: Int = states.size

    init {
        require(size > 0)
        require(matrix.rowCount == size && matrix.columnCount == size)
        for (row in 0 until size) {
            var rowSum = Rational.ZERO
            for (column in 0 until size) {
                val value = matrix[row, column]
                rowSum += value
                if (row == column) require(value <= Rational.ZERO)
                else require(value >= Rational.ZERO)
            }
            require(rowSum == Rational.ZERO) { "Generator row $row does not sum exactly to zero" }
        }
    }

    fun exitRate(stateIndex: Int): Rational = -matrix[stateIndex, stateIndex]

    fun outgoing(stateIndex: Int): List<Pair<Int, Rational>> =
        (0 until size)
            .filter { it != stateIndex && matrix[stateIndex, it] > Rational.ZERO }
            .map { it to matrix[stateIndex, it] }

    fun applyTo(testFunction: List<Rational>): List<Rational> = matrix * testFunction

    fun isIrreducible(): Boolean {
        if (size == 1) return true
        fun reachable(reverse: Boolean): Set<Int> {
            val visited = mutableSetOf(0)
            val queue = ArrayDeque<Int>()
            queue.add(0)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (candidate in 0 until size) {
                    val rate = if (reverse) matrix[candidate, current] else matrix[current, candidate]
                    if (rate > Rational.ZERO && visited.add(candidate)) queue.add(candidate)
                }
            }
            return visited
        }
        return reachable(reverse = false).size == size && reachable(reverse = true).size == size
    }

    fun structuralAssumptions(): List<Assumption> = listOf(
        Assumption(
            AssumptionIds.FINITE_STATE,
            "The reachable microscopic state space is finite.",
            AssumptionStatus.CHECKED,
            "$size states were explicitly enumerated.",
        ),
        Assumption(
            AssumptionIds.BOUNDED_RATES,
            "All jump intensities are bounded on the reachable state space.",
            AssumptionStatus.CHECKED,
            "A finite exact generator has a finite maximum exit rate.",
        ),
        Assumption(
            AssumptionIds.IRREDUCIBLE,
            "The generator is irreducible.",
            if (isIrreducible()) AssumptionStatus.CHECKED else AssumptionStatus.FAILED,
            if (isIrreducible()) "Forward and reverse graph searches reach every state." else "The state graph is reducible.",
        ),
    )

    companion object {
        fun fromNetwork(
            network: ReactionNetwork,
            initial: PopulationState,
            maximumStates: Int = 20_000,
        ): ExactGenerator {
            val states = network.reachableStates(initial, maximumStates)
            val indices = states.withIndex().associate { it.value to it.index }
            val mutable = MutableList(states.size) { MutableList(states.size) { Rational.ZERO } }

            states.forEachIndexed { sourceIndex, state ->
                network.reactions.forEach { reaction ->
                    val rate = reaction.propensity(state)
                    if (rate == Rational.ZERO) return@forEach
                    val target = reaction.apply(state) ?: return@forEach
                    val targetIndex = indices.getValue(target)
                    mutable[sourceIndex][targetIndex] += rate
                }
            }

            mutable.indices.forEach { row ->
                val exitRate = mutable[row].fold(Rational.ZERO, Rational::plus)
                mutable[row][row] = -exitRate
            }
            return ExactGenerator(states, ExactMatrix.of(mutable))
        }

        fun fromRateMatrix(matrix: List<List<Rational>>): ExactGenerator {
            val states = matrix.indices.map { PopulationState(listOf(it)) }
            return ExactGenerator(states, ExactMatrix.of(matrix))
        }
    }
}
