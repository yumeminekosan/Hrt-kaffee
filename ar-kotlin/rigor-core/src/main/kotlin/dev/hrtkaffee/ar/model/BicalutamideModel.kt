package dev.hrtkaffee.ar.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class BicalutamideArContext(val bicalutamideEfficacy: Double) {
    WILD_TYPE(0.0),
    W741L_AGONIST_UPPER_BOUND(1.0),
}

data class BicalutamideInput(
    val doseMg: Double = 50.0,
    val doseIntervalHours: Double = 24.0,
    val days: Int = 42,
    val freeTissueTestosteroneNm: Double = BicalutamideModel.TESTOSTERONE_KD_NM,
    val freeTissueDhtNm: Double = BicalutamideModel.DHT_KD_NM,
    val tissueUnboundPartition: Double = 1.0,
    val hpgFeedbackGain: Double = 0.0,
    val arContext: BicalutamideArContext = BicalutamideArContext.WILD_TYPE,
)

data class BicalutamidePoint(
    val timeHours: Double,
    val activeRTotalUgMl: Double,
    val activeRUnboundPlasmaNm: Double,
    val activeRTissueNm: Double,
    val androgenMultiplier: Double,
    val freeTissueTestosteroneNm: Double,
    val freeTissueDhtNm: Double,
    val freeArFraction: Double,
    val testosteroneOccupancyFraction: Double,
    val dhtOccupancyFraction: Double,
    val bicalutamideOccupancyFraction: Double,
    val arActivationFraction: Double,
    val sameAndrogenCounterfactualActivationFraction: Double,
    val relativeActivationVsPretreatment: Double,
    val directSuppressionFraction: Double,
)

data class BicalutamideStressRange(
    val lowerDirectSuppressionFraction: Double,
    val upperDirectSuppressionFraction: Double,
    val description: String,
)

data class BicalutamideCalibration(
    val singleDosePeakUgMl: Double,
    val singleDosePeakTimeHours: Double,
    val predictedSteadyStateMeanUgMl: Double,
    val labelSteadyStateMeanUgMl: Double,
    val labelSteadyStateSdUgMl: Double,
) {
    val steadyStateStandardizedResidual: Double
        get() = abs(predictedSteadyStateMeanUgMl - labelSteadyStateMeanUgMl) /
            labelSteadyStateSdUgMl
}

data class BicalutamideProjection(
    val input: BicalutamideInput,
    val curve: List<BicalutamidePoint>,
    val endpoint: BicalutamidePoint,
    val peakDirectSuppressionFraction: Double,
    val stressRange: BicalutamideStressRange,
    val calibration: BicalutamideCalibration,
    val isPkReferenceRegimen: Boolean,
    val boundaryMessage: String,
)

/**
 * Population PK -> unbound-tissue transfer -> competitive AR occupancy.
 *
 * The PK layer is a linear one-compartment oral model calibrated to the FDA
 * 50 mg active-R-enantiomer single-dose Cmax/Tmax anchors. Absolute F and V are
 * not separately identifiable and therefore appear only through one calibrated
 * concentration scale. The receptor layer reports a same-androgen
 * counterfactual: it never calls AR antagonism a reduction in T or DHT amount.
 */
object BicalutamideModel {
    const val MOLECULAR_WEIGHT_G_MOL = 430.37
    const val UNBOUND_PLASMA_FRACTION = 0.04
    const val ELIMINATION_HALF_LIFE_DAYS = 5.8
    const val ELIMINATION_RATE_PER_HOUR = 0.004979505607470872
    const val ABSORPTION_RATE_PER_HOUR = 0.10120311278353253
    const val DOSE_TO_CONCENTRATION_SCALE = 0.017950630743549988
    const val LABEL_SINGLE_DOSE_CMAX_UG_ML = 0.768
    const val LABEL_SINGLE_DOSE_TMAX_HOURS = 31.3
    const val LABEL_STEADY_STATE_MEAN_UG_ML = 8.939
    const val LABEL_STEADY_STATE_SD_UG_ML = 3.504
    const val R_BICALUTAMIDE_KI_NM = 11.0
    const val R_BICALUTAMIDE_KI_SD_NM = 1.5
    const val TESTOSTERONE_KD_NM = 0.2
    const val DHT_KD_NM = 0.018
    const val HPG_FEEDBACK_TIME_CONSTANT_HOURS = 168.0

    const val FDA_LABEL_URL =
        "https://www.accessdata.fda.gov/drugsatfda_docs/label/2017/020498s028lbl.pdf"
    const val BINDING_STUDY_URL = "https://pubmed.ncbi.nlm.nih.gov/8867996/"
    const val MUTANT_STRUCTURE_URL = "https://www.rcsb.org/structure/1Z95"

    fun simulate(
        input: BicalutamideInput,
        integrationStepHours: Double = 1.0,
    ): BicalutamideProjection {
        validate(input, integrationStepHours)
        val durationHours = input.days * 24.0
        val totalSteps = ceil(durationHours / integrationStepHours).toInt()
        val recordEvery = max(1, ceil(totalSteps / 480.0).toInt())
        val pretreatmentActivation = androgenOnlyActivation(
            input.freeTissueTestosteroneNm,
            input.freeTissueDhtNm,
        )
        var time = 0.0
        var androgenMultiplier = 1.0
        var stepIndex = 0
        var peakSuppression = Double.NEGATIVE_INFINITY
        val curve = mutableListOf<BicalutamidePoint>()

        while (true) {
            val point = observe(input, time, durationHours, androgenMultiplier, pretreatmentActivation)
            peakSuppression = max(peakSuppression, point.directSuppressionFraction)
            if (stepIndex % recordEvery == 0 || time >= durationHours - 1e-9) {
                if (curve.lastOrNull()?.timeHours != time) curve += point
            }
            if (time >= durationHours - 1e-9) break
            val step = min(integrationStepHours, durationHours - time)
            androgenMultiplier = feedbackRk4(
                input,
                time,
                durationHours,
                androgenMultiplier,
                pretreatmentActivation,
                step,
            ).coerceIn(0.1, 5.0)
            time = min(durationHours, time + step)
            stepIndex += 1
        }

        val endpoint = curve.last()
        val low = suppressionAt(
            input,
            endpoint,
            tissuePartition = 0.25,
            effectiveKiNm = R_BICALUTAMIDE_KI_NM + R_BICALUTAMIDE_KI_SD_NM,
        )
        val high = suppressionAt(
            input,
            endpoint,
            tissuePartition = 2.0,
            effectiveKiNm = R_BICALUTAMIDE_KI_NM - R_BICALUTAMIDE_KI_SD_NM,
        )
        val calibration = BicalutamideCalibration(
            singleDosePeakUgMl = singleDoseActiveRConcentrationUgMl(
                50.0,
                LABEL_SINGLE_DOSE_TMAX_HOURS,
            ),
            singleDosePeakTimeHours = LABEL_SINGLE_DOSE_TMAX_HOURS,
            predictedSteadyStateMeanUgMl = steadyStateMeanActiveRUgMl(50.0, 24.0),
            labelSteadyStateMeanUgMl = LABEL_STEADY_STATE_MEAN_UG_ML,
            labelSteadyStateSdUgMl = LABEL_STEADY_STATE_SD_UG_ML,
        )
        val referenceRegimen =
            (input.doseMg == 0.0 || abs(input.doseMg - 50.0) < 1e-12) &&
                abs(input.doseIntervalHours - 24.0) < 1e-12
        val boundary = when {
            input.arContext != BicalutamideArContext.WILD_TYPE ->
                "W741L 模式是结构支持的定性激动上界；没有可转移的人体效应幅度校准。"
            !referenceRegimen ->
                "PK 已超出 50 mg q24h 标签锚点；组织 Kp,uu 与体外 Ki→人体转移仍是显式敏感性参数。"
            else ->
                "PK 位于 50 mg q24h 标签锚点；组织 Kp,uu 与体外 Ki→人体转移仍未被人体数据识别。"
        }
        return BicalutamideProjection(
            input = input,
            curve = curve,
            endpoint = endpoint,
            peakDirectSuppressionFraction = peakSuppression,
            stressRange = BicalutamideStressRange(
                lowerDirectSuppressionFraction = min(low, high),
                upperDirectSuppressionFraction = max(low, high),
                description = "Kp,uu 0.25–2.0 and assay Ki 9.5–12.5 nM structural stress range; not a confidence interval.",
            ),
            calibration = calibration,
            isPkReferenceRegimen = referenceRegimen,
            boundaryMessage = boundary,
        )
    }

    fun singleDoseActiveRConcentrationUgMl(doseMg: Double, timeHours: Double): Double {
        require(doseMg >= 0.0 && timeHours >= 0.0)
        if (doseMg == 0.0 || timeHours == 0.0) return 0.0
        return DOSE_TO_CONCENTRATION_SCALE * doseMg * batemanShape(timeHours)
    }

    fun repeatedDoseActiveRConcentrationUgMl(
        doseMg: Double,
        doseIntervalHours: Double,
        treatmentDurationHours: Double,
        timeHours: Double,
    ): Double {
        require(doseMg >= 0.0)
        require(doseIntervalHours > 0.0 && treatmentDurationHours >= 0.0 && timeHours >= 0.0)
        if (doseMg == 0.0 || treatmentDurationHours == 0.0) return 0.0
        val lastPermittedTime = min(timeHours, treatmentDurationHours - 1e-9)
        if (lastPermittedTime < 0.0) return 0.0
        val lastDoseIndex = floor(lastPermittedTime / doseIntervalHours).toInt()
        val doseCount = lastDoseIndex + 1
        val youngestDoseAge = timeHours - lastDoseIndex * doseIntervalHours
        val eliminationSum = exponentialDoseSum(
            ELIMINATION_RATE_PER_HOUR,
            youngestDoseAge,
            doseIntervalHours,
            doseCount,
        )
        val absorptionSum = exponentialDoseSum(
            ABSORPTION_RATE_PER_HOUR,
            youngestDoseAge,
            doseIntervalHours,
            doseCount,
        )
        val shapeSum = ABSORPTION_RATE_PER_HOUR /
            (ABSORPTION_RATE_PER_HOUR - ELIMINATION_RATE_PER_HOUR) *
            (eliminationSum - absorptionSum)
        return max(0.0, DOSE_TO_CONCENTRATION_SCALE * doseMg * shapeSum)
    }

    fun steadyStateMeanActiveRUgMl(doseMg: Double, doseIntervalHours: Double): Double {
        require(doseMg >= 0.0 && doseIntervalHours > 0.0)
        return DOSE_TO_CONCENTRATION_SCALE * doseMg /
            (doseIntervalHours * ELIMINATION_RATE_PER_HOUR)
    }

    private fun validate(input: BicalutamideInput, integrationStepHours: Double) {
        require(input.doseMg in 0.0..200.0)
        require(input.doseIntervalHours in 6.0..168.0)
        require(input.days in 1..365)
        require(input.freeTissueTestosteroneNm >= 0.0)
        require(input.freeTissueDhtNm >= 0.0)
        require(input.freeTissueTestosteroneNm + input.freeTissueDhtNm > 0.0)
        require(input.tissueUnboundPartition in 0.05..5.0)
        require(input.hpgFeedbackGain in 0.0..2.0)
        require(integrationStepHours > 0.0 && integrationStepHours <= 1.0)
    }

    private fun observe(
        input: BicalutamideInput,
        timeHours: Double,
        treatmentDurationHours: Double,
        androgenMultiplier: Double,
        pretreatmentActivation: Double,
    ): BicalutamidePoint {
        val total = repeatedDoseActiveRConcentrationUgMl(
            input.doseMg,
            input.doseIntervalHours,
            treatmentDurationHours,
            timeHours,
        )
        val unboundPlasmaNm = total * 1_000_000.0 / MOLECULAR_WEIGHT_G_MOL *
            UNBOUND_PLASMA_FRACTION
        val tissueNm = unboundPlasmaNm * input.tissueUnboundPartition
        val testosteroneNm = input.freeTissueTestosteroneNm * androgenMultiplier
        val dhtNm = input.freeTissueDhtNm * androgenMultiplier
        return receptorPoint(
            timeHours = timeHours,
            totalUgMl = total,
            unboundPlasmaNm = unboundPlasmaNm,
            tissueNm = tissueNm,
            androgenMultiplier = androgenMultiplier,
            testosteroneNm = testosteroneNm,
            dhtNm = dhtNm,
            effectiveKiNm = R_BICALUTAMIDE_KI_NM,
            bicalutamideEfficacy = input.arContext.bicalutamideEfficacy,
            pretreatmentActivation = pretreatmentActivation,
        )
    }

    private fun receptorPoint(
        timeHours: Double,
        totalUgMl: Double,
        unboundPlasmaNm: Double,
        tissueNm: Double,
        androgenMultiplier: Double,
        testosteroneNm: Double,
        dhtNm: Double,
        effectiveKiNm: Double,
        bicalutamideEfficacy: Double,
        pretreatmentActivation: Double,
    ): BicalutamidePoint {
        val testosteroneWeight = testosteroneNm / TESTOSTERONE_KD_NM
        val dhtWeight = dhtNm / DHT_KD_NM
        val bicalutamideWeight = tissueNm / effectiveKiNm
        val denominator = 1.0 + testosteroneWeight + dhtWeight + bicalutamideWeight
        val free = 1.0 / denominator
        val testosteroneOccupancy = testosteroneWeight / denominator
        val dhtOccupancy = dhtWeight / denominator
        val bicalutamideOccupancy = bicalutamideWeight / denominator
        val activation = testosteroneOccupancy + dhtOccupancy +
            bicalutamideEfficacy * bicalutamideOccupancy
        val counterfactual = androgenOnlyActivation(testosteroneNm, dhtNm)
        val relativeToCounterfactual = activation / counterfactual
        return BicalutamidePoint(
            timeHours = timeHours,
            activeRTotalUgMl = totalUgMl,
            activeRUnboundPlasmaNm = unboundPlasmaNm,
            activeRTissueNm = tissueNm,
            androgenMultiplier = androgenMultiplier,
            freeTissueTestosteroneNm = testosteroneNm,
            freeTissueDhtNm = dhtNm,
            freeArFraction = free,
            testosteroneOccupancyFraction = testosteroneOccupancy,
            dhtOccupancyFraction = dhtOccupancy,
            bicalutamideOccupancyFraction = bicalutamideOccupancy,
            arActivationFraction = activation,
            sameAndrogenCounterfactualActivationFraction = counterfactual,
            relativeActivationVsPretreatment = activation / pretreatmentActivation,
            directSuppressionFraction = 1.0 - relativeToCounterfactual,
        )
    }

    private fun suppressionAt(
        input: BicalutamideInput,
        endpoint: BicalutamidePoint,
        tissuePartition: Double,
        effectiveKiNm: Double,
    ): Double = receptorPoint(
        timeHours = endpoint.timeHours,
        totalUgMl = endpoint.activeRTotalUgMl,
        unboundPlasmaNm = endpoint.activeRUnboundPlasmaNm,
        tissueNm = endpoint.activeRUnboundPlasmaNm * tissuePartition,
        androgenMultiplier = endpoint.androgenMultiplier,
        testosteroneNm = endpoint.freeTissueTestosteroneNm,
        dhtNm = endpoint.freeTissueDhtNm,
        effectiveKiNm = effectiveKiNm,
        bicalutamideEfficacy = input.arContext.bicalutamideEfficacy,
        pretreatmentActivation = androgenOnlyActivation(
            input.freeTissueTestosteroneNm,
            input.freeTissueDhtNm,
        ),
    ).directSuppressionFraction

    private fun feedbackRk4(
        input: BicalutamideInput,
        timeHours: Double,
        treatmentDurationHours: Double,
        multiplier: Double,
        pretreatmentActivation: Double,
        step: Double,
    ): Double {
        if (input.hpgFeedbackGain == 0.0) return 1.0
        fun derivative(time: Double, value: Double): Double {
            val suppression = observe(
                input,
                time,
                treatmentDurationHours,
                value.coerceIn(0.1, 5.0),
                pretreatmentActivation,
            ).directSuppressionFraction.coerceIn(0.0, 1.0)
            val target = 1.0 + input.hpgFeedbackGain * suppression
            return (target - value) / HPG_FEEDBACK_TIME_CONSTANT_HOURS
        }
        val k1 = derivative(timeHours, multiplier)
        val k2 = derivative(timeHours + step / 2.0, multiplier + step * k1 / 2.0)
        val k3 = derivative(timeHours + step / 2.0, multiplier + step * k2 / 2.0)
        val k4 = derivative(timeHours + step, multiplier + step * k3)
        return multiplier + step * (k1 + 2.0 * k2 + 2.0 * k3 + k4) / 6.0
    }

    private fun androgenOnlyActivation(testosteroneNm: Double, dhtNm: Double): Double {
        val androgenWeight = testosteroneNm / TESTOSTERONE_KD_NM + dhtNm / DHT_KD_NM
        return androgenWeight / (1.0 + androgenWeight)
    }

    private fun batemanShape(timeHours: Double): Double = ABSORPTION_RATE_PER_HOUR /
        (ABSORPTION_RATE_PER_HOUR - ELIMINATION_RATE_PER_HOUR) *
        (exp(-ELIMINATION_RATE_PER_HOUR * timeHours) -
            exp(-ABSORPTION_RATE_PER_HOUR * timeHours))

    private fun exponentialDoseSum(
        rate: Double,
        youngestDoseAge: Double,
        intervalHours: Double,
        doseCount: Int,
    ): Double = exp(-rate * youngestDoseAge) *
        (1.0 - exp(-rate * intervalHours * doseCount)) /
        (1.0 - exp(-rate * intervalHours))
}
