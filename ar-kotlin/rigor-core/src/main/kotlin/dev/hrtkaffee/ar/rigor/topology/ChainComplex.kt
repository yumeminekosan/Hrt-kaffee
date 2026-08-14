package dev.hrtkaffee.ar.rigor.topology

import dev.hrtkaffee.ar.rigor.Evidence
import dev.hrtkaffee.ar.rigor.EvidenceKind
import dev.hrtkaffee.ar.rigor.exact.ExactMatrix
import dev.hrtkaffee.ar.rigor.exact.Rational
import dev.hrtkaffee.ar.rigor.markov.ReactionNetwork

data class Simplex<V>(val vertices: List<V>) {
    init {
        require(vertices.isNotEmpty())
        require(vertices.distinct().size == vertices.size)
    }

    val dimension: Int get() = vertices.size - 1

    /** The i-th face map deletes vertex i. */
    fun face(index: Int): Simplex<V> {
        require(dimension > 0)
        require(index in vertices.indices)
        return Simplex(vertices.filterIndexed { vertexIndex, _ -> vertexIndex != index })
    }
}

class SimplicialChain<V>(coefficients: Map<Simplex<V>, Rational>) {
    val coefficients: Map<Simplex<V>, Rational> = coefficients
        .filterValues { it != Rational.ZERO }
        .toMap()

    fun boundary(): SimplicialChain<V> {
        val result = linkedMapOf<Simplex<V>, Rational>()
        coefficients.forEach { (simplex, coefficient) ->
            if (simplex.dimension == 0) return@forEach
            simplex.vertices.indices.forEach { faceIndex ->
                val sign = if (faceIndex % 2 == 0) Rational.ONE else -Rational.ONE
                val face = simplex.face(faceIndex)
                result[face] = (result[face] ?: Rational.ZERO) + sign * coefficient
            }
        }
        return SimplicialChain(result)
    }

    fun isZero(): Boolean = coefficients.isEmpty()

    companion object {
        fun <V> of(simplex: Simplex<V>, coefficient: Rational = Rational.ONE): SimplicialChain<V> =
            SimplicialChain(mapOf(simplex to coefficient))
    }
}

data class StoichiometricHomologyReport(
    val boundary: ExactMatrix,
    val conservationLawBasis: List<List<Rational>>,
    val reactionCycleBasis: List<List<Rational>>,
)

/** Two-term reaction complex C₁(reactions) --∂₁=S--> C₀(species). */
class StoichiometricChainComplex private constructor(
    val boundary: ExactMatrix,
) {
    val speciesCount: Int get() = boundary.rowCount
    val reactionCount: Int get() = boundary.columnCount

    /** Left nullspace ker(Sᵀ): exact linear conservation laws. */
    fun conservationLaws(): List<List<Rational>> = rationalNullspace(boundary.transpose())

    /** Right nullspace ker(S): exact reaction cycles. */
    fun reactionCycles(): List<List<Rational>> = rationalNullspace(boundary)

    fun audit(): Evidence<StoichiometricHomologyReport> {
        val laws = conservationLaws()
        val cycles = reactionCycles()
        require(laws.all { law -> boundary.transpose() * law == List(reactionCount) { Rational.ZERO } })
        require(cycles.all { cycle -> boundary * cycle == List(speciesCount) { Rational.ZERO } })
        return Evidence(
            value = StoichiometricHomologyReport(boundary, laws, cycles),
            kind = EvidenceKind.EXACT_IDENTITY,
            claim = "Conservation laws and reaction cycles are exact kernels of the stoichiometric boundary map.",
        )
    }

    companion object {
        fun from(network: ReactionNetwork): StoichiometricChainComplex {
            val entries = List(network.species.size) { species ->
                network.reactions.map { reaction ->
                    Rational.of(reaction.stoichiometricChange()[species])
                }
            }
            return StoichiometricChainComplex(ExactMatrix.of(entries))
        }
    }
}

private fun rationalNullspace(matrix: ExactMatrix): List<List<Rational>> {
    val rows = matrix.rowCount
    val columns = matrix.columnCount
    val reduced = MutableList(rows) { row -> matrix.row(row).toMutableList() }
    val pivotColumns = mutableListOf<Int>()
    var pivotRow = 0

    for (column in 0 until columns) {
        val selected = (pivotRow until rows).firstOrNull { reduced[it][column] != Rational.ZERO }
            ?: continue
        if (selected != pivotRow) {
            val temporary = reduced[pivotRow]
            reduced[pivotRow] = reduced[selected]
            reduced[selected] = temporary
        }
        val pivot = reduced[pivotRow][column]
        for (entry in column until columns) {
            reduced[pivotRow][entry] = reduced[pivotRow][entry] / pivot
        }
        for (row in 0 until rows) {
            if (row == pivotRow) continue
            val factor = reduced[row][column]
            if (factor == Rational.ZERO) continue
            for (entry in column until columns) {
                reduced[row][entry] = reduced[row][entry] - factor * reduced[pivotRow][entry]
            }
        }
        pivotColumns += column
        pivotRow += 1
        if (pivotRow == rows) break
    }

    val freeColumns = (0 until columns).filterNot(pivotColumns::contains)
    return freeColumns.map { freeColumn ->
        MutableList(columns) { Rational.ZERO }.apply {
            this[freeColumn] = Rational.ONE
            pivotColumns.forEachIndexed { row, pivotColumn ->
                this[pivotColumn] = -reduced[row][freeColumn]
            }
        }.toList()
    }
}
