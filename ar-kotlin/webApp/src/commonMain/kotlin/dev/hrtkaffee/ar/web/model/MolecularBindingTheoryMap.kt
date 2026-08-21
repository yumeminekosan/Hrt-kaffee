package dev.hrtkaffee.ar.web.model

import kotlin.math.PI
import kotlin.math.sqrt

enum class TheoryNodeId {
    QUANTUM_SCALE,
    QUANTUM_CALIBRATION,
    DISCRETE_CTMC,
    JUMP_STRUCTURE,
    FLUID_LIMIT,
    NONLINEAR_GENERATOR,
    HAMILTONIAN_HJ,
    METASTABILITY,
    TILTED_OPERATOR,
    DOOB_TRANSFORM,
    DRIVEN_GILLESPIE,
    SPATIAL_PARTICLES,
    HYDRODYNAMIC_PDE,
    CHAIN_COMPLEX,
    LIVE_EQUILIBRIUM_PROJECTION,
}

enum class TheoryStageStatus {
    EXACT_CORE,
    CONDITIONAL_THEOREM,
    NUMERICAL_CERTIFICATE,
    INPUT_REQUIRED,
    LIVE_PROJECTION,
}

data class TheoryNode(
    val id: TheoryNodeId,
    val code: String,
    val title: String,
    val detail: String,
    val status: TheoryStageStatus,
)

data class TheoryEdge(
    val source: TheoryNodeId,
    val target: TheoryNodeId,
    val label: String,
)

data class TheoryReference(
    val code: String,
    val citation: String,
    val locator: String,
)

object MolecularBindingTheoryMap {
    val nodes: List<TheoryNode> = listOf(
        TheoryNode(
            TheoryNodeId.QUANTUM_SCALE,
            "Q0",
            "热德布罗意尺度 λₜₕ",
            "h/√(2πmkBT)；只诊断核量子离域尺度",
            TheoryStageStatus.NUMERICAL_CERTIFICATE,
        ),
        TheoryNode(
            TheoryNodeId.QUANTUM_CALIBRATION,
            "Q1",
            "PIMD / 量子化学校准",
            "识别 ΔΔGbind、ΔΔG‡、隧穿与零点能修正",
            TheoryStageStatus.INPUT_REQUIRED,
        ),
        TheoryNode(
            TheoryNodeId.DISCRETE_CTMC,
            "A",
            "离散分子数 CTMC",
            "自由能权重、活度与局部详细平衡",
            TheoryStageStatus.EXACT_CORE,
        ),
        TheoryNode(
            TheoryNodeId.JUMP_STRUCTURE,
            "B",
            "生成元 · 随机时间变换",
            "反应钟、跳鞅与可预测二次变差",
            TheoryStageStatus.EXACT_CORE,
        ),
        TheoryNode(
            TheoryNodeId.FLUID_LIMIT,
            "C",
            "Ω→∞ · Kurtz 流体极限",
            "需 Lipschitz、紧性与初值收敛见证",
            TheoryStageStatus.CONDITIONAL_THEOREM,
        ),
        TheoryNode(
            TheoryNodeId.NONLINEAR_GENERATOR,
            "D",
            "指数非线性生成元",
            "N⁻¹e⁻ᴺᶠ Lₙeᴺᶠ；不需要 Fourier 变换",
            TheoryStageStatus.NUMERICAL_CERTIFICATE,
        ),
        TheoryNode(
            TheoryNodeId.HAMILTONIAN_HJ,
            "E",
            "Hamiltonian · Legendre–Fenchel · HJ",
            "同一跳通道生成 H；对偶与 HJ 报告残差",
            TheoryStageStatus.NUMERICAL_CERTIFICATE,
        ),
        TheoryNode(
            TheoryNodeId.METASTABILITY,
            "F",
            "准势 · 最小作用路径 · 亚稳态",
            "全局结论需 LDP、强制性与尺度分离",
            TheoryStageStatus.CONDITIONAL_THEOREM,
        ),
        TheoryNode(
            TheoryNodeId.TILTED_OPERATOR,
            "G",
            "路径可观测量倾斜算子",
            "Feynman–Kac 算子不是随机生成元",
            TheoryStageStatus.EXACT_CORE,
        ),
        TheoryNode(
            TheoryNodeId.DOOB_TRANSFORM,
            "H",
            "广义 Doob 变换",
            "正主特征向量通过残差后才构造驱动率",
            TheoryStageStatus.NUMERICAL_CERTIFICATE,
        ),
        TheoryNode(
            TheoryNodeId.DRIVEN_GILLESPIE,
            "I",
            "可精确 Gillespie 的驱动过程",
            "只采样 Doob 后的随机生成元",
            TheoryStageStatus.EXACT_CORE,
        ),
        TheoryNode(
            TheoryNodeId.SPATIAL_PARTICLES,
            "J",
            "空间晶格交互粒子系统",
            "局部反应表 + D·L² 双向迁移",
            TheoryStageStatus.EXACT_CORE,
        ),
        TheoryNode(
            TheoryNodeId.HYDRODYNAMIC_PDE,
            "K",
            "L,K→∞ · 水动力 PDE",
            "另需替换引理、局部平衡与经验场紧性",
            TheoryStageStatus.CONDITIONAL_THEOREM,
        ),
        TheoryNode(
            TheoryNodeId.CHAIN_COMPLEX,
            "L",
            "链复形 · 守恒量 · 反应循环",
            "S 的左右精确核；通用单纯形验证 ∂²=0",
            TheoryStageStatus.EXACT_CORE,
        ),
        TheoryNode(
            TheoryNodeId.LIVE_EQUILIBRIUM_PROJECTION,
            "WEB",
            "当前浏览器平衡投影",
            "四反事实 exact Rational；不是 CTMC 路径样本",
            TheoryStageStatus.LIVE_PROJECTION,
        ),
    )

    val edges: List<TheoryEdge> = listOf(
        TheoryEdge(TheoryNodeId.QUANTUM_SCALE, TheoryNodeId.QUANTUM_CALIBRATION, "不能跳过校准"),
        TheoryEdge(TheoryNodeId.QUANTUM_CALIBRATION, TheoryNodeId.DISCRETE_CTMC, "修正 ΔG 与跃迁率"),
        TheoryEdge(TheoryNodeId.DISCRETE_CTMC, TheoryNodeId.JUMP_STRUCTURE, "同一反应表"),
        TheoryEdge(TheoryNodeId.JUMP_STRUCTURE, TheoryNodeId.FLUID_LIMIT, "Ω→∞"),
        TheoryEdge(TheoryNodeId.JUMP_STRUCTURE, TheoryNodeId.NONLINEAR_GENERATOR, "指数变换"),
        TheoryEdge(TheoryNodeId.NONLINEAR_GENERATOR, TheoryNodeId.HAMILTONIAN_HJ, "极限与凸对偶"),
        TheoryEdge(TheoryNodeId.HAMILTONIAN_HJ, TheoryNodeId.METASTABILITY, "作用泛函"),
        TheoryEdge(TheoryNodeId.JUMP_STRUCTURE, TheoryNodeId.TILTED_OPERATOR, "路径倾斜"),
        TheoryEdge(TheoryNodeId.TILTED_OPERATOR, TheoryNodeId.DOOB_TRANSFORM, "主特征对"),
        TheoryEdge(TheoryNodeId.DOOB_TRANSFORM, TheoryNodeId.DRIVEN_GILLESPIE, "随机化"),
        TheoryEdge(TheoryNodeId.DISCRETE_CTMC, TheoryNodeId.SPATIAL_PARTICLES, "空间提升"),
        TheoryEdge(TheoryNodeId.SPATIAL_PARTICLES, TheoryNodeId.HYDRODYNAMIC_PDE, "L,K→∞"),
        TheoryEdge(TheoryNodeId.DISCRETE_CTMC, TheoryNodeId.CHAIN_COMPLEX, "化学计量边界"),
        TheoryEdge(TheoryNodeId.DISCRETE_CTMC, TheoryNodeId.LIVE_EQUILIBRIUM_PROJECTION, "静态约化"),
    )

    val references: List<TheoryReference> = listOf(
        TheoryReference("R1", "Kurtz · pure-jump → ODE", "doi:10.2307/3212147"),
        TheoryReference("R2", "Chetrite–Touchette · driven process", "doi:10.1007/s00023-014-0375-8"),
        TheoryReference("R3", "Dal Cengio et al. · reaction-network geometry", "doi:10.1103/PhysRevX.13.021040"),
        TheoryReference("R4", "Fang et al. · nuclear quantum binding", "doi:10.1021/acs.jpclett.6b00777"),
    )

    private val nodesById = nodes.associateBy(TheoryNode::id)

    init {
        require(nodesById.size == TheoryNodeId.entries.size)
        require(edges.all { it.source in nodesById && it.target in nodesById })
    }

    fun node(id: TheoryNodeId): TheoryNode = nodesById.getValue(id)

    fun isReachable(source: TheoryNodeId, target: TheoryNodeId): Boolean {
        val visited = mutableSetOf(source)
        val frontier = mutableListOf(source)
        while (frontier.isNotEmpty()) {
            val current = frontier.removeAt(0)
            if (current == target) return true
            edges.asSequence()
                .filter { it.source == current }
                .map(TheoryEdge::target)
                .filter(visited::add)
                .forEach(frontier::add)
        }
        return false
    }
}

data class WebThermalDeBroglieScale(
    val massDalton: Double,
    val temperatureKelvin: Double,
    val wavelengthPicometres: Double,
)

object WebThermalDeBroglie {
    private const val PLANCK_JOULE_SECONDS = 6.626_070_15e-34
    private const val BOLTZMANN_JOULES_PER_KELVIN = 1.380_649e-23
    private const val ATOMIC_MASS_KILOGRAMS = 1.660_539_068_92e-27

    fun evaluate(massDalton: Double, temperatureKelvin: Double): WebThermalDeBroglieScale {
        require(massDalton.isFinite() && massDalton > 0.0)
        require(temperatureKelvin.isFinite() && temperatureKelvin > 0.0)
        val wavelengthMetres = PLANCK_JOULE_SECONDS / sqrt(
            2.0 * PI * massDalton * ATOMIC_MASS_KILOGRAMS *
                BOLTZMANN_JOULES_PER_KELVIN * temperatureKelvin,
        )
        return WebThermalDeBroglieScale(
            massDalton,
            temperatureKelvin,
            wavelengthMetres * 1.0e12,
        )
    }
}
