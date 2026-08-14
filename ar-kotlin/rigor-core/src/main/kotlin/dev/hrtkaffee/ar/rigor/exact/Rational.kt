package dev.hrtkaffee.ar.rigor.exact

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** A normalized exact rational number. Floating-point conversion is always explicit. */
class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) : Comparable<Rational> {
    init {
        require(denominator.signum() > 0) { "The denominator must be positive" }
        require(numerator.gcd(denominator) == BigInteger.ONE) { "Rational must be normalized" }
    }

    operator fun plus(other: Rational): Rational = of(
        numerator * other.denominator + other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun minus(other: Rational): Rational = of(
        numerator * other.denominator - other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun times(other: Rational): Rational = of(
        numerator * other.numerator,
        denominator * other.denominator,
    )

    operator fun div(other: Rational): Rational {
        require(other != ZERO) { "Division by zero" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Rational = of(-numerator, denominator)

    fun reciprocal(): Rational {
        require(this != ZERO) { "Zero has no reciprocal" }
        return of(denominator, numerator)
    }

    fun abs(): Rational = if (numerator.signum() < 0) -this else this

    fun pow(exponent: Int): Rational {
        if (exponent == 0) return ONE
        if (exponent < 0) return reciprocal().pow(-exponent)
        return of(numerator.pow(exponent), denominator.pow(exponent))
    }

    fun toBigDecimal(mathContext: MathContext = DEFAULT_CONTEXT): BigDecimal =
        numerator.toBigDecimal().divide(denominator.toBigDecimal(), mathContext)

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    override fun compareTo(other: Rational): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean =
        other is Rational && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String =
        if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"

    companion object {
        private val DEFAULT_CONTEXT = MathContext(34, RoundingMode.HALF_EVEN)
        val ZERO: Rational = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Rational = Rational(BigInteger.ONE, BigInteger.ONE)
        val TWO: Rational = of(2)

        fun of(value: Int): Rational = of(value.toLong())

        fun of(value: Long): Rational = Rational(BigInteger.valueOf(value), BigInteger.ONE)

        fun of(numerator: Long, denominator: Long): Rational =
            of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))

        fun of(numerator: BigInteger, denominator: BigInteger): Rational {
            require(denominator != BigInteger.ZERO) { "The denominator cannot be zero" }
            if (numerator == BigInteger.ZERO) return ZERO

            val sign = if (denominator.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
            val signedNumerator = numerator * sign
            val positiveDenominator = denominator * sign
            val gcd = signedNumerator.gcd(positiveDenominator)
            return Rational(signedNumerator / gcd, positiveDenominator / gcd)
        }

        /** Parses a decimal string exactly, including scientific notation. */
        fun decimal(value: String): Rational {
            val decimal = BigDecimal(value)
            val unscaled = decimal.unscaledValue()
            val scale = decimal.scale()
            return if (scale >= 0) {
                of(unscaled, BigInteger.TEN.pow(scale))
            } else {
                of(unscaled * BigInteger.TEN.pow(-scale), BigInteger.ONE)
            }
        }
    }
}

operator fun Int.times(value: Rational): Rational = Rational.of(this) * value
operator fun Long.times(value: Rational): Rational = Rational.of(this) * value

fun fallingFactorial(n: Int, order: Int): BigInteger {
    require(n >= 0) { "Population counts cannot be negative" }
    require(order >= 0) { "Reaction order cannot be negative" }
    if (order > n) return BigInteger.ZERO
    var result = BigInteger.ONE
    repeat(order) { offset -> result *= BigInteger.valueOf((n - offset).toLong()) }
    return result
}
