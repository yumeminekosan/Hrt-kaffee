# Hrt-kaffee

**H**ormone **R**eplacement **T**herapy + **Kaffee** (德语：咖啡)

跨性别女性激素替代疗法（HRT）药代动力学（PK/PD）模拟器与计算工具。

## 功能

- **PK/PD 模拟器**：模拟雌二醇（E2）等激素药物的体内浓度曲线
- **参数计算**：计算 Cmax、Cmin、Tmax、AUC 等药代动力学参数
- **多药物交互**：支持 CPA 等药物对 CYP3A4 酶抑制的模拟
- **给药方案优化**：比较不同给药方案的效果
- **内联 5αR 动力学模块**：原 Hrt-kaffee 页面内直接计算非那雄胺与度他雄胺；使用者只看到剂量、时间、5αR1/5αR2 抑制、DHT 下降与曲线，CTMC→Kurtz/LDP/Doob/PDE/链复形过程仅保留在 Kotlin 实现与审计文档
- **内联比卡鲁胺 PK–AR 模块**：标签锚定的活性 R-异构体解析 PK、游离血浆→组织 `Kp,uu`、T/DHT/R-比卡鲁胺竞争占有、WT 与 W741L 边界、固定雄激素与 HPG 反馈应激；明确报告“同雄激素反事实信号抑制”，不伪称雄激素浓度下降或临床总阻断率
- **E2 × 比卡鲁胺阈值桥**：把经皮 E2 暴露上下文链接到比卡鲁胺面板；仅在另有基线与同期残余组织等效 T/DHT 时开放 0–50 mg 模型阈值探索，并分列中心/保守结构，不把低剂量通过写成个人 HRT 足够或用药建议
- **内联孕激素–GnRH 反馈模块**：原孕激素面板内计算口服 PK、PR 占有率、延迟 GnRH 脉冲负反馈曲线与末次给药后的阈值回落窗口；不把孕酮误写成直接 GnRH 受体拮抗剂，也不把信号回落冒充为跨女身体变化的倒计时
- **核量子输入边界**：交互显示热德布罗意尺度；只有带来源的量子结合/势垒自由能修正才能进入精确 CTMC 跳率

## 文件说明

### 核心文件
- `pkpd-simulator.html` / `pkpd-simulator-v3.html` — 交互式模拟器
- `public/pkpd-simulator*.html` — GitHub Pages 部署版本

### 计算脚本
- `pk-validation.mjs` — PK 参数验证
- `pk-optimization.mjs` — 参数优化
- `pk-compare.mjs` — 方案对比
- `pk_correct_calibration.js` — 参数校准
- `pk_final_calibration.js` — 最终校准

### 文档
- `download/cpa_validation.md` — CPA 抑制效果验算报告

## 使用

```bash
# 安装依赖
bun install

# 运行开发服务器
bun run dev

# 或直接打开模拟器
open pkpd-simulator-v3.html
```

### Kotlin 5αR / bicalutamide–AR / PR–GnRH 计算模块

```bash
cd ar-kotlin
gradle test
gradle :composeApp:run
gradle :embeddedEngine:jsBrowserDistribution
```

本地构建要求 JDK 17 与 Gradle 9.5.0；CI 固定使用相同版本。比卡鲁胺方程与验收门见 [`ar-kotlin/docs/BICALUTAMIDE_MODEL.md`](ar-kotlin/docs/BICALUTAMIDE_MODEL.md)，E2–比卡鲁胺识别门与阈值反演见 [`ar-kotlin/docs/BICALUTAMIDE_E2_BRIDGE.md`](ar-kotlin/docs/BICALUTAMIDE_E2_BRIDGE.md)，两份问题库 153 条逐题处置见 [`ar-kotlin/docs/BICALUTAMIDE_QUESTION_COVERAGE.md`](ar-kotlin/docs/BICALUTAMIDE_QUESTION_COVERAGE.md)。逐箭头审计见 [`ar-kotlin/docs/FLOW_AUDIT.md`](ar-kotlin/docs/FLOW_AUDIT.md)，模型边界、成立条件和验证状态见 [`ar-kotlin/docs/RIGOR_MATRIX.md`](ar-kotlin/docs/RIGOR_MATRIX.md)。该模块是研究模拟，不提供诊断或给药建议。

## 在线访问

GitHub Pages: https://yumeminekosan.github.io/Hrt-kaffee/

## 参考文献

- PMID 1548642 — E2V 药代动力学
- PMID 9793623 — E2V 口服参数
- PMID 8131397 — CPA CYP3A4 抑制
- Transfeminine Science — 注射 E2 元分析

## License

MIT
