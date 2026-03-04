// Quick validation of Cmin fix
const simulateMultiDose = (dose_mg, F, Vd_L, ka_per_h, CL_L_h, doses, interval_h, duration_h) => {
  const dt = 0.25;
  const n = Math.ceil(duration_h / dt);
  const ke = CL_L_h / Vd_L;
  
  const doseTimes = [];
  for (let d = 0; d < doses; d++) {
    doseTimes.push(d * interval_h);
  }
  
  let A_depot = 0;
  let A_central = 0;
  let lastDoseIdx = -1;
  const C = [];
  
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
    
    C.push(A_central / Vd_L);
  }
  
  return C;
};

// Test with updated DRUG_DB parameters
const drugs = {
  E2_oral: { dose: 2, F: 0.03, Vd: 150, ka: 0.5, CL: 100, interval: 24 },
  E2V: { dose: 5, F: 0.85, Vd: 2500, ka: 0.007, CL: 60, interval: 168 },
  E2C: { dose: 5, F: 0.95, Vd: 7300, ka: 0.004, CL: 60, interval: 168 },
  TEST_En: { dose: 100, F: 0.60, Vd: 1100, ka: 0.010, CL: 10, interval: 168 }
};

console.log('=== Cmin Fix Validation ===\n');

for (const [drug, params] of Object.entries(drugs)) {
  const doses = drug === 'E2_oral' ? 30 : 6;
  const duration = doses * params.interval;
  
  const C = simulateMultiDose(
    params.dose, params.F, params.Vd, params.ka, params.CL,
    doses, params.interval, duration
  );
  
  const Cmax = Math.max(...C);
  
  // Fixed Cmin calculation
  const validC = C.filter(c => c > 0.001);
  const Cmin = validC.length > 0 ? Math.min(...validC) : 0;
  
  // Old calculation (would cause Infinity)
  const CminOld = Math.min(...C.filter(c => c > 0));
  
  const unit = drug.includes('TEST') ? 'ng/mL' : 'pg/mL';
  const multiplier = drug.includes('TEST') ? 1 : 1000;
  
  console.log(`${drug}:`);
  console.log(`  Cmax: ${(Cmax * multiplier).toFixed(1)} ${unit}`);
  console.log(`  Cmin (fixed): ${(Cmin * multiplier).toFixed(1)} ${unit}`);
  console.log(`  Cmin (old): ${isFinite(CminOld) ? (CminOld * multiplier).toFixed(1) : 'Infinity'} ${unit}`);
  console.log('');
}
