package dev.hrtkaffee.ar.rigor.exact

/** Small dense exact matrix for proof-relevant finite-state calculations. */
class ExactMatrix private constructor(private val entries: List<List<Rational>>) {
    val rowCount: Int = entries.size
    val columnCount: Int = entries.firstOrNull()?.size ?: 0

    init {
        require(entries.all { it.size == columnCount }) { "Matrix must be rectangular" }
    }

    operator fun get(row: Int, column: Int): Rational = entries[row][column]

    fun row(row: Int): List<Rational> = entries[row].toList()

    fun transpose(): ExactMatrix = of(
        List(columnCount) { column -> List(rowCount) { row -> entries[row][column] } },
    )

    operator fun times(vector: List<Rational>): List<Rational> {
        require(vector.size == columnCount) { "Dimension mismatch" }
        return List(rowCount) { row ->
            (0 until columnCount).fold(Rational.ZERO) { sum, column ->
                sum + entries[row][column] * vector[column]
            }
        }
    }

    /** Exact Gaussian elimination. Throws when the square matrix is singular. */
    fun solve(rightHandSide: List<Rational>): List<Rational> {
        require(rowCount == columnCount) { "Only square systems can be solved" }
        require(rightHandSide.size == rowCount) { "Dimension mismatch" }

        val augmented = MutableList(rowCount) { row ->
            MutableList(columnCount + 1) { column ->
                if (column == columnCount) rightHandSide[row] else entries[row][column]
            }
        }

        for (pivotColumn in 0 until columnCount) {
            val pivotRow = (pivotColumn until rowCount).firstOrNull {
                augmented[it][pivotColumn] != Rational.ZERO
            } ?: error("Singular exact system at column $pivotColumn")

            if (pivotRow != pivotColumn) {
                val temporary = augmented[pivotColumn]
                augmented[pivotColumn] = augmented[pivotRow]
                augmented[pivotRow] = temporary
            }

            val pivot = augmented[pivotColumn][pivotColumn]
            for (column in pivotColumn until columnCount + 1) {
                augmented[pivotColumn][column] = augmented[pivotColumn][column] / pivot
            }

            for (row in 0 until rowCount) {
                if (row == pivotColumn) continue
                val factor = augmented[row][pivotColumn]
                if (factor == Rational.ZERO) continue
                for (column in pivotColumn until columnCount + 1) {
                    augmented[row][column] =
                        augmented[row][column] - factor * augmented[pivotColumn][column]
                }
            }
        }

        return List(rowCount) { row -> augmented[row][columnCount] }
    }

    fun toLists(): List<List<Rational>> = entries.map { it.toList() }

    companion object {
        fun of(entries: List<List<Rational>>): ExactMatrix =
            ExactMatrix(entries.map { it.toList() })

        fun zeros(rows: Int, columns: Int): ExactMatrix {
            require(rows >= 0 && columns >= 0)
            return of(List(rows) { List(columns) { Rational.ZERO } })
        }
    }
}
