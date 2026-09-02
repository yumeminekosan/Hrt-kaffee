package dev.hrtkaffee.ar.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class BicalutamideAndrogenEvidence {
    ESTRADIOL_EXPOSURE_ONLY,
    LAB_ANCHORED_TISSUE_EQUIVALENTS,
}

data class BicalutamideEstradiolBridgeInput(
    val estradiolAveragePgMl: Double,
    val androgenEvidence: BicalutamideAndrogenEvidence =
        BicalutamideAndrogenEvidence.ESTRADIOL_EXPOSURE_ONLY,
    val baselineFreeTissueTestosteroneNm: Double = BicalutamideModel.TESTOSTERONE_KD_NM,
    val baselineFreeTissueDhtNm: Double = BicalutamideModel.DHT_KD_NM,
    val currentFreeTissueTestosteroneNm: Double = BicalutamideModel.TESTOSTERONE_KD_NM,
    val currentFreeTissueDhtNm: Double = BicalutamideModel.DHT_KD_NM,
    val targetRelativeArSignal: Double = 0.5,
    val doseIntervalHours: Double = 24.0,
    val days: Int = 42,
    val tissueUnboundPartition: Double = 1.0,
    val maximumEquivalentDoseMg: Double = 50.0,
    val doseStepMg: Double = 1.0,
)

data class BicalutamideDoseCandidate(
    val doseMg: Double,
    val centralWorstRelativeArSignal: Double,
    val conservativeWorstRelativeArSignal: Double,
    val meetsCentralTarget: Boolean,
    val meetsConservativeTarget: Boolean,
)

data class BicalutamideEstradiolBridgeProjection(
    val input: BicalutamideEstradiolBridgeInput,
    val isDoseIdentifiable: Boolean,
    val estradiolOnlyRelativeArSignal: Double?,
    val centralMinimumEquivalentDoseMg: Double?,
    val conservativeMinimumEquivalentDoseMg: Double?,
    val candidates: List<BicalutamideDoseCandidate>,
    val gateMessage: String,
)

/**
 * Estradiol exposure context -> observed residual androgen -> direct AR competition.
 *
 * Estradiol exposure alone is deliberately not converted into testosterone or DHT:
 * the attached source report is a formulation/PK bridge, and available HRT studies
 * do not identify an individual E2-only HPG response function. Once residual
 * tissue-equivalent free T/DHT values are supplied, this object performs a forward
 * threshold inversion and reports the smallest model-equivalent dose on an explicit
 * grid. The result is a mechanism sensitivity output, never a prescribing dose.
 */
object BicalutamideEstradiolBridge {
    const val ADULT_CASE_SERIES_URL =
        "https://doi.org/10.1177/20420188241305022"
    const val TRANSDERMAL_RANDOMIZED_TRIAL_URL =
        "https://doi.org/10.1210/jendso/bvae108"

    private const val CONSERVATIVE_TISSUE_PARTITION = 0.25
    private const val SAMPLE_STEP_HOURS = 1.0
    private val DISPLAY_DOSES_MG = listOf(0.0, 5.0, 10.0, 25.0, 50.0)

    fun evaluate(input: BicalutamideEstradiolBridgeInput): BicalutamideEstradiolBridgeProjection {
        validate(input)
        if (input.androgenEvidence == BicalutamideAndrogenEvidence.ESTRADIOL_EXPOSURE_ONLY) {
            return BicalutamideEstradiolBridgeProjection(
                input = input,
                isDoseIdentifiable = false,
                estradiolOnlyRelativeArSignal = null,
                centralMinimumEquivalentDoseMg = null,
                conservativeMinimumEquivalentDoseMg = null,
                candidates = emptyList(),
                gateMessage =
                    "E2 Cavg 只能作为暴露上下文；没有同期残余 T/DHT，不能识别个人 AR 信号或最低比卡鲁胺剂量。",
            )
        }

        val baselineActivation = androgenOnlyActivation(
            input.baselineFreeTissueTestosteroneNm,
            input.baselineFreeTissueDhtNm,
        )
        val estradiolOnlyActivation = androgenOnlyActivation(
            input.currentFreeTissueTestosteroneNm,
            input.currentFreeTissueDhtNm,
        )
        val fullGrid = doseGrid(input.maximumEquivalentDoseMg, input.doseStepMg).map { dose ->
            candidate(input, baselineActivation, dose)
        }
        val centralMinimum = fullGrid.firstOrNull(BicalutamideDoseCandidate::meetsCentralTarget)
            ?.doseMg
        val conservativeMinimum =
            fullGrid.firstOrNull(BicalutamideDoseCandidate::meetsConservativeTarget)?.doseMg
        val displayDoses = DISPLAY_DOSES_MG
            .filter { it <= input.maximumEquivalentDoseMg + 1e-12 }
            .toMutableList()
            .also { doses ->
                if (doses.none { it == input.maximumEquivalentDoseMg }) {
                    doses += input.maximumEquivalentDoseMg
                }
            }
            .distinct()
            .sorted()
        val displayCandidates = displayDoses.map { dose ->
            candidate(input, baselineActivation, dose)
        }
        val gate = when {
            conservativeMinimum == 0.0 ->
                "按所填残余 T/DHT 与研究阈值，0 mg 已通过保守结构压力测试；这不是停药或免用结论。"
            conservativeMinimum != null ->
                "找到通过中心与保守结构压力测试的模型等效阈值；仍不含肝脏风险、临床终点或个体 PK。"
            centralMinimum != null ->
                "中心参数可通过，但在 Kp,uu/Ki 保守压力下，给定搜索上限内没有稳健通过。"
            else ->
                "给定搜索上限内没有通过研究阈值；提高剂量不是本模型允许推出的临床结论。"
        }
        return BicalutamideEstradiolBridgeProjection(
            input = input,
            isDoseIdentifiable = true,
            estradiolOnlyRelativeArSignal = estradiolOnlyActivation / baselineActivation,
            centralMinimumEquivalentDoseMg = centralMinimum,
            conservativeMinimumEquivalentDoseMg = conservativeMinimum,
            candidates = displayCandidates,
            gateMessage = gate,
        )
    }

    private fun candidate(
        input: BicalutamideEstradiolBridgeInput,
        baselineActivation: Double,
        doseMg: Double,
    ): BicalutamideDoseCandidate {
        val central = worstRelativeSignalOverLastInterval(
            input = input,
            baselineActivation = baselineActivation,
            doseMg = doseMg,
            tissuePartition = input.tissueUnboundPartition,
            effectiveKiNm = BicalutamideModel.R_BICALUTAMIDE_KI_NM,
        )
        val conservative = worstRelativeSignalOverLastInterval(
            input = input,
            baselineActivation = baselineActivation,
            doseMg = doseMg,
            tissuePartition = min(
                input.tissueUnboundPartition,
                CONSERVATIVE_TISSUE_PARTITION,
            ),
            effectiveKiNm = BicalutamideModel.R_BICALUTAMIDE_KI_NM +
                BicalutamideModel.R_BICALUTAMIDE_KI_SD_NM,
        )
        return BicalutamideDoseCandidate(
            doseMg = doseMg,
            centralWorstRelativeArSignal = central,
            conservativeWorstRelativeArSignal = conservative,
            meetsCentralTarget = central <= input.targetRelativeArSignal + 1e-12,
            meetsConservativeTarget = conservative <= input.targetRelativeArSignal + 1e-12,
        )
    }

    private fun worstRelativeSignalOverLastInterval(
        input: BicalutamideEstradiolBridgeInput,
        baselineActivation: Double,
        doseMg: Double,
        tissuePartition: Double,
        effectiveKiNm: Double,
    ): Double {
        val durationHours = input.days * 24.0
        val intervalStart = max(0.0, durationHours - input.doseIntervalHours)
        val sampleCount = ceil((durationHours - intervalStart) / SAMPLE_STEP_HOURS).toInt()
        var worst = 0.0
        for (index in 0..sampleCount) {
            val timeHours = min(durationHours, intervalStart + index * SAMPLE_STEP_HOURS)
            val totalUgMl = BicalutamideModel.repeatedDoseActiveRConcentrationUgMl(
                doseMg = doseMg,
                doseIntervalHours = input.doseIntervalHours,
                treatmentDurationHours = durationHours,
                timeHours = timeHours,
            )
            val unboundPlasmaNm = totalUgMl * 1_000_000.0 /
                BicalutamideModel.MOLECULAR_WEIGHT_G_MOL *
                BicalutamideModel.UNBOUND_PLASMA_FRACTION
            val activation = wildTypeActivation(
                testosteroneNm = input.currentFreeTissueTestosteroneNm,
                dhtNm = input.currentFreeTissueDhtNm,
                activeRTissueNm = unboundPlasmaNm * tissuePartition,
                effectiveKiNm = effectiveKiNm,
            )
            worst = max(worst, activation / baselineActivation)
        }
        return worst
    }

    private fun androgenOnlyActivation(testosteroneNm: Double, dhtNm: Double): Double {
        val weight = testosteroneNm / BicalutamideModel.TESTOSTERONE_KD_NM +
            dhtNm / BicalutamideModel.DHT_KD_NM
        return weight / (1.0 + weight)
    }

    private fun wildTypeActivation(
        testosteroneNm: Double,
        dhtNm: Double,
        activeRTissueNm: Double,
        effectiveKiNm: Double,
    ): Double {
        val androgenWeight = testosteroneNm / BicalutamideModel.TESTOSTERONE_KD_NM +
            dhtNm / BicalutamideModel.DHT_KD_NM
        val antagonistWeight = activeRTissueNm / effectiveKiNm
        return androgenWeight / (1.0 + androgenWeight + antagonistWeight)
    }

    private fun doseGrid(maximumDoseMg: Double, doseStepMg: Double): List<Double> {
        val count = ceil(maximumDoseMg / doseStepMg).toInt()
        return (0..count).map { index -> min(maximumDoseMg, index * doseStepMg) }.distinct()
    }

    private fun validate(input: BicalutamideEstradiolBridgeInput) {
        require(input.estradiolAveragePgMl >= 0.0)
        require(input.baselineFreeTissueTestosteroneNm >= 0.0)
        require(input.baselineFreeTissueDhtNm >= 0.0)
        require(input.baselineFreeTissueTestosteroneNm + input.baselineFreeTissueDhtNm > 0.0)
        require(input.currentFreeTissueTestosteroneNm >= 0.0)
        require(input.currentFreeTissueDhtNm >= 0.0)
        require(input.targetRelativeArSignal in 0.01..1.0)
        require(input.doseIntervalHours in 6.0..168.0)
        require(input.days in 1..365)
        require(input.tissueUnboundPartition in 0.05..5.0)
        require(input.maximumEquivalentDoseMg in 0.0..200.0)
        require(input.doseStepMg > 0.0 && input.doseStepMg <= 10.0)
    }
}
