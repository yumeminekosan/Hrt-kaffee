// ============================================================
// FJÖRÐUR Integration Patch — Advanced Stochastic Methods
// ============================================================
// 
// 使用方式:
// 1. 在 index.html 的 <script> 标签中引入本文件:
//    <script src="advanced-sde-methods.js"></script>
// 2. 在 index.html 的 <script> 标签末尾添加本文件内容
//
// 或者合并到主文件中.

// --- 1. 添加新的模拟模式到 setMode ---
// 在 tabs HTML 中添加:
/*
<div class="tabs">
  <button class="tab active" onclick="setMode('oral')" data-i18n="t0">Oral</button>
  <button class="tab" onclick="setMode('im')" data-i18n="t1">Intramuscular</button>
  <button class="tab" onclick="setMode('ddi')" data-i18n="t2">DDI</button>
  <button class="tab" onclick="setMode('calibration')" data-i18n="t3">CALIBRATION</button>
  <button class="tab" onclick="setMode('master')">MASTER</button>
  <button class="tab" onclick="setMode('fokkerplanck')">F-P</button>
  <button class="tab" onclick="setMode('langevin')">LANGEVIN</button>
  <button class="tab" onclick="setMode('pathintegral')">PATH INT</button>
</div>
*/

// --- 2. 在 presets 中添加新模式的默认参数 ---
const advancedPresets = {
  master: {
    label: 'Master Equation (Gillespie)',
    N0: 1000,           // 初始分子数
    k: [[0, 0.05, 0],   // Gut -> Central
        [0, 0, 0.01],   // Central -> Peripheral
        [0, 0.005, 0]], // Peripheral -> Central
    kElim: [0, 0.02, 0], // 消除速率
    V: [1, 200, 100],    // 容积 (L)
    compartments: 3,
    nTrajectories: 50,
    dt: 0.1,
    dose: 2
  },
  fokkerplanck: {
    label: 'Fokker-Planck (Spectral)',
    D: 0.01,            // 扩散系数
    xMin: 0,
    xMax: 5,
    Nx: 128,
    dt: 0.001,
    tEnd: 24,
    dose: 2
  },
  langevin: {
    label: 'Langevin (Nanoparticle)',
    gamma: 1e-9,        // 摩擦系数 (kg/s)
    m: 1e-18,           // 粒子质量 (kg)
    kT: 4.11e-21,       // 热能 (J)
    x0: 0,
    v0: 0,
    dt: 1e-7,           // 时间步 (s)
    tEnd: 1e-4,         // 总时间 (s)
    nTrajectories: 100,
    dose: 2
  },
  pathintegral: {
    label: 'Path Integral (Rare Events)',
    threshold: 1.0,     // 毒性阈值 (mg/L)
    tEnd: 24,
    dt: 0.01,
    nPaths: 1000,
    nImportance: 100,
    dose: 2
  }
};

// --- 3. 在 setMode 函数中添加新模式处理 ---
function setModeAdvanced(m) {
  // 合并到现有的 setMode 中
  if (['master', 'fokkerplanck', 'langevin', 'pathintegral'].includes(m)) {
    currentMode = m;
    
    // 更新tab样式
    document.querySelectorAll('.tab').forEach((t, i) => {
      const modes = ['oral', 'im', 'ddi', 'calibration', 'master', 'fokkerplanck', 'langevin', 'pathintegral'];
      t.classList.toggle('active', modes[i] === m);
    });
    
    // 隐藏校准面板
    document.getElementById('calibrationPanel').style.display = 'none';
    document.getElementById('paramPanel').style.display = 'block';
    
    // 构建对应模式的控制面板
    buildAdvancedControls(m);
    runAdvanced(m);
  }
}

// --- 4. 构建高级控制面板 ---
function buildAdvancedControls(mode) {
  const c = document.getElementById('controls');
  const p = advancedPresets[mode];
  let html = '';
  
  switch (mode) {
    case 'master':
      html += slider('Initial Molecules (N₀)', 'n0', 100, 5000, p.N0, 100);
      html += slider('Gut→Central k', 'k01', 0.001, 0.2, p.k[0][1], 0.001);
      html += slider('Central→Periph k', 'k12', 0.001, 0.05, p.k[1][2], 0.001);
      html += slider('Periph→Central k', 'k21', 0.001, 0.02, p.k[2][1], 0.001);
      html += slider('Elimination ke', 'ke', 0.001, 0.1, p.kElim[1], 0.001);
      html += slider('Trajectories', 'ntraj', 10, 200, p.nTrajectories, 10);
      break;
      
    case 'fokkerplanck':
      html += slider('Diffusion D', 'D', 0.001, 0.1, p.D, 0.001);
      html += slider('Spatial Points Nx', 'nx', 32, 256, p.Nx, 32);
      html += slider('Time Step dt', 'dt', 0.0001, 0.01, p.dt, 0.0001);
      html += slider('Simulation Time', 'tend', 1, 48, p.tEnd, 1);
      break;
      
    case 'langevin':
      html += slider('Friction γ (log)', 'gamma', -12, -6, Math.log10(p.gamma), 0.1);
      html += slider('Mass m (log)', 'mass', -21, -15, Math.log10(p.m), 0.1);
      html += slider('Temperature T', 'temp', 273, 310, 300, 1);
      html += slider('Trajectories', 'ntraj', 10, 500, p.nTrajectories, 10);
      break;
      
    case 'pathintegral':
      html += slider('Toxicity Threshold', 'threshold', 0.1, 5, p.threshold, 0.1);
      html += slider('MC Paths', 'npaths', 100, 10000, p.nPaths, 100);
      html += slider('Importance Samples', 'nimp', 10, 500, p.nImportance, 10);
      html += slider('Simulation Time', 'tend', 1, 72, p.tEnd, 1);
      break;
  }
  
  c.innerHTML = html;
  
  // 绑定事件
  c.querySelectorAll('input[type=range]').forEach(inp => {
    inp.addEventListener('input', () => {
      document.getElementById(inp.id + 'Val').textContent = fmtVal(inp);
      runAdvanced(mode);
    });
  });
}

// --- 5. 运行高级模拟 ---
function runAdvanced(mode) {
  const dose = parseFloat(document.getElementById('dose').value);
  const days = parseInt(document.getElementById('days').value);
  
  switch (mode) {
    case 'master':
      runMasterEquation(dose, days);
      break;
    case 'fokkerplanck':
      runFokkerPlanck(dose, days);
      break;
    case 'langevin':
      runLangevin(dose, days);
      break;
    case 'pathintegral':
      runPathIntegral(dose, days);
      break;
  }
}

// --- 6. 具体模拟实现 ---

function runMasterEquation(dose, days) {
  const N0 = parseInt(document.getElementById('n0').value);
  const k01 = parseFloat(document.getElementById('k01').value);
  const k12 = parseFloat(document.getElementById('k12').value);
  const k21 = parseFloat(document.getElementById('k21').value);
  const ke = parseFloat(document.getElementById('ke').value);
  const nTraj = parseInt(document.getElementById('ntraj').value);
  
  const solver = new MasterEquationSolver({
    compartments: 3,
    N0: N0,
    k: [[0, k01, 0], [0, 0, k12], [0, k21, 0]],
    kElim: [0, ke, 0],
    V: [1, 200, 100],
    nTrajectories: nTraj,
    dt: 0.1,
    tEnd: days * 24,
    dose: dose
  });
  
  const result = solver.solve();
  const chartData = masterToChartData(result, 1, dose, N0);
  
  // 绘制均值和置信区间
  drawChart([
    { t: chartData.t, c: chartData.c, label: 'Master Eq. (mean)' },
    { t: chartData.t, c: chartData.upper, label: 'Mean + σ' },
    { t: chartData.t, c: chartData.lower, label: 'Mean - σ' }
  ], 'Time (h)', 'Concentration (mg/L)');
  
  // 计算统计量
  calcStats(chartData.t, chartData.c, false);
}

function runFokkerPlanck(dose, days) {
  const D = parseFloat(document.getElementById('D').value);
  const Nx = parseInt(document.getElementById('nx').value);
  const dt = parseFloat(document.getElementById('dt').value);
  const tEnd = parseFloat(document.getElementById('tend').value);
  
  // PK漂移: 吸收-消除
  const ka = 1.5;
  const ke = 0.125;
  const A = (x) => ka * dose * Math.exp(-ka * 0) - ke * x; // 简化
  
  const solver = new FokkerPlanckSolver({
    D: D,
    A: A,
    B: (x) => 2 * D,
    xMin: 0,
    xMax: dose * 2,
    Nx: Nx,
    dt: dt,
    tEnd: tEnd
  });
  
  const results = solver.solve();
  
  // 显示最后时刻的PDF
  const final = results[results.length - 1];
  const moments = FokkerPlanckSolver.computeMoments(final.P, final.x);
  
  drawChart([
    { t: final.x, c: final.P, label: `PDF at t=${final.t.toFixed(1)}h` }
  ], 'Concentration (mg/L)', 'Probability Density');
  
  // 显示统计量
  document.getElementById('statCmax').textContent = moments.mean.toFixed(3) + ' mg/L';
  document.getElementById('statTmax').textContent = moments.std.toFixed(3);
  document.getElementById('statAUC').textContent = moments.median.toFixed(3);
}

function runLangevin(dose, days) {
  const gammaLog = parseFloat(document.getElementById('gamma').value);
  const massLog = parseFloat(document.getElementById('mass').value);
  const temp = parseFloat(document.getElementById('temp').value);
  const nTraj = parseInt(document.getElementById('ntraj').value);
  
  const kB = 1.38e-23;
  
  const solver = new LangevinSolver({
    gamma: Math.pow(10, gammaLog),
    m: Math.pow(10, massLog),
    kT: kB * temp,
    Fext: (x, v, t) => -1e-12 * x, // 简谐势
    x0: 0,
    v0: 0,
    dt: 1e-7,
    tEnd: 1e-4,
    nTrajectories: nTraj
  });
  
  const result = solver.solve();
  const chartData = langevinToChartData(result);
  
  drawChart([
    { t: chartData.t, c: chartData.c, label: 'Langevin (mean)' },
    { t: chartData.t, c: chartData.upper, label: 'Mean + σ' },
    { t: chartData.t, c: chartData.lower, label: 'Mean - σ' }
  ], 'Time (s)', 'Position (m)');
  
  // MSD
  const msd = LangevinSolver.computeMSD(result.trajectories);
  
  // 计算扩散系数
  const tMid = Math.floor(result.t.length / 2);
  const D_est = msd[tMid] / (2 * result.t[tMid]);
  
  document.getElementById('statCmax').textContent = D_est.toExponential(2) + ' m²/s';
}

function runPathIntegral(dose, days) {
  const threshold = parseFloat(document.getElementById('threshold').value);
  const nPaths = parseInt(document.getElementById('npaths').value);
  const nImp = parseInt(document.getElementById('nimp').value);
  const tEnd = parseFloat(document.getElementById('tend').value);
  
  const ka = 1.5;
  const ke = 0.125;
  
  const solver = new PathIntegralSolver({
    A: (x) => ka * dose * Math.exp(-ka * 0) - ke * x,
    B: (x) => 0.1,
    x0: 0,
    threshold: threshold,
    tEnd: tEnd,
    dt: 0.01,
    nPaths: nPaths,
    nImportance: nImp
  });
  
  // 标准MC
  const pMC = solver.monteCarloProbability();
  
  // 重要性采样
  const pIS = solver.importanceSamplingProbability();
  
  // 瞬子近似
  const instanton = solver.instantonPath();
  
  // 显示结果
  const resultsDiv = document.getElementById('calibrationResults');
  resultsDiv.innerHTML = `
    <div class="panel">
      <div class="panel-title">Path Integral Results</div>
      <table style="width:100%;border-collapse:collapse;font-size:0.85rem">
        <tr><th>Method</th><th>P(crossing)</th></tr>
        <tr><td>Monte Carlo</td><td>${pMC.toExponential(4)}</td></tr>
        <tr><td>Importance Sampling</td><td>${pIS.toExponential(4)}</td></tr>
        <tr><td>Instanton Approx.</td><td>${instanton.probability.toExponential(4)}</td></tr>
      </table>
      <div style="margin-top:12px;font-size:0.8rem;color:var(--text-dim)">
        Action: ${instanton.action.toFixed(4)}<br>
        Threshold: ${threshold} mg/L
      </div>
    </div>
  `;
  
  // 绘制瞬子路径
  const tPath = instanton.path.map((_, i) => i * solver.dt);
  drawChart([
    { t: tPath, c: instanton.path, label: 'Instanton Path' }
  ], 'Time (h)', 'Concentration (mg/L)');
}

// --- 7. 修改现有的 run() 函数以支持新模式 ---
// 在 run() 函数开头添加:
/*
if (['master', 'fokkerplanck', 'langevin', 'pathintegral'].includes(currentMode)) {
  runAdvanced(currentMode);
  return;
}
*/

// --- 8. i18n 扩展 ---
const advancedI18n = {
  en: {
    master: 'Master Equation',
    fokkerplanck: 'Fokker-Planck',
    langevin: 'Langevin',
    pathintegral: 'Path Integral'
  },
  zh: {
    master: '主方程',
    fokkerplanck: 'Fokker-Planck',
    langevin: '朗之万',
    pathintegral: '路径积分'
  }
};

// 合并到现有i18n
Object.keys(advancedI18n.en).forEach(key => {
  i18n.en[key] = advancedI18n.en[key];
  i18n.zh[key] = advancedI18n.zh[key];
});
