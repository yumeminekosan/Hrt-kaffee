// 验证当前文件中的参数
// 使用相同的模型计算

function simulatePK(dose_mg, F, Vd_L, ka_per_h, CL_L_h, duration_h, dt = 0.1) {
  const n = Math.ceil(duration_h / dt);
  const ke = CL_L_h / Vd_L;
  
  let A_depot = dose_mg * 1000 * F; // ug
  let A_central = 0;
  
  let Cmax = 0, Tmax = 0;
  const concentrations = [];
  
  for (let i = 0; i < n; i++) {
    const t = i * dt;
    
    const dA_dep = ka_per_h * A_depot * dt;
    A_depot = Math.max(0, A_depot - dA_dep);
    A_central += dA_dep;
    
    const dA_elim = ke * A_central * dt;
    A_central = Math.max(0, A_central - dA_elim);
    
    const C = A_central / Vd_L; // ng/mL
    concentrations.push({ t, C });
    
    if (C > Cmax) {
      Cmax = C;
      Tmax = t;
    }
  }
  
  return {
    Cmax_ng_mL: Cmax,
    Cmax_pg_mL: Cmax * 1000,
    Tmax_h: Tmax,
    Tmax_d: Tmax / 24,
    concentrations
  };
}

// 当前文件中的参数
const currentParams = {
  E2_oral: { dose: 2, CL: 100, Vd: 150, ka: 0.5, F: 0.03 },
  E2V: { dose: 5, CL: 100, Vd: 2800, ka: 0.0096, F: 1 },
  E2C: { dose: 5, CL: 90, Vd: 4800, ka: 0.0043, F: 1 },
  TEST_En: { dose: 100, CL: 20, Vd: 800, ka: 0.004, F: 0.9 }
};

// 我优化的参数
const optimizedParams = {
  E2_oral: { dose: 2, CL: 45, Vd: 50, ka: 0.05, F: 0.07 },
  E2V: { dose: 5, CL: 50, Vd: 80, ka: 0.006, F: 0.95 },
  E2C: { dose: 5, CL: 60, Vd: 110, ka: 0.003, F: 0.85 },
  TEST_En: { dose: 100, CL: 25, Vd: 160, ka: 0.004, F: 0.80 }
};

// 文献目标值
const targets = {
  E2_oral: { Cmax: 125, unit: 'pg/mL' },
  E2V: { Cmax: 667, unit: 'pg/mL' },
  E2C: { Cmax: 338, unit: 'pg/mL' },
  TEST_En: { Cmax: 29.4, unit: 'ng/mL' }
};

console.log('╔════════════════════════════════════════════════════════════════╗');
console.log('║           参数对比验算                                           ║');
console.log('╚════════════════════════════════════════════════════════════════╝\n');

for (const [drug, target] of Object.entries(targets)) {
  console.log(`【${drug}】目标 Cmax: ${target.Cmax} ${target.unit}`);
  
  // 当前参数
  const curr = currentParams[drug];
  const currResult = simulatePK(curr.dose, curr.F, curr.Vd, curr.ka, curr.CL, 720);
  const currCmax = target.unit === 'pg/mL' ? currResult.Cmax_pg_mL : currResult.Cmax_ng_mL;
  const currDiff = ((currCmax - target.Cmax) / target.Cmax * 100).toFixed(1);
  
  // 优化参数
  const opt = optimizedParams[drug];
  const optResult = simulatePK(opt.dose, opt.F, opt.Vd, opt.ka, opt.CL, 720);
  const optCmax = target.unit === 'pg/mL' ? optResult.Cmax_pg_mL : optResult.Cmax_ng_mL;
  const optDiff = ((optCmax - target.Cmax) / target.Cmax * 100).toFixed(1);
  
  console.log(`  当前参数: Cmax = ${currCmax.toFixed(1)} ${target.unit} (差异 ${currDiff}%)`);
  console.log(`  当前参数详情: CL=${curr.CL}, Vd=${curr.Vd}, ka=${curr.ka}, F=${curr.F}`);
  console.log(`  优化参数: Cmax = ${optCmax.toFixed(1)} ${target.unit} (差异 ${optDiff}%)`);
  console.log(`  优化参数详情: CL=${opt.CL}, Vd=${opt.Vd}, ka=${opt.ka}, F=${opt.F}`);
  console.log('');
}

// 多剂量稳态测试
console.log('【多剂量稳态测试 - E2V 5mg/7天 x 4周】');

function simulateMultiDose(params, doses, interval_h, duration_h) {
  const { dose_mg, F, Vd_L, ka_per_h, CL_L_h } = params;
  const dt = 0.25;
  const n = Math.ceil(duration_h / dt);
  const ke = CL_L_h / Vd_L;
  
  const doseTimes = [];
  for (let d = 0; d < doses; d++) {
    doseTimes.push(d * interval_h);
  }
  
  let A_depot = 0;
  let A_central = 0;
  const concentrations = [];
  let lastDoseIdx = -1;
  
  for (let i = 0; i < n; i++) {
    const t = i * dt;
    
    const doseIdx = doseTimes.findIndex(dt => Math.abs(t - dt) < dt / 2);
    if (doseIdx !== -1 && doseIdx > lastDoseIdx) {
      A_depot += dose_mg * 1000 * F;
      lastDoseIdx = doseIdx;
    }
    
    const dA_dep = ka_per_h * A_depot * dt;
    A_depot -= dA_dep;
    A_central += dA_dep;
    
    const dA_elim = ke * A_central * dt;
    A_central -= dA_elim;
    
    A_depot = Math.max(0, A_depot);
    A_central = Math.max(0, A_central);
    
    concentrations.push({
      t,
      C: A_central / Vd_L // ng/mL
    });
  }
  
  return concentrations;
}

// 当前参数多剂量
const currE2V = currentParams.E2V;
const currSS = simulateMultiDose(currE2V, 4, 168, 28 * 24);
const currSS_last7d = currSS.filter(c => c.t > 21 * 24);
const currSS_max = Math.max(...currSS_last7d.map(c => c.C)) * 1000;
const currSS_min = Math.min(...currSS_last7d.map(c => c.C)) * 1000;
const currSS_avg = currSS_last7d.reduce((s, c) => s + c.C, 0) / currSS_last7d.length * 1000;

console.log(`当前参数稳态 (第4周):`);
console.log(`  Cmax: ${currSS_max.toFixed(1)} pg/mL`);
console.log(`  Cmin: ${currSS_min.toFixed(1)} pg/mL`);
console.log(`  Cave: ${currSS_avg.toFixed(1)} pg/mL`);

// 优化参数多剂量
const optE2V = optimizedParams.E2V;
const optSS = simulateMultiDose(optE2V, 4, 168, 28 * 24);
const optSS_last7d = optSS.filter(c => c.t > 21 * 24);
const optSS_max = Math.max(...optSS_last7d.map(c => c.C)) * 1000;
const optSS_min = Math.min(...optSS_last7d.map(c => c.C)) * 1000;
const optSS_avg = optSS_last7d.reduce((s, c) => s + c.C, 0) / optSS_last7d.length * 1000;

console.log(`\n优化参数稳态 (第4周):`);
console.log(`  Cmax: ${optSS_max.toFixed(1)} pg/mL`);
console.log(`  Cmin: ${optSS_min.toFixed(1)} pg/mL`);
console.log(`  Cave: ${optSS_avg.toFixed(1)} pg/mL`);
console.log(`  文献目标 (TfS): Cmax ~384, Cmin ~142 pg/mL`);
