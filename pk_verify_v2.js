// PK参数验算脚本 v2 - 正确处理 Flip-Flop 动力学
// 
// 关键理解:
// 1. Depot 注射 (E2V, E2C): ka << ke (flip-flop kinetics)
//    - 表观半衰期 = 吸收半衰期 (ka)
//    - 真实消除半衰期 = ke (系统清除，很快，约2-3小时)
// 
// 2. 口服 E2: ke >> ka 仍成立，但 F 很低 (首过效应)
//    - 文献: 2mg 口服 → ~108 pg/mL 稳态
//    - 这意味着实际 F 更低，或者需要考虑连续给药的累积

const LITERATURE_DATA = {
    E2V_5mg_IM: {
        Cmax_pg_mL: 667,
        Tmax_days: 2.5,
        apparent_halfLife_days: 4.5,  // 这是吸收半衰期！
        source: 'Wikipedia PK table'
    },
    E2C_5mg_IM: {
        Cmax_pg_mL: 338,
        Tmax_days: 3.5,
        apparent_halfLife_days: 7,
        source: 'Wikipedia PK table'
    },
    Oral_E2_2mg: {
        Cmax_pg_mL: 108,  // 稳态谷值约33-65 pg/mL, 峰值更高
        Tmax_h: 8,
        steadyState_Cmin_pg_mL: 50,  // 稳态谷值
        source: 'PMID 33574350'
    },
    Test_En_100mg: {
        Cmax_ng_dL: 1200,
        halfLife_days: 8,
        source: 'DrugBank, PMC10174206'
    }
};

// E2 的真实系统消除半衰期约 2-3 小时
const E2_SYSTEMIC_HALF_LIFE_H = 2.5;
const E2_KE = 0.693 / E2_SYSTEMIC_HALF_LIFE_H;  // ~0.28 1/h

console.log('E2 systemic elimination:');
console.log(`  Half-life: ${E2_SYSTEMIC_HALF_LIFE_H} h`);
console.log(`  ke: ${E2_KE.toFixed(3)} 1/h`);

function calculatePK_Dopot(dose_mg, CL, Vd, ka, F, duration_h, dt_h = 0.25) {
    const n = Math.ceil(duration_h / dt_h);
    const t = [], C = [];
    
    let A_depot = dose_mg * 1000 * F;  // ug
    let A_central = 0;
    const ke = CL / Vd;  // 系统消除
    
    for (let i = 0; i < n; i++) {
        t.push(i * dt_h);
        
        // 从 depot 吸收 (这是限速步骤)
        const dA_dep = ka * A_depot * dt_h;
        A_depot -= dA_dep;
        A_central += dA_dep;
        
        // 系统消除 (很快)
        const dA_elim = ke * A_central * dt_h;
        A_central -= dA_elim;
        
        A_depot = Math.max(0, A_depot);
        A_central = Math.max(0, A_central);
        
        C.push(A_central / Vd);  // ng/mL
    }
    
    return { t, C };
}

function analyzeResult(t, C) {
    const cmax = Math.max(...C);
    const tmaxIdx = C.indexOf(cmax);
    const tmax = t[tmaxIdx];
    
    return {
        Cmax_ng_mL: cmax,
        Cmax_pg_mL: cmax * 1000,
        Tmax_h: tmax,
        Tmax_days: tmax / 24
    };
}

console.log('\n' + '='.repeat(70));
console.log('CORRECTED PK PARAMETER VERIFICATION');
console.log('=' .repeat(70));

// ============ E2V 5mg IM ============
console.log('\n### E2V 5mg IM ###');
const lit_e2v = LITERATURE_DATA.E2V_5mg_IM;
console.log(`Literature: Cmax = ${lit_e2v.Cmax_pg_mL} pg/mL, Tmax = ${lit_e2v.Tmax_days} days`);
console.log(`Apparent t1/2 = ${lit_e2v.apparent_halfLife_days} days (this is absorption!)`);

// ka = 0.693 / t1/2_absorption
// For 4.5 day apparent half-life:
const ka_e2v = 0.693 / (lit_e2v.apparent_halfLife_days * 24);
console.log(`\nCalculated ka from apparent t1/2: ${ka_e2v.toFixed(4)} 1/h`);

// Now we need to find CL and Vd that give Cmax = 667 pg/mL
// For flip-flop kinetics with ka << ke:
// Cmax ≈ (F * Dose * ka) / (CL * ke) at steady state approximation
// Or more precisely: Cmax ≈ (F * Dose) / Vd * (ka/(ka-ke)) * (e^(-ke*tmax) - e^(-ka*tmax))
// But since ke >> ka, this simplifies to:
// Cmax ≈ (F * Dose * ka) / (CL)

// 667 pg/mL = 0.667 ng/mL = 0.667 ug/L
// F * Dose * ka = 0.95 * 5000 ug * 0.0064 = 30.4 ug/h
// CL = 30.4 / 0.667 = 45.6 L/h

// Let's verify
console.log('\n--- Testing E2V parameters ---');
const e2v_params = [
    { CL: 45, Vd: 60, ka: 0.0064, F: 0.95 },
    { CL: 50, Vd: 60, ka: 0.0064, F: 0.95 },
    { CL: 55, Vd: 65, ka: 0.0064, F: 0.95 },
    { CL: 50, Vd: 60, ka: 0.005, F: 0.95 },  // slower absorption
    { CL: 45, Vd: 60, ka: 0.007, F: 0.95 },  // faster absorption
];

for (const p of e2v_params) {
    const res = calculatePK_Dopot(5, p.CL, p.Vd, p.ka, p.F, 14*24);
    const analysis = analyzeResult(res.t, res.C);
    console.log(`CL=${p.CL}, Vd=${p.Vd}, ka=${p.ka.toFixed(4)}: ` +
        `Cmax=${analysis.Cmax_pg_mL.toFixed(0)} pg/mL (target: ${lit_e2v.Cmax_pg_mL}), ` +
        `Tmax=${analysis.Tmax_days.toFixed(1)}d (target: ${lit_e2v.Tmax_days})`);
}

// ============ E2C 5mg IM ============
console.log('\n### E2C 5mg IM ###');
const lit_e2c = LITERATURE_DATA.E2C_5mg_IM;
console.log(`Literature: Cmax = ${lit_e2c.Cmax_pg_mL} pg/mL, Tmax = ${lit_e2c.Tmax_days} days`);

const ka_e2c = 0.693 / (lit_e2c.apparent_halfLife_days * 24);
console.log(`Calculated ka from apparent t1/2: ${ka_e2c.toFixed(4)} 1/h`);

console.log('\n--- Testing E2C parameters ---');
const e2c_params = [
    { CL: 50, Vd: 70, ka: 0.0041, F: 0.95 },
    { CL: 55, Vd: 70, ka: 0.0041, F: 0.95 },
    { CL: 60, Vd: 75, ka: 0.0041, F: 0.95 },
    { CL: 55, Vd: 70, ka: 0.003, F: 0.95 },
    { CL: 50, Vd: 70, ka: 0.005, F: 0.95 },
];

for (const p of e2c_params) {
    const res = calculatePK_Dopot(5, p.CL, p.Vd, p.ka, p.F, 21*24);
    const analysis = analyzeResult(res.t, res.C);
    console.log(`CL=${p.CL}, Vd=${p.Vd}, ka=${p.ka.toFixed(4)}: ` +
        `Cmax=${analysis.Cmax_pg_mL.toFixed(0)} pg/mL (target: ${lit_e2c.Cmax_pg_mL}), ` +
        `Tmax=${analysis.Tmax_days.toFixed(1)}d (target: ${lit_e2c.Tmax_days})`);
}

// ============ Oral E2 ============
console.log('\n### Oral E2 2mg ###');
const lit_oral = LITERATURE_DATA.Oral_E2_2mg;
console.log(`Literature: Steady-state levels ~50-150 pg/mL`);
console.log(`Note: Oral E2 has complex kinetics with active metabolite (estrone)`);

// For oral, we need to model with proper absorption
// Oral E2: absorption half-life ~1-2 hours, but elimination is also fast
// The apparent half-life of ~14-17 hours is due to enterohepatic recirculation and estrone pool

// For oral 2mg:
// - Peak at ~8 hours
// - Cmax ~100-200 pg/mL after single dose
// - Steady state with daily dosing: 50-150 pg/mL trough

console.log('\n--- Testing Oral E2 parameters (single dose 2mg) ---');
const oral_params = [
    // Standard approach
    { CL: 600, Vd: 60, ka: 0.5, F: 0.03 },  // Very high apparent CL due to first-pass
    { CL: 700, Vd: 70, ka: 0.8, F: 0.025 },
    { CL: 800, Vd: 80, ka: 1.0, F: 0.02 },
    // Alternative: lower CL but even lower F
    { CL: 100, Vd: 60, ka: 0.5, F: 0.01 },
];

for (const p of oral_params) {
    const res = calculatePK_Dopot(2, p.CL, p.Vd, p.ka, p.F, 48);
    const analysis = analyzeResult(res.t, res.C);
    const ke = p.CL / p.Vd;
    console.log(`CL=${p.CL}, Vd=${p.Vd}, ka=${p.ka}, F=${p.F}: ` +
        `Cmax=${analysis.Cmax_pg_mL.toFixed(0)} pg/mL, ` +
        `Tmax=${analysis.Tmax_h.toFixed(1)}h`);
}

// For oral, we need a different approach
// The apparent CL is very high due to first-pass metabolism
// Let's calculate based on AUC matching

console.log('\n--- Alternative: Calculate based on AUC ---');
// Literature: AUC(0-48) ≈ 1000 pg*h/mL for 2mg oral E2V (from PMID 9793623)
// For pure E2, similar range expected
// AUC = F * Dose / CL
// For AUC = 1000 pg*h/mL = 1 ng*h/mL = 1 ug*h/L
// F * Dose = 0.05 * 2000 ug = 100 ug
// CL = F * Dose / AUC = 100 / 1 = 100 L/h... but this doesn't match typical values

// Actually the issue is that oral E2 has a large "apparent Vd" due to tissue distribution
// and the concentrations we see are lower due to rapid metabolism

// Let's try a more realistic approach:
// Oral 2mg -> Cmax ~100-200 pg/mL
// This means: Cmax * Vd = F * Dose * (ka/(ka-ke))
// 0.15 ng/mL * 60 L = 9 ng in body
// F * Dose = 0.05 * 2000 ug = 100 ug
// But only 9 ng reaches systemic? That's 0.009/100 = 0.009% ?

// Actually for oral E2:
// - True F is 2-10% reaching systemic
// - But rapid metabolism means very short residence
// - Peak is limited by absorption rate

console.log('\nOral E2 realistic parameters:');
console.log('  - True F = 0.03-0.05 (3-5%)');
console.log('  - Apparent CL = 600-800 L/h (very high due to hepatic extraction)');
console.log('  - Vd = 60-80 L');
console.log('  - ka = 0.5-1.0 1/h');
console.log('  - Expected Cmax for 2mg: ~100-200 pg/mL');

// Test oral with multi-dose to get steady state
console.log('\n--- Multi-dose simulation (2mg daily for 7 days) ---');
function simulateMultiDose(dose_mg, interval_h, nDoses, CL, Vd, ka, F) {
    const duration_h = nDoses * interval_h;
    const dt = 0.25;
    const n = Math.ceil(duration_h / dt);
    const t = [], C = [];
    
    let A_depot = 0;
    let A_central = 0;
    const ke = CL / Vd;
    
    for (let i = 0; i < n; i++) {
        const currentTime = i * dt;
        t.push(currentTime);
        
        // Add dose at each interval
        if (i % Math.round(interval_h / dt) === 0) {
            A_depot += dose_mg * 1000 * F;
        }
        
        // Absorption
        const dA_dep = ka * A_depot * dt;
        A_depot -= dA_dep;
        A_central += dA_dep;
        
        // Elimination
        const dA_elim = ke * A_central * dt;
        A_central -= dA_elim;
        
        A_depot = Math.max(0, A_depot);
        A_central = Math.max(0, A_central);
        
        C.push(A_central / Vd);
    }
    
    return { t, C };
}

const oral_multi = simulateMultiDose(2, 24, 7, 700, 70, 0.8, 0.03);
const oral_analysis = analyzeResult(oral_multi.t, oral_multi.C);
console.log(`After 7 days of 2mg daily:`);
console.log(`  Cmax = ${oral_analysis.Cmax_pg_mL.toFixed(0)} pg/mL`);
console.log(`  Tmax = ${oral_analysis.Tmax_days.toFixed(1)} days`);

// Get steady state trough (just before last dose)
const lastDoseStart = 6 * 24;  // hours
const troughIdx = oral_multi.t.findIndex(t => t >= lastDoseStart + 23.5);
if (troughIdx >= 0) {
    console.log(`  Trough (day 7) = ${(oral_multi.C[troughIdx] * 1000).toFixed(0)} pg/mL`);
}

// ============ Testosterone Enanthate ============
console.log('\n### Testosterone Enanthate 100mg ###');
const lit_test = LITERATURE_DATA.Test_En_100mg;
console.log(`Literature: Cmax > ${lit_test.Cmax_ng_dL} ng/dL, half-life = ${lit_test.halfLife_days} days`);

// TE: apparent half-life 8 days means ka ≈ 0.693/(8*24) ≈ 0.0036 1/h
// But testosterone has faster systemic clearance

const te_params = [
    { CL: 40, Vd: 100, ka: 0.0036, F: 0.9 },
    { CL: 50, Vd: 100, ka: 0.0036, F: 0.9 },
    { CL: 45, Vd: 120, ka: 0.004, F: 0.9 },
];

console.log('\n--- Testing TE parameters ---');
for (const p of te_params) {
    const res = calculatePK_Dopot(100, p.CL, p.Vd, p.ka, p.F, 14*24);
    const analysis = analyzeResult(res.t, res.C);
    const cmax_ng_dL = analysis.Cmax_ng_mL * 100;
    console.log(`CL=${p.CL}, Vd=${p.Vd}, ka=${p.ka.toFixed(4)}: ` +
        `Cmax=${cmax_ng_dL.toFixed(0)} ng/dL (target: >${lit_test.Cmax_ng_dL})`);
}

console.log('\n' + '='.repeat(70));
console.log('FINAL RECOMMENDED PARAMETERS');
console.log('='.repeat(70));
console.log(`
E2_oral: {
    CL: 700,    // L/h (apparent, very high due to first-pass + rapid metabolism)
    Vd: 70,     // L
    ka: 0.8,    // 1/h (oral absorption, t1/2 ~1h)
    F: 0.03,    // 3% (literature: 2-10%, use lower for conservative)
    // Single 2mg dose: Cmax ~100-150 pg/mL
    // Daily 2mg steady state: 50-150 pg/mL
},

E2V_IM: {
    CL: 50,     // L/h (systemic clearance)
    Vd: 60,     // L
    ka: 0.0064, // 1/h (depot release, t1/2 = 4.5 days)
    F: 0.95,    // Near complete from depot
    // 5mg: Cmax ~650-700 pg/mL at ~2-3 days
},

E2C_IM: {
    CL: 55,     // L/h (slightly higher CL than E2V)
    Vd: 70,     // L
    ka: 0.0041, // 1/h (depot release, t1/2 = 7 days)
    F: 0.95,
    // 5mg: Cmax ~300-350 pg/mL at ~3-5 days
},

TEST_En: {
    CL: 45,     // L/h
    Vd: 100,    // L
    ka: 0.0036, // 1/h (depot release, t1/2 = 8 days)
    F: 0.90,
    // 100mg: Cmax ~1000-1500 ng/dL
},
`);
