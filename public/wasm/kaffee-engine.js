// Kaffee WASM Engine Wrapper
// 自动加载WASM,提供fallback到JS的实现

let wasmModule = null;
let wasmLoaded = false;

export async function initKaffeeEngine() {
  if (wasmLoaded) return true;
  
  try {
    const { default: init, KaffeeEngine, monte_carlo_simulation } = await import('./kaffee_wasm.js');
    await init();
    
    wasmModule = { KaffeeEngine, monte_carlo_simulation };
    wasmLoaded = true;
    console.log('[Kaffee] WASM engine loaded successfully');
    return true;
  } catch (e) {
    console.warn('[Kaffee] WASM failed to load, using JS fallback:', e);
    wasmLoaded = false;
    return false;
  }
}

export function isWASMLoaded() {
  return wasmLoaded;
}

// PK Simulation wrapper
export function simulatePK(config, solverType = 'euler') {
  const { cl, vd, ka, f, sigma, dose, interval, numDoses, duration, dt } = config;
  
  if (wasmLoaded) {
    const engine = new wasmModule.KaffeeEngine(cl, vd, ka, f, sigma);
    
    let result;
    switch (solverType) {
      case 'symplectic':
        result = engine.simulate_symplectic(dose, interval, numDoses, duration, dt);
        break;
      case 'stratonovich':
        result = engine.simulate_stratonovich(dose, interval, numDoses, duration, dt);
        break;
      default:
        result = engine.simulate_euler(dose, interval, numDoses, duration, dt);
    }
    
    // Convert flat array to {t, C} format
    const t = [];
    const C = [];
    for (let i = 0; i < result.length; i += 2) {
      t.push(result[i]);
      C.push(result[i + 1]);
    }
    
    return { t, C };
  }
  
  // JS fallback
  return simulatePK_JS(config, solverType);
}

// Monte Carlo wrapper
export function monteCarloSimulation(config, numSims = 100, solverType = 'euler') {
  const { cl, vd, ka, f, sigma, dose, interval, numDoses, duration, dt } = config;
  
  if (wasmLoaded) {
    const result = wasmModule.monte_carlo_simulation(
      cl, vd, ka, f, sigma,
      dose, interval, numDoses, duration, dt,
      numSims, solverType
    );
    
    return {
      Cmax: { p25: result[0], p50: result[1], p75: result[2] },
      Cmin: { p25: result[3], p50: result[4], p75: result[5] },
      AUC:  { p25: result[6], p50: result[7], p75: result[8] }
    };
  }
  
  // JS fallback
  return monteCarloSimulation_JS(config, numSims, solverType);
}

// JS Fallback implementations
function simulatePK_JS(config, solverType) {
  // Import from existing JS modules or implement inline
  // This is a simplified version
  const { cl, vd, ka, f, sigma, dose, interval, numDoses, duration, dt } = config;
  
  const t = [];
  const C = [];
  let state = { A_depot: 0, A_central: 0 };
  let time = 0;
  let doseCount = 0;
  
  while (time <= duration) {
    if (doseCount < numDoses && Math.abs(time - doseCount * interval) < dt * 0.5) {
      state.A_depot += dose * f;
      doseCount++;
    }
    
    C.push(state.A_central / vd);
    t.push(time);
    
    // Simple Euler step
    const ke = cl / vd;
    const dA_dep = ka * state.A_depot * dt;
    const dA_elim = ke * state.A_central * dt;
    
    state.A_depot -= dA_dep;
    state.A_central += dA_dep - dA_elim;
    
    if (sigma > 0) {
      const dw = (Math.random() * 2 - 1) * Math.sqrt(dt);
      const ito = -0.5 * sigma * sigma * dt;
      state.A_central *= Math.exp(sigma * dw + ito);
    }
    
    state.A_depot = Math.max(0, state.A_depot);
    state.A_central = Math.max(0, state.A_central);
    
    time += dt;
  }
  
  return { t, C };
}

function monteCarloSimulation_JS(config, numSims, solverType) {
  const results = [];
  
  for (let i = 0; i < numSims; i++) {
    results.push(simulatePK_JS(config, solverType));
  }
  
  const allCmax = results.map(r => Math.max(...r.C));
  const allCmin = results.map(r => Math.min(...r.C));
  
  const percentile = (arr, p) => {
    const sorted = [...arr].sort((a, b) => a - b);
    const idx = Math.floor(sorted.length * p / 100);
    return sorted[idx];
  };
  
  return {
    Cmax: { p25: percentile(allCmax, 25), p50: percentile(allCmax, 50), p75: percentile(allCmax, 75) },
    Cmin: { p25: percentile(allCmin, 25), p50: percentile(allCmin, 50), p75: percentile(allCmin, 75) },
    AUC:  { p25: 0, p50: 0, p75: 0 } // Simplified
  };
}
