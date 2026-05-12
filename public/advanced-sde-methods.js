// ============================================================
// FJÖRÐUR — Advanced Stochastic Methods Extension
// Master Equation + Fokker-Planck + Langevin + Path Integral
// ============================================================

/**
 * Master Equation Solver for Multi-Compartment PK
 * 
 * 物理化学背景: 药物分子在隔室间的转移可以用Master方程描述.
 * P(n1,n2,...,nk;t) = 各隔室分子数的联合概率分布
 * 
 * 这里用Gillespie算法(随机模拟算法,SSA)求解:
 * - 反应: 吸收、消除、隔室间转移
 * - 每次模拟一个轨迹,多次模拟得统计分布
 */
class MasterEquationSolver {
  constructor(params) {
    // 默认三室模型: Gut(0) -> Central(1) -> Peripheral(2) -> Eliminated
    this.compartments = params.compartments || 3;
    this.k = params.k || [];      // 转移速率矩阵 k[i][j]: i->j
    this.V = params.V || [];      // 各室容积
    this.dose = params.dose || 0;
    this.N0 = params.N0 || 1000;  // 初始分子数(用于离散模拟)
    this.dt = params.dt || 0.01;  // 时间步长(h)
    this.tEnd = params.tEnd || 24 * 14;
    this.nTrajectories = params.nTrajectories || 100;
  }

  /**
   * Gillespie SSA 单轨迹
   * 返回: {t: [], states: [[n0,n1,n2], ...]}
   */
  gillespieTrajectory() {
    const nComp = this.compartments;
    const states = [];
    const t = [];
    let n = new Array(nComp).fill(0);
    n[0] = this.N0; // 初始在Gut
    let time = 0;
    
    while (time < this.tEnd) {
      // 计算所有反应的propensity
      const a = [];
      const reactions = [];
      
      for (let i = 0; i < nComp; i++) {
        for (let j = 0; j < nComp; j++) {
          if (i !== j && this.k[i] && this.k[i][j] > 0) {
            a.push(this.k[i][j] * n[i]);
            reactions.push({ from: i, to: j });
          }
        }
      }
      
      // 消除反应 (到外部)
      if (this.kElim) {
        for (let i = 0; i < nComp; i++) {
          if (this.kElim[i] > 0) {
            a.push(this.kElim[i] * n[i]);
            reactions.push({ from: i, to: -1 }); // -1 = eliminated
          }
        }
      }
      
      const a0 = a.reduce((s, v) => s + v, 0);
      if (a0 === 0) break;
      
      // 采样等待时间
      const r1 = Math.random();
      const tau = -Math.log(r1) / a0;
      
      // 采样反应
      const r2 = Math.random() * a0;
      let cum = 0;
      let reaction = null;
      for (let i = 0; i < a.length; i++) {
        cum += a[i];
        if (cum >= r2) {
          reaction = reactions[i];
          break;
        }
      }
      
      // 执行反应
      if (reaction) {
        n[reaction.from]--;
        if (reaction.to >= 0) n[reaction.to]++;
      }
      
      time += tau;
      t.push(time);
      states.push([...n]);
    }
    
    return { t, states };
  }

  /**
   * 运行多次模拟,返回统计结果
   */
  solve() {
    const trajectories = [];
    for (let i = 0; i < this.nTrajectories; i++) {
      trajectories.push(this.gillespieTrajectory());
    }
    
    // 在统一时间网格上插值,计算均值和方差
    const nSteps = Math.ceil(this.tEnd / this.dt);
    const tGrid = [];
    const mean = [];
    const std = [];
    
    for (let i = 0; i <= nSteps; i++) {
      const t = i * this.dt;
      tGrid.push(t);
      
      // 收集所有轨迹在t时刻的状态
      const values = trajectories.map(traj => {
        // 找到最接近t的时间点
        let idx = 0;
        while (idx < traj.t.length && traj.t[idx] < t) idx++;
        if (idx >= traj.t.length) idx = traj.t.length - 1;
        return traj.states[idx] || new Array(this.compartments).fill(0);
      });
      
      // 计算各室的均值和标准差
      const m = new Array(this.compartments).fill(0);
      const s = new Array(this.compartments).fill(0);
      
      for (let c = 0; c < this.compartments; c++) {
        const vals = values.map(v => v[c]);
        m[c] = vals.reduce((a, b) => a + b, 0) / vals.length;
        const variance = vals.reduce((a, b) => a + (b - m[c]) ** 2, 0) / vals.length;
        s[c] = Math.sqrt(variance);
      }
      
      mean.push(m);
      std.push(s);
    }
    
    return { t: tGrid, mean, std, trajectories };
  }

  /**
   * 转换为浓度 (mg/L)
   * N * MW / (Na * V * 1e-3) 
   * 简化: 假设MW=272.38 (E2), 用宏观剂量换算
   */
  static toConcentration(n, dose, N0) {
    return (n / N0) * dose; // 简化的线性换算
  }
}

/**
 * Fokker-Planck Equation Solver (Spectral Method)
 * 
 * 物理化学背景: Fokker-Planck方程描述概率密度函数P(x,t)的演化:
 * ∂P/∂t = -∂/∂x[A(x)P] + (1/2)∂²/∂x²[B(x)P]
 * 
 * 对于PK:
 * A(x) = 确定性漂移 (药代动力学)
 * B(x) = 扩散系数 (随机波动)
 * 
 * 这里用谱方法(Chebyshev配置)在有限域上求解.
 */
class FokkerPlanckSolver {
  constructor(params) {
    this.D = params.D || 0.01;        // 扩散系数
    this.A = params.A || ((x) => -0.1 * x); // 漂移函数 A(x)
    this.B = params.B || ((x) => 2 * this.D); // 扩散函数 B(x)
    this.xMin = params.xMin || 0;
    this.xMax = params.xMax || 10;
    this.Nx = params.Nx || 128;       // 空间网格数
    this.dt = params.dt || 0.001;
    this.tEnd = params.tEnd || 24;
    
    // 初始化Chebyshev网格
    this.setupGrid();
  }

  setupGrid() {
    const N = this.Nx;
    this.x = new Array(N);
    for (let i = 0; i < N; i++) {
      // Chebyshev点: x_i = cos(π * i / (N-1)), 映射到[xMin, xMax]
      const xi = Math.cos(Math.PI * i / (N - 1));
      this.x[i] = 0.5 * (this.xMax + this.xMin) + 0.5 * (this.xMax - this.xMin) * xi;
    }
    
    // 初始化: delta函数在x=0
    this.P = new Array(N).fill(0);
    // 找到最接近0的点
    let minDist = Infinity;
    let idx0 = 0;
    for (let i = 0; i < N; i++) {
      const dist = Math.abs(this.x[i]);
      if (dist < minDist) {
        minDist = dist;
        idx0 = i;
      }
    }
    this.P[idx0] = 1.0 / (this.x[1] - this.x[0]); // 归一化
  }

  /**
   * 计算空间导数 (中心差分)
   */
  dPdx(P, i) {
    if (i === 0) return (P[1] - P[0]) / (this.x[1] - this.x[0]);
    if (i === P.length - 1) return (P[i] - P[i - 1]) / (this.x[i] - this.x[i - 1]);
    return (P[i + 1] - P[i - 1]) / (this.x[i + 1] - this.x[i - 1]);
  }

  d2Pdx2(P, i) {
    if (i === 0 || i === P.length - 1) return 0;
    const dx = this.x[i + 1] - this.x[i - 1];
    return (P[i + 1] - 2 * P[i] + P[i - 1]) / (dx * dx / 4);
  }

  /**
   * 单步Euler-Maruyama时间推进
   */
  step() {
    const N = this.Nx;
    const Pnew = new Array(N).fill(0);
    
    for (let i = 1; i < N - 1; i++) {
      const x = this.x[i];
      const A = this.A(x);
      const B = this.B(x);
      const dP = this.dPdx(this.P, i);
      const d2P = this.d2Pdx2(this.P, i);
      
      // Fokker-Planck: ∂P/∂t = -∂(AP)/∂x + (1/2)∂²(BP)/∂x²
      const dAPdx = A * dP + this.dPdx(this.P.map((p, j) => this.A(this.x[j]) * p), i);
      const d2BPdx2 = B * d2P + 2 * this.dPdx(this.P.map((p, j) => this.B(this.x[j]) * p), i) * 0.5;
      
      // 简化: 直接计算
      const flux = -A * this.P[i] + 0.5 * B * dP;
      const dFlux = this.dPdx(this.P.map((p, j) => -this.A(this.x[j]) * p + 0.5 * this.B(this.x[j]) * this.dPdx(this.P, j)), i);
      
      Pnew[i] = this.P[i] + this.dt * dFlux;
    }
    
    // 边界条件: 吸收边界 P=0
    Pnew[0] = 0;
    Pnew[N - 1] = 0;
    
    // 归一化
    const sum = Pnew.reduce((a, b) => a + b, 0);
    if (sum > 0) {
      for (let i = 0; i < N; i++) Pnew[i] /= sum;
    }
    
    this.P = Pnew;
  }

  /**
   * 求解到tEnd
   */
  solve() {
    const nSteps = Math.ceil(this.tEnd / this.dt);
    const results = [];
    
    for (let n = 0; n < nSteps; n++) {
      this.step();
      if (n % Math.ceil(nSteps / 100) === 0) {
        results.push({
          t: n * this.dt,
          P: [...this.P],
          x: [...this.x]
        });
      }
    }
    
    return results;
  }

  /**
   * 计算统计量: 均值、方差、分位数
   */
  static computeMoments(P, x) {
    const N = P.length;
    let mean = 0, var_ = 0;
    
    for (let i = 0; i < N; i++) {
      mean += x[i] * P[i];
    }
    for (let i = 0; i < N; i++) {
      var_ += (x[i] - mean) ** 2 * P[i];
    }
    
    // 分位数
    const cumsum = [];
    let cum = 0;
    for (let i = 0; i < N; i++) {
      cum += P[i];
      cumsum.push(cum);
    }
    
    const quantile = (q) => {
      for (let i = 0; i < N; i++) {
        if (cumsum[i] >= q) return x[i];
      }
      return x[N - 1];
    };
    
    return { mean, std: Math.sqrt(var_), median: quantile(0.5), q05: quantile(0.05), q95: quantile(0.95) };
  }
}

/**
 * Langevin Dynamics for Nanoparticle Drug Carriers
 * 
 * 物理化学背景: Langevin方程描述布朗运动粒子:
 * m dv/dt = -γv + F(t) + F_ext
 * 
 * 对于纳米药物载体:
 * - γ: 溶剂摩擦系数 (Stokes: γ = 6πηr)
 * - F(t): 随机力 (白噪声, <F(t)F(t')> = 2γkT δ(t-t'))
 * - F_ext: 外力 (如靶向配体-受体结合)
 */
class LangevinSolver {
  constructor(params) {
    this.gamma = params.gamma || 1.0;      // 摩擦系数 (kg/s)
    this.m = params.m || 1e-18;            // 粒子质量 (kg)
    this.kT = params.kT || 4.11e-21;       // 热能 (J, 300K)
    this.Fext = params.Fext || ((x, v, t) => 0); // 外力函数
    this.x0 = params.x0 || 0;              // 初始位置
    this.v0 = params.v0 || 0;              // 初始速度
    this.dt = params.dt || 1e-6;           // 时间步 (s)
    this.tEnd = params.tEnd || 1e-3;       // 总时间 (s)
    this.nTrajectories = params.nTrajectories || 100;
  }

  /**
   * 单轨迹Euler-Maruyama积分
   */
  trajectory() {
    const nSteps = Math.ceil(this.tEnd / this.dt);
    const t = [];
    const x = [];
    const v = [];
    
    let xi = this.x0;
    let vi = this.v0;
    
    const sqrtDt = Math.sqrt(this.dt);
    const noiseAmp = Math.sqrt(2 * this.gamma * this.kT / this.m);
    
    for (let i = 0; i < nSteps; i++) {
      const ti = i * this.dt;
      const Fext = this.Fext(xi, vi, ti);
      const dW = randn() * sqrtDt; // Wiener增量
      
      // Langevin: dv = (-γv + Fext)/m dt + noise dW
      const dv = (-this.gamma * vi + Fext) / this.m * this.dt + noiseAmp * dW;
      const dx = vi * this.dt;
      
      vi += dv;
      xi += dx;
      
      if (i % Math.max(1, Math.floor(nSteps / 1000)) === 0) {
        t.push(ti);
        x.push(xi);
        v.push(vi);
      }
    }
    
    return { t, x, v };
  }

  /**
   * 多次模拟
   */
  solve() {
    const trajectories = [];
    for (let i = 0; i < this.nTrajectories; i++) {
      trajectories.push(this.trajectory());
    }
    
    // 计算均值和方差
    const nSteps = trajectories[0].t.length;
    const meanX = new Array(nSteps).fill(0);
    const meanV = new Array(nSteps).fill(0);
    const stdX = new Array(nSteps).fill(0);
    const stdV = new Array(nSteps).fill(0);
    
    for (let i = 0; i < nSteps; i++) {
      const xs = trajectories.map(t => t.x[i]);
      const vs = trajectories.map(t => t.v[i]);
      
      meanX[i] = xs.reduce((a, b) => a + b, 0) / xs.length;
      meanV[i] = vs.reduce((a, b) => a + b, 0) / vs.length;
      
      stdX[i] = Math.sqrt(xs.reduce((a, b) => a + (b - meanX[i]) ** 2, 0) / xs.length);
      stdV[i] = Math.sqrt(vs.reduce((a, b) => a + (b - meanV[i]) ** 2, 0) / vs.length);
    }
    
    return {
      t: trajectories[0].t,
      meanX, meanV, stdX, stdV,
      trajectories
    };
  }

  /**
   * 计算均方位移(MSD)
   */
  static computeMSD(trajectories) {
    const nSteps = trajectories[0].t.length;
    const msd = new Array(nSteps).fill(0);
    
    for (let i = 0; i < nSteps; i++) {
      const displacements = trajectories.map(t => (t.x[i] - t.x[0]) ** 2);
      msd[i] = displacements.reduce((a, b) => a + b, 0) / displacements.length;
    }
    
    return msd;
  }
}

/**
 * Path Integral Method for Rare Event Probability
 * 
 * 物理化学背景: 计算药物浓度超过毒性阈值的概率.
 * 用路径积分(或Wentzel-Kramers-Brillouin近似)估算稀有事件概率.
 * 
 * 对于SDE: dx = A(x)dt + B(x)dW
 * 最可能路径(瞬子)满足Euler-Lagrange方程.
 * 这里用简化版本: Monte Carlo + 重要性采样.
 */
class PathIntegralSolver {
  constructor(params) {
    this.A = params.A || ((x) => -0.1 * x);  // 漂移
    this.B = params.B || ((x) => 0.1);       // 扩散
    this.x0 = params.x0 || 0;                // 初始值
    this.threshold = params.threshold || 1.0; // 毒性阈值
    this.tEnd = params.tEnd || 24;
    this.dt = params.dt || 0.01;
    this.nPaths = params.nPaths || 10000;
    this.nImportance = params.nImportance || 1000; // 重要性采样路径数
  }

  /**
   * 标准Monte Carlo估计穿越概率
   */
  monteCarloProbability() {
    let crossings = 0;
    const nSteps = Math.ceil(this.tEnd / this.dt);
    
    for (let p = 0; p < this.nPaths; p++) {
      let x = this.x0;
      let crossed = false;
      
      for (let i = 0; i < nSteps; i++) {
        const dW = randn() * Math.sqrt(this.dt);
        x += this.A(x) * this.dt + this.B(x) * dW;
        
        if (x >= this.threshold) {
          crossed = true;
          break;
        }
      }
      
      if (crossed) crossings++;
    }
    
    return crossings / this.nPaths;
  }

  /**
   * 重要性采样: 偏向漂移以增加穿越概率
   * 使用Girsanov定理调整权重
   */
  importanceSamplingProbability() {
    let weightedCrossings = 0;
    let totalWeight = 0;
    const nSteps = Math.ceil(this.tEnd / this.dt);
    
    // 偏向漂移: 向阈值方向推动
    const biasStrength = 0.5;
    
    for (let p = 0; p < this.nImportance; p++) {
      let x = this.x0;
      let logWeight = 0;
      let crossed = false;
      
      for (let i = 0; i < nSteps; i++) {
        const bias = biasStrength * (this.threshold - x);
        const dW = randn() * Math.sqrt(this.dt);
        const dW_biased = dW + bias * this.dt;
        
        x += this.A(x) * this.dt + this.B(x) * dW_biased;
        
        // Girsanov权重
        logWeight -= bias * dW + 0.5 * bias * bias * this.dt;
        
        if (x >= this.threshold) {
          crossed = true;
          break;
        }
      }
      
      const weight = Math.exp(logWeight);
      totalWeight += weight;
      if (crossed) weightedCrossings += weight;
    }
    
    return totalWeight > 0 ? weightedCrossings / totalWeight : 0;
  }

  /**
   * 瞬子近似(最可能路径)
   * 求解: d²x/dt² = A(x)A'(x) + (B(x)²/2)A''(x)
   * 简化为梯度下降找最可能路径
   */
  instantonPath() {
    const nSteps = Math.ceil(this.tEnd / this.dt);
    const path = new Array(nSteps).fill(this.x0);
    path[nSteps - 1] = this.threshold;
    
    // 迭代优化路径
    const nIter = 1000;
    const learningRate = 0.01;
    
    for (let iter = 0; iter < nIter; iter++) {
      const newPath = [...path];
      
      for (let i = 1; i < nSteps - 1; i++) {
        const x = path[i];
        const dx = (path[i + 1] - path[i - 1]) / (2 * this.dt);
        const d2x = (path[i + 1] - 2 * path[i] + path[i - 1]) / (this.dt ** 2);
        
        // Euler-Lagrange残差
        const residual = d2x - this.A(x) * derivative(this.A, x) - 0.5 * this.B(x) ** 2 * secondDerivative(this.A, x);
        
        newPath[i] -= learningRate * residual;
      }
      
      path.splice(0, path.length, ...newPath);
    }
    
    // 计算作用量
    let action = 0;
    for (let i = 1; i < nSteps; i++) {
      const dx = (path[i] - path[i - 1]) / this.dt;
      const x = 0.5 * (path[i] + path[i - 1]);
      const v = dx - this.A(x);
      action += 0.5 * v * v / (this.B(x) ** 2) * this.dt;
    }
    
    return { path, action, probability: Math.exp(-action) };
  }
}

// ============================================================
// Utility Functions
// ============================================================

function randn() {
  // Box-Muller变换生成标准正态分布
  let u = 0, v = 0;
  while (u === 0) u = Math.random();
  while (v === 0) v = Math.random();
  return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
}

function derivative(f, x, h = 1e-6) {
  return (f(x + h) - f(x - h)) / (2 * h);
}

function secondDerivative(f, x, h = 1e-6) {
  return (f(x + h) - 2 * f(x) + f(x - h)) / (h * h);
}

// ============================================================
// Integration with FJÖRÐUR Main Engine
// ============================================================

/**
 * 将Master方程结果转换为FJÖRÐUR图表格式
 */
function masterToChartData(result, compartmentIdx = 1, dose = 2, N0 = 1000) {
  const t = result.t;
  const c = result.mean.map(m => MasterEquationSolver.toConcentration(m[compartmentIdx], dose, N0));
  const upper = result.mean.map((m, i) => 
    MasterEquationSolver.toConcentration(m[compartmentIdx] + result.std[i][compartmentIdx], dose, N0)
  );
  const lower = result.mean.map((m, i) => 
    Math.max(0, MasterEquationSolver.toConcentration(m[compartmentIdx] - result.std[i][compartmentIdx], dose, N0))
  );
  
  return { t, c, upper, lower, label: 'Master Eq. (mean ± σ)' };
}

/**
 * 将Fokker-Planck结果转换为图表格式
 */
function fpToChartData(results, timeIdx = -1) {
  const r = timeIdx >= 0 ? results[timeIdx] : results[results.length - 1];
  return {
    x: r.x,
    P: r.P,
    label: `FP at t=${r.t.toFixed(1)}h`
  };
}

/**
 * 将Langevin结果转换为图表格式
 */
function langevinToChartData(result) {
  return {
    t: result.t,
    c: result.meanX,
    upper: result.meanX.map((m, i) => m + result.stdX[i]),
    lower: result.meanX.map((m, i) => Math.max(0, m - result.stdX[i])),
    label: 'Langevin (mean ± σ)'
  };
}

// ============================================================
// Export
// ============================================================

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    MasterEquationSolver,
    FokkerPlanckSolver,
    LangevinSolver,
    PathIntegralSolver,
    masterToChartData,
    fpToChartData,
    langevinToChartData,
    randn,
    derivative,
    secondDerivative
  };
}
