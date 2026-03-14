# Hrt-kaffee

**H**ormone **R**eplacement **T**herapy + **Kaffee** (德语：咖啡)

跨性别女性激素替代疗法（HRT）药代动力学（PK/PD）模拟器与计算工具。

## 功能

- **PK/PD 模拟器**：模拟雌二醇（E2）等激素药物的体内浓度曲线
- **参数计算**：计算 Cmax、Cmin、Tmax、AUC 等药代动力学参数
- **多药物交互**：支持 CPA 等药物对 CYP3A4 酶抑制的模拟
- **给药方案优化**：比较不同给药方案的效果

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

## 在线访问

GitHub Pages: https://yumeminekosan.github.io/Hrt-kaffee/pkpd-simulator-v3.html

## 参考文献

- PMID 1548642 — E2V 药代动力学
- PMID 9793623 — E2V 口服参数
- PMID 8131397 — CPA CYP3A4 抑制
- Transfeminine Science — 注射 E2 元分析

## License

MIT
