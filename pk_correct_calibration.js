// 最终校准 - 正确处理 depot 制剂的表观参数
// 
// 关键理解:
// 1. Depot 注射的 Cmax = (F * Dose / Vd_apparent) * f_flipflop
// 2. Vd_apparent 对于 depot 制剂非常大（因为吸收期间消除也在进行）
// 3. 实际上 F * Dose 需要乘以一个 "bioavailability factor" 来补偿

const TF_DATA = {
    single_5mg: {
        E2V: { Tmax: 2.1, Cmax: 295, t1_2: 3.0, AUC: 1886 },
        E2C: { Tmax: 4.3, Cmax: 155, t1_2: 6.7, AUC: 2150 },
    },
    steady_state_5mg_7d: {
        E2V: { Tmax: 1.9, Cmax: 384, Cmin: 142, Cavg: 269 },
        E2C: { Tmax: 3.1, Cmax: 339, Cmin: 262, Cavg: 307 },
    }
};

// 简化一室模型 - 正确的参数关系
function oneCompartmentModel(dose_mg, params, duration_h, dt = 0.1) {
    const { ka, ke, Vd, F } = params;
    
    const n = Math.ceil(duration_h / dt);
    const t = [], C = [];
    
    let A_depot = dose_mg * 1000 * F;  // ug
    let A_central = 0;  // ug
    
    for (let i = 0; i < n; i++) {
        t.push(i * dt);
        
        // 从 depot 吸收
        const dA_depot = ka * A_depot * dt;
        A_depot -= dA_depot;
        A_central += dA_depot;
        
        // 消除
        const dA_elim = ke * A_central * dt;
        A_central -= dA_elim;
        
        A_depot = Math.max(0, A_depot);
        A_central = Math.max(0, A_central);
        
        // ng/mL = ug/L
        C.push(A_central / Vd);
    }
    
    return { t, C };
}

function analyze(t, C) {
    const cmax = Math.max(...C);
    const tmaxIdx = C.indexOf(cmax);
    const tmax = t[tmaxIdx];
    
    let auc = 0;
    for (let i = 1; i < t.length; i++) {
        auc += (t[i] - t[i-1]) * (C[i] + C[i-1]) / 2;
    }
    
    return {
        Cmax_ng_mL: cmax,
        Cmax_pg_mL: cmax * 1000,
        Tmax_h: tmax,
        Tmax_d: tmax / 24,
        AUC_ng_h_mL: auc,
        AUC_pg_d_mL: auc * 1000 / 24
    };
}

// 数学关系:
// 对于 flip-flop 动力学 (ka << ke):
// Tmax ≈ ln(ke/ka) / (ke - ka) ≈ ln(ke/ka) / ke  (当 ke >> ka)
// Cmax ≈ (F * Dose * ka / Vd) * e^(-ka * Tmax)
//
// 如果 Tmax = 2.1d = 50.4h, t1/2 = 3d = 72h:
// ka = 0.693 / 72 = 0.0096 1/h
// 
// 对于 Tmax:
// Tmax ≈ ln(ke/ka) / ke = 50.4
// ln(ke/ka) = 50.4 * ke
// ke/ka = e^(50.4 * ke)
//
// 这需要迭代求解。简化方法:
// 假设 ke >> ka (flip-flop), 则 Tmax ≈ ln(ke/ka) / ke
// 如果 Tmax = 50.4h, ka = 0.0096:
// 50.4 = ln(ke/0.0096) / ke
// 尝试 ke = 0.5: ln(0.5/0.0096)/0.5 = ln(52)/0.5 = 3.95/0.5 = 7.9h (太短)
// 尝试 ke = 0.1: ln(0.1/0.0096)/0.1 = ln(10.4)/0.1 = 2.34/0.1 = 23.4h (太短)
// 尝试 ke = 0.05: ln(0.05/0.0096)/0.05 = ln(5.2)/0.05 = 1.65/0.05 = 33h (仍短)
//
// 问题: 如果 ke 真的 >> ka, Tmax 会很短
// 但文献 Tmax = 2.1d, 这意味着 ke 和 ka 比较接近
//
// 实际上, 对于 depot 注射, 有效消除率 ke_eff = ka * f_systemic
// 因为只有部分药物在吸收后立即被消除

console.log('='.repeat(70));
console.log('CORRECTED PARAMETER DERIVATION');
console.log('='.repeat(70));

// 使用 AUC 匹配来反推参数
// AUC = F * Dose / (Vd * ke) 对于一室模型
// TF_DATA: AUC = 1886 pg*d/mL = 1886 ng*h/mL * 24 = 45264 ng*h/mL
// 但这是总 AUC, 需要正确换算

console.log('\n### AUC-based parameter derivation ###');
console.log(`E2V Target AUC: ${TF_DATA.single_5mg.E2V.AUC} pg*d/mL`);

// AUC = (F * Dose) / CL = (F * Dose) / (ke * Vd)
// 对于 depot: AUC = (F * Dose) / CL
// 5mg = 5000 ug
// AUC = 1886 pg*d/mL = 1886 ng*h/L * 24 = 45264 ng*h/L
// 45264 = F * 5000 / CL
// CL = F * 5000 / 45264 = F * 0.11 L/h = F * 2.64 L/d
// 如果 F = 1, CL = 0.11 L/h

// 但这太小了。问题在于单位换算。
// AUC = 1886 pg*d/mL = 1886 pg*h/mL * 24
// 对于 E2, 1 pg/mL = 0.001 ng/mL
// AUC = 1886 * 24 * 0.001 = 45.26 ng*h/mL = 45.26 ug*h/L
// CL = F * Dose / AUC = 5000 / 45.26 = 110 L/h

console.log(`Dose: 5mg = 5000 ug`);
console.log(`AUC: ${TF_DATA.single_5mg.E2V.AUC} pg*d/mL = ${TF_DATA.single_5mg.E2V.AUC * 24 / 1000} ug*h/L`);
console.log(`Calculated CL: ${5000 / (TF_DATA.single_5mg.E2V.AUC * 24 / 1000)} L/h`);

// 但 Tmax = 2.1d 意味着 ka 和 ke 的关系需要调整
// 对于一室模型: Tmax = ln(ka/ke) / (ka - ke)

console.log('\n### Tmax-based parameter derivation ###');
const target_tmax_h = TF_DATA.single_5mg.E2V.Tmax * 24;
console.log(`Target Tmax: ${target_tmax_h} h`);

// 假设 ka = 0.693 / (t1/2 * 24) = 0.693 / 72 = 0.0096 1/h
const ka_e2v = 0.693 / (TF_DATA.single_5mg.E2V.t1_2 * 24);
console.log(`ka (from t1/2): ${ka_e2v.toFixed(4)} 1/h`);

// 找 ke 使得 Tmax 匹配
function findTmax(ka, ke) {
    return Math.log(ka / ke) / (ka - ke);
}

// 使用二分法找正确的 ke
let ke_low = 0.001, ke_high = 1;
for (let iter = 0; iter < 50; iter++) {
    const ke_mid = (ke_low + ke_high) / 2;
    const tmax_calc = findTmax(ka_e2v, ke_mid);
    if (tmax_calc > target_tmax_h) {
        ke_low = ke_mid;
    } else {
        ke_high = ke_mid;
    }
}
const ke_e2v = (ke_low + ke_high) / 2;
console.log(`ke (from Tmax): ${ke_e2v.toFixed(4)} 1/h`);
console.log(`Verified Tmax: ${findTmax(ka_e2v, ke_e2v).toFixed(1)} h`);

// 现在用 Cmax 反推 Vd
// Cmax = (F * Dose / Vd) * (ka / (ka - ke)) * (e^(-ke * Tmax) - e^(-ka * Tmax))
const target_cmax_ng = TF_DATA.single_5mg.E2V.Cmax / 1000;  // pg/mL -> ng/mL
const tmax_calc = findTmax(ka_e2v, ke_e2v);
const cmax_factor = (ka_e2v / (ka_e2v - ke_e2v)) * 
    (Math.exp(-ke_e2v * tmax_calc) - Math.exp(-ka_e2v * tmax_calc));
const Vd_e2v = (1 * 5000 / target_cmax_ng) * cmax_factor;
console.log(`\nVd (from Cmax): ${Vd_e2v.toFixed(0)} L`);

// 验证
console.log('\n### E2V VERIFICATION ###');
const e2v_test = oneCompartmentModel(5, {
    ka: ka_e2v,
    ke: ke_e2v,
    Vd: Vd_e2v,
    F: 1
}, 14 * 24);
const e2v_result = analyze(e2v_test.t, e2v_test.C);
console.log(`Target: Cmax = ${TF_DATA.single_5mg.E2V.Cmax} pg/mL, Tmax = ${TF_DATA.single_5mg.E2V.Tmax} d`);
console.log(`Result: Cmax = ${e2v_result.Cmax_pg_mL.toFixed(0)} pg/mL, Tmax = ${e2v_result.Tmax_d.toFixed(1)} d`);

// E2C
console.log('\n### E2C DERIVATION ###');
const ka_e2c = 0.693 / (TF_DATA.single_5mg.E2C.t1_2 * 24);
const target_tmax_e2c = TF_DATA.single_5mg.E2C.Tmax * 24;

ke_low = 0.0001; ke_high = 0.5;
for (let iter = 0; iter < 50; iter++) {
    const ke_mid = (ke_low + ke_high) / 2;
    const tmax_calc = findTmax(ka_e2c, ke_mid);
    if (tmax_calc > target_tmax_e2c) {
        ke_low = ke_mid;
    } else {
        ke_high = ke_mid;
    }
}
const ke_e2c = (ke_low + ke_high) / 2;
const tmax_e2c = findTmax(ka_e2c, ke_e2c);
const target_cmax_e2c = TF_DATA.single_5mg.E2C.Cmax / 1000;
const cmax_factor_e2c = (ka_e2c / (ka_e2c - ke_e2c)) * 
    (Math.exp(-ke_e2c * tmax_e2c) - Math.exp(-ka_e2c * tmax_e2c));
const Vd_e2c = (1 * 5000 / target_cmax_e2c) * cmax_factor_e2c;

console.log(`ka: ${ka_e2c.toFixed(4)} 1/h`);
console.log(`ke: ${ke_e2c.toFixed(4)} 1/h`);
console.log(`Vd: ${Vd_e2c.toFixed(0)} L`);

const e2c_test = oneCompartmentModel(5, {
    ka: ka_e2c,
    ke: ke_e2c,
    Vd: Vd_e2c,
    F: 1
}, 21 * 24);
const e2c_result = analyze(e2c_test.t, e2c_test.C);
console.log(`Target: Cmax = ${TF_DATA.single_5mg.E2C.Cmax} pg/mL, Tmax = ${TF_DATA.single_5mg.E2C.Tmax} d`);
console.log(`Result: Cmax = ${e2c_result.Cmax_pg_mL.toFixed(0)} pg/mL, Tmax = ${e2c_result.Tmax_d.toFixed(1)} d`);

// 最终参数输出
console.log('\n' + '='.repeat(70));
console.log('FINAL CALIBRATED PARAMETERS');
console.log('='.repeat(70));

console.log(`
E2V_IM: {
    CL: ${(ke_e2v * Vd_e2v).toFixed(1)},  // L/h (CL = ke * Vd)
    Vd: ${Vd_e2v.toFixed(0)},   // L
    ka: ${ka_e2v.toFixed(4)},   // 1/h (吸收半衰期 ${TF_DATA.single_5mg.E2V.t1_2}d)
    F: 1,
    halfLife: ${TF_DATA.single_5mg.E2V.t1_2 * 24}, // h
    therapeutic: [100, 200],  // pg/mL
    // 预期: 5mg -> Cmax ~${TF_DATA.single_5mg.E2V.Cmax} pg/mL, Tmax ~${TF_DATA.single_5mg.E2V.Tmax}d
},

E2C_IM: {
    CL: ${(ke_e2c * Vd_e2c).toFixed(1)},  // L/h
    Vd: ${Vd_e2c.toFixed(0)},   // L
    ka: ${ka_e2c.toFixed(4)},   // 1/h (吸收半衰期 ${TF_DATA.single_5mg.E2C.t1_2}d)
    F: 1,
    halfLife: ${TF_DATA.single_5mg.E2C.t1_2 * 24}, // h
    therapeutic: [100, 200],
    // 预期: 5mg -> Cmax ~${TF_DATA.single_5mg.E2C.Cmax} pg/mL, Tmax ~${TF_DATA.single_5mg.E2C.Tmax}d
},
`);
