// 精确参数优化 - 基于稳态目标
// 使用网格搜索找到最优参数

function simulateMultiDose(dose_mg, F, Vd_L, ka_per_h, CL_L_h, doses, interval_h) {
  const dt = 0.25;
  const duration_h = doses * interval_h + 168; // 额外一周用于稳态
  const n = Math.ceil(duration_h / dt);
  const ke = CL_L_h / Vd_L;
  
  const doseTimes = [];
  for (let d = 0; d < doses; d++) {
    doseTimes.push(d * interval_h);
  }
  
  let A_depot = 0;
  let A_central = 0;
  let lastDoseIdx = -1;
  const concentrations = [];
  
  for (let i = 0; i < n; i++) {
    const t = i * dt;
    
    const doseIdx = doseTimes.findIndex(dt => Math.abs(t - dt) < dt / 2);
    if (doseIdx !== -1 && doseIdx > lastDoseIdx) {
      A_depot += dose_mg * 1000 * F;
      lastDoseIdx = doseIdx;
    }
    
    const dA_dep = ka_per_h * A_depot * dt;
    A_depot = Math.max(0, A_depot - dA_dep);
    A_central += dA_dep;
    
    const dA_elim = ke * A_central * dt;
    A_central = Math.max(0, A_central - dA_elim);
    
    concentrations.push({ t, C: A_central / Vd_L });
  }
  
  return concentrations;
}

function getSSMetrics(conc, lastNDoses = 2, interval_h = 168) {
  // 取最后 N 个给药周期的数据
  const cutoff = conc[0].t + (conc.length * 0.25) - lastNDoses * interval_h;
  const ss = conc.filter(c => c.t > cutoff);
  
  const Cmax = Math.max(...ss.map(c => c.C));
  const nonzero = ss.filter(c => c.C > Cmax * 0.01);
  const Cmin = Math.min(...nonzero.map(c => c.C));
  const Cave = ss.reduce((s, c) => s + c.C, 0) / ss.length;
  
  return { Cmax, Cmin, Cave };
}

// 误差函数
function calcError(params, target, dose, interval_h, doses = 6) {
  const { CL, Vd, ka, F } = params;
  const conc = simulateMultiDose(dose, F, Vd, ka, CL, doses, interval_h);
  const ss = getSSMetrics(conc, 2, interval_h);
  
  // 转换为 pg/mL
  const Cmax_pg = ss.Cmax * 1000;
  const Cmin_pg = ss.Cmin * 1000;
  
  const errCmax = Math.abs(Cmax_pg - target.Cmax) / target.Cmax;
  const errCmin = Math.abs(Cmin_pg - target.Cmin) / target.Cmin;
  
  return errCmax + errCmin;
}

console.log('╔════════════════════════════════════════════════════════════════╗');
console.log('║       精确参数优化 - 基于稳态目标                                ║');
console.log('╚════════════════════════════════════════════════════════════════╝\n');

// =====================================================
// E2V 优化
// =====================================================
console.log('【E2V 参数优化】');
console.log('目标: 5mg/7d 稳态 Cmax=384, Cmin=142 pg/mL\n');

const e2vTarget = { Cmax: 384, Cmin: 142 };
let bestE2V = { error: Infinity, params: {} };

// 网格搜索
for (let CL = 30; CL <= 150; CL += 10) {
  for (let Vd = 500; Vd <= 5000; Vd += 200) {
    for (let ka = 0.002; ka <= 0.02; ka += 0.001) {
      for (let F = 0.8; F <= 1.0; F += 0.05) {
        const error = calcError({ CL, Vd, ka, F }, e2vTarget, 5, 168);
        if (error < bestE2V.error) {
          bestE2V = { error, params: { CL, Vd, ka: ka.toFixed(4), F } };
        }
      }
    }
  }
}

// 验证最佳参数
const e2vConc = simulateMultiDose(5, bestE2V.params.F, bestE2V.params.Vd, 
  parseFloat(bestE2V.params.ka), bestE2V.params.CL, 6, 168);
const e2vSS = getSSMetrics(e2vConc, 2, 168);

console.log(`最佳参数: CL=${bestE2V.params.CL}, Vd=${bestE2V.params.Vd}, ka=${bestE2V.params.ka}, F=${bestE2V.params.F}`);
console.log(`误差: ${bestE2V.error.toFixed(3)}`);
console.log(`稳态 Cmax: ${(e2vSS.Cmax * 1000).toFixed(0)} pg/mL (目标 384)`);
console.log(`稳态 Cmin: ${(e2vSS.Cmin * 1000).toFixed(0)} pg/mL (目标 142)`);
console.log(`稳态 Cave: ${(e2vSS.Cave * 1000).toFixed(0)} pg/mL\n`);

// =====================================================
// E2C 优化
// =====================================================
console.log('【E2C 参数优化】');
console.log('目标: 5mg/7d 稳态 Cmax=339, Cmin=262 pg/mL\n');

const e2cTarget = { Cmax: 339, Cmin: 262 };
let bestE2C = { error: Infinity, params: {} };

for (let CL = 20; CL <= 120; CL += 10) {
  for (let Vd = 1000; Vd <= 8000; Vd += 300) {
    for (let ka = 0.001; ka <= 0.01; ka += 0.0005) {
      for (let F = 0.8; F <= 1.0; F += 0.05) {
        const error = calcError({ CL, Vd, ka, F }, e2cTarget, 5, 168);
        if (error < bestE2C.error) {
          bestE2C = { error, params: { CL, Vd, ka: ka.toFixed(4), F } };
        }
      }
    }
  }
}

const e2cConc = simulateMultiDose(5, bestE2C.params.F, bestE2C.params.Vd,
  parseFloat(bestE2C.params.ka), bestE2C.params.CL, 6, 168);
const e2cSS = getSSMetrics(e2cConc, 2, 168);

console.log(`最佳参数: CL=${bestE2C.params.CL}, Vd=${bestE2C.params.Vd}, ka=${bestE2C.params.ka}, F=${bestE2C.params.F}`);
console.log(`误差: ${bestE2C.error.toFixed(3)}`);
console.log(`稳态 Cmax: ${(e2cSS.Cmax * 1000).toFixed(0)} pg/mL (目标 339)`);
console.log(`稳态 Cmin: ${(e2cSS.Cmin * 1000).toFixed(0)} pg/mL (目标 262)`);
console.log(`稳态 Cave: ${(e2cSS.Cave * 1000).toFixed(0)} pg/mL\n`);

// =====================================================
// TEST_En 优化
// =====================================================
console.log('【TEST_En 参数优化】');
console.log('目标: 100mg/7d 稳态 Cmax~29 ng/mL (2900 ng/dL)\n');

// TEST 参数目标（ng/mL）
let bestTest = { error: Infinity, params: {} };

for (let CL = 5; CL <= 50; CL += 5) {
  for (let Vd = 200; Vd <= 2000; Vd += 100) {
    for (let ka = 0.001; ka <= 0.02; ka += 0.001) {
      for (let F = 0.6; F <= 1.0; F += 0.05) {
        const conc = simulateMultiDose(100, F, Vd, ka, CL, 6, 168);
        const ss = getSSMetrics(conc, 2, 168);
        
        // 目标 Cmax ~29 ng/mL
        const errCmax = Math.abs(ss.Cmax - 29) / 29;
        const errCmin = Math.abs(ss.Cmin - 15) / 15; // 期望谷值 ~15 ng/mL
        const error = errCmax + errCmin;
        
        if (error < bestTest.error) {
          bestTest = { error, params: { CL, Vd, ka: ka.toFixed(4), F }, ss };
        }
      }
    }
  }
}

console.log(`最佳参数: CL=${bestTest.params.CL}, Vd=${bestTest.params.Vd}, ka=${bestTest.params.ka}, F=${bestTest.params.F}`);
console.log(`误差: ${bestTest.error.toFixed(3)}`);
console.log(`稳态 Cmax: ${bestTest.ss.Cmax.toFixed(1)} ng/mL (${(bestTest.ss.Cmax * 100).toFixed(0)} ng/dL)`);
console.log(`稳态 Cmin: ${bestTest.ss.Cmin.toFixed(1)} ng/mL (${(bestTest.ss.Cmin * 100).toFixed(0)} ng/dL)`);
console.log(`稳态 Cave: ${bestTest.ss.Cave.toFixed(1)} ng/mL\n`);

// =====================================================
// 最终参数汇总
// =====================================================
console.log('\n╔════════════════════════════════════════════════════════════════╗');
console.log('║       最终优化参数                                               ║');
console.log('╠════════════════════════════════════════════════════════════════╣');

console.log(`
E2 Oral (口服雌二醇):
  CL: 100 L/h, Vd: 150 L, ka: 0.5 /h, F: 0.03
  2mg/day -> Cmax ~122 pg/mL ✓

E2V IM (戊酸雌二醇):
  CL: ${bestE2V.params.CL} L/h, Vd: ${bestE2V.params.Vd} L, ka: ${bestE2V.params.ka} /h, F: ${bestE2V.params.F}
  5mg/7d -> 稳态 Cmax ~${(e2vSS.Cmax*1000).toFixed(0)}, Cmin ~${(e2vSS.Cmin*1000).toFixed(0)} pg/mL

E2C IM (环戊丙酸雌二醇):
  CL: ${bestE2C.params.CL} L/h, Vd: ${bestE2C.params.Vd} L, ka: ${bestE2C.params.ka} /h, F: ${bestE2C.params.F}
  5mg/7d -> 稳态 Cmax ~${(e2cSS.Cmax*1000).toFixed(0)}, Cmin ~${(e2cSS.Cmin*1000).toFixed(0)} pg/mL

TEST_En IM (庚酸睾酮):
  CL: ${bestTest.params.CL} L/h, Vd: ${bestTest.params.Vd} L, ka: ${bestTest.params.ka} /h, F: ${bestTest.params.F}
  100mg/7d -> 稳态 Cmax ~${bestTest.ss.Cmax.toFixed(0)} ng/mL (${(bestTest.ss.Cmax*100).toFixed(0)} ng/dL)
`);
console.log('╚════════════════════════════════════════════════════════════════╝');
