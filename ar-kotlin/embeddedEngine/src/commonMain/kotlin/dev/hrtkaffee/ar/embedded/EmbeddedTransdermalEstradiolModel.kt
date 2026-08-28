package dev.hrtkaffee.ar.embedded

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

data class TransdermalEstradiolInput(
    val deliveryMicrogramsPerDay: Double = 50.0,
    val applicationAreaCm2: Double = 5.0,
    val replacementIntervalHours: Double = 84.0,
    val days: Int = 14,
    val siteFactor: Double = 1.0,
    val shbgNm: Double = 60.0,
    val albuminGramsPerLitre: Double = 43.0,
)

data class TransdermalEstradiolPoint(
    val timeHours: Double,
    val totalEstradiolPgMl: Double,
    val freeEstradiolPgMl: Double,
    val skinDepotMicrograms: Double,
    val dermalFluxMicrogramsPerCm2Hour: Double,
    val erAlphaOccupancyFraction: Double,
    val erBetaOccupancyFraction: Double,
    val gperEngagementFraction: Double,
)

data class TransdermalEstradiolProjection(
    val input: TransdermalEstradiolInput,
    val curve: List<TransdermalEstradiolPoint>,
    val endpoint: TransdermalEstradiolPoint,
    val cMaxPgMl: Double,
    val cAverageLastIntervalPgMl: Double,
    val troughPgMl: Double,
    val cumulativeAbsorbedMicrograms: Double,
    val labelledPatchContentMicrograms: Double,
    val patchChanges: Int,
    val isReferenceDomain: Boolean,
    val boundaryMessage: String,
)

/**
 * Browser projection of the audited patch → multilayer skin → systemic →
 * carrier/receptor network. The layer states are a finite-volume reduction of
 * the interface-partitioned Fick system; patch replacement is an exact jump.
 * Population label anchors are kept separate from receptor engagement.
 */
object EmbeddedTransdermalEstradiolModel {
    private const val BASELINE_PG_ML = 11.7
    private const val E2_MOLECULAR_WEIGHT = 272.38
    private const val SC_TRANSFER_PER_HOUR = 0.55
    private const val EPIDERMIS_TRANSFER_PER_HOUR = 0.85
    private const val DERMIS_TRANSFER_PER_HOUR = 1.15
    private const val CENTRAL_RELAXATION_PER_HOUR = 0.105
    private const val CENTRAL_TO_PERIPHERAL_PER_HOUR = 0.055
    private const val PERIPHERAL_TO_CENTRAL_PER_HOUR = 0.028

    private data class State(
        val patchReservoirMicrograms: Double,
        val stratumCorneumMicrograms: Double,
        val viableEpidermisMicrograms: Double,
        val dermalDepotMicrograms: Double,
        val centralIncrementPgMl: Double,
        val peripheralIncrementPgMl: Double,
        val absorbedMicrograms: Double,
    )

    fun simulate(
        input: TransdermalEstradiolInput,
        integrationStepHours: Double = 0.1,
    ): TransdermalEstradiolProjection {
        require(input.deliveryMicrogramsPerDay in 0.0..200.0)
        require(input.applicationAreaCm2 in 1.0..40.0)
        require(input.replacementIntervalHours in 24.0..168.0)
        require(input.days in 1..365)
        require(input.siteFactor in 0.5..1.5)
        require(input.shbgNm in 5.0..250.0)
        require(input.albuminGramsPerLitre in 20.0..60.0)
        require(integrationStepHours > 0.0 && integrationStepHours <= 0.25)

        val duration = input.days * 24.0
        val nominalInputPerHour = input.deliveryMicrogramsPerDay / 24.0
        val plateau = labelledPlateauIncrementPgMl(input.deliveryMicrogramsPerDay) * input.siteFactor
        val totalSteps = ceil(duration / integrationStepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 600.0).toInt())
        val patchContent = labelledContentMicrograms(input.deliveryMicrogramsPerDay)
        var state = State(patchContent, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        var time = 0.0
        var nextReplacement = input.replacementIntervalHours
        var stepIndex = 0
        val curve = mutableListOf<TransdermalEstradiolPoint>()

        fun point(): TransdermalEstradiolPoint {
            val total = BASELINE_PG_ML + state.centralIncrementPgMl.coerceAtLeast(0.0)
            val free = freeEstradiolNm(total, input.shbgNm, input.albuminGramsPerLitre)
            val tissueFree = free * 8.0
            return TransdermalEstradiolPoint(
                timeHours = time,
                totalEstradiolPgMl = total,
                freeEstradiolPgMl = free * E2_MOLECULAR_WEIGHT,
                skinDepotMicrograms = (state.stratumCorneumMicrograms +
                    state.viableEpidermisMicrograms + state.dermalDepotMicrograms).coerceAtLeast(0.0),
                dermalFluxMicrogramsPerCm2Hour =
                    (DERMIS_TRANSFER_PER_HOUR * state.dermalDepotMicrograms /
                        input.applicationAreaCm2).coerceAtLeast(0.0),
                erAlphaOccupancyFraction = occupancy(tissueFree, 0.10),
                erBetaOccupancyFraction = occupancy(tissueFree, 0.40),
                gperEngagementFraction = occupancy(tissueFree, 3.0),
            )
        }

        while (true) {
            if (time + 1e-9 >= nextReplacement && nextReplacement < duration - 1e-9) {
                state = state.copy(patchReservoirMicrograms = patchContent)
                nextReplacement += input.replacementIntervalHours
            }
            if (stepIndex % recordEvery == 0 || time >= duration - 1e-9) {
                val observed = point()
                if (curve.lastOrNull()?.timeHours != observed.timeHours) curve += observed
            }
            if (time >= duration - 1e-9) break
            val step = minOf(integrationStepHours, duration - time, nextReplacement - time)
            val source = if (state.patchReservoirMicrograms > 1e-12) {
                minOf(nominalInputPerHour, state.patchReservoirMicrograms / step)
            } else {
                0.0
            }
            state = rk4(state, step, source, nominalInputPerHour, plateau)
            time = minOf(duration, time + step)
            stepIndex += 1
        }

        val lastIntervalStart = (duration - input.replacementIntervalHours).coerceAtLeast(0.0)
        val lastInterval = curve.filter { it.timeHours >= lastIntervalStart }
        val reference = input.deliveryMicrogramsPerDay in setOf(25.0, 37.5, 50.0, 75.0, 100.0) &&
            input.replacementIntervalHours == 84.0 && input.days <= 28 &&
            input.applicationAreaCm2 == labelledAreaCm2(input.deliveryMicrogramsPerDay)
        return TransdermalEstradiolProjection(
            input = input,
            curve = curve,
            endpoint = curve.last(),
            cMaxPgMl = curve.maxOf(TransdermalEstradiolPoint::totalEstradiolPgMl),
            cAverageLastIntervalPgMl = lastInterval.map { it.totalEstradiolPgMl }.average(),
            troughPgMl = lastInterval.minOf(TransdermalEstradiolPoint::totalEstradiolPgMl),
            cumulativeAbsorbedMicrograms = state.absorbedMicrograms.coerceAtLeast(0.0),
            labelledPatchContentMicrograms = patchContent,
            patchChanges = ceil(duration / input.replacementIntervalHours).toInt(),
            isReferenceDomain = reference,
            boundaryMessage = if (reference) {
                "位于 Vivelle-Dot 贴片标签输送率/面积/84 h 参考域；血药浓度为群体尺度投影。"
            } else {
                "剂量、面积、部位或更换间隔超出标签锚点；显示多层输运与 PK 结构外推。"
            },
        )
    }

    fun labelledAreaCm2(deliveryMicrogramsPerDay: Double): Double =
        deliveryMicrogramsPerDay / 10.0

    fun labelledContentMicrograms(deliveryMicrogramsPerDay: Double): Double =
        deliveryMicrogramsPerDay * 15.6

    private fun labelledPlateauIncrementPgMl(rate: Double): Double {
        val anchors = listOf(0.0 to 0.0, 37.5 to 22.3, 50.0 to 45.3, 75.0 to 60.3, 100.0 to 77.3)
        if (rate <= 0.0) return 0.0
        if (rate >= 100.0) return 77.3 * rate / 100.0
        val upper = anchors.first { it.first >= rate }
        val lower = anchors.last { it.first <= rate }
        if (upper.first == lower.first) return upper.second
        val fraction = (rate - lower.first) / (upper.first - lower.first)
        return lower.second + fraction * (upper.second - lower.second)
    }

    private fun derivatives(
        state: State,
        source: Double,
        nominalInput: Double,
        plateau: Double,
    ): State {
        val scOut = SC_TRANSFER_PER_HOUR * state.stratumCorneumMicrograms
        val epidermisOut = EPIDERMIS_TRANSFER_PER_HOUR * state.viableEpidermisMicrograms
        val dermisOut = DERMIS_TRANSFER_PER_HOUR * state.dermalDepotMicrograms
        val inputRatio = if (nominalInput > 0.0) dermisOut / nominalInput else 0.0
        val centralTarget = plateau * inputRatio.coerceIn(0.0, 1.5)
        return State(
            -source,
            source - scOut,
            scOut - epidermisOut,
            epidermisOut - dermisOut,
            CENTRAL_RELAXATION_PER_HOUR * (centralTarget - state.centralIncrementPgMl) +
                PERIPHERAL_TO_CENTRAL_PER_HOUR *
                (state.peripheralIncrementPgMl - state.centralIncrementPgMl),
            CENTRAL_TO_PERIPHERAL_PER_HOUR *
                (state.centralIncrementPgMl - state.peripheralIncrementPgMl),
            dermisOut,
        )
    }

    private fun rk4(state: State, step: Double, source: Double, nominalInput: Double, plateau: Double): State {
        val k1 = derivatives(state, source, nominalInput, plateau)
        val k2 = derivatives(add(state, k1, step / 2.0), source, nominalInput, plateau)
        val k3 = derivatives(add(state, k2, step / 2.0), source, nominalInput, plateau)
        val k4 = derivatives(add(state, k3, step), source, nominalInput, plateau)
        fun next(a: Double, b: Double, c: Double, d: Double, e: Double): Double =
            (a + step * (b + 2.0 * c + 2.0 * d + e) / 6.0).coerceAtLeast(0.0)
        return State(
            next(state.patchReservoirMicrograms, k1.patchReservoirMicrograms, k2.patchReservoirMicrograms, k3.patchReservoirMicrograms, k4.patchReservoirMicrograms),
            next(state.stratumCorneumMicrograms, k1.stratumCorneumMicrograms, k2.stratumCorneumMicrograms, k3.stratumCorneumMicrograms, k4.stratumCorneumMicrograms),
            next(state.viableEpidermisMicrograms, k1.viableEpidermisMicrograms, k2.viableEpidermisMicrograms, k3.viableEpidermisMicrograms, k4.viableEpidermisMicrograms),
            next(state.dermalDepotMicrograms, k1.dermalDepotMicrograms, k2.dermalDepotMicrograms, k3.dermalDepotMicrograms, k4.dermalDepotMicrograms),
            next(state.centralIncrementPgMl, k1.centralIncrementPgMl, k2.centralIncrementPgMl, k3.centralIncrementPgMl, k4.centralIncrementPgMl),
            next(state.peripheralIncrementPgMl, k1.peripheralIncrementPgMl, k2.peripheralIncrementPgMl, k3.peripheralIncrementPgMl, k4.peripheralIncrementPgMl),
            next(state.absorbedMicrograms, k1.absorbedMicrograms, k2.absorbedMicrograms, k3.absorbedMicrograms, k4.absorbedMicrograms),
        )
    }

    private fun add(a: State, b: State, scale: Double): State = State(
        a.patchReservoirMicrograms + scale * b.patchReservoirMicrograms,
        a.stratumCorneumMicrograms + scale * b.stratumCorneumMicrograms,
        a.viableEpidermisMicrograms + scale * b.viableEpidermisMicrograms,
        a.dermalDepotMicrograms + scale * b.dermalDepotMicrograms,
        a.centralIncrementPgMl + scale * b.centralIncrementPgMl,
        a.peripheralIncrementPgMl + scale * b.peripheralIncrementPgMl,
        a.absorbedMicrograms + scale * b.absorbedMicrograms,
    )

    private fun freeEstradiolNm(totalPgMl: Double, shbgNm: Double, albuminGramsPerLitre: Double): Double {
        val totalNm = totalPgMl / E2_MOLECULAR_WEIGHT
        val albuminNm = albuminGramsPerLitre / 66_500.0 * 1e9
        val albuminKdNm = 32_500.0
        val shbgKdNm = 1.0
        fun balance(free: Double): Double = free +
            albuminNm * free / (albuminKdNm + free) +
            shbgNm * free / (shbgKdNm + free)
        var low = 0.0
        var high = totalNm
        repeat(80) {
            val mid = (low + high) / 2.0
            if (balance(mid) > totalNm) high = mid else low = mid
        }
        return (low + high) / 2.0
    }

    private fun occupancy(ligandNm: Double, kdNm: Double): Double =
        if (ligandNm <= 0.0) 0.0 else ligandNm / (ligandNm + kdNm)
}
