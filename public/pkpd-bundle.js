"use strict";
var PKPD = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // src/lib/index.ts
  var index_exports = {};
  __export(index_exports, {
    ANDROGENS: () => ANDROGENS,
    CYP3A4_FACTORS: () => CYP3A4_FACTORS,
    DRUG_DB: () => DRUG_DB,
    ESTROGENS: () => ESTROGENS,
    OneCompartmentModel: () => OneCompartmentModel,
    PKPDSimulator: () => PKPDSimulator,
    PROGESTOGENS: () => PROGESTOGENS
  });

  // src/lib/pkpd/solvers/EulerMaruyama.ts
  var EulerMaruyamaSolver = class {
    constructor(sigma) {
      this.sigma = sigma;
    }
    step(state, CL, Vd, ka, dt, dW) {
      const ke = CL / Vd;
      const dA_dep = ka * state.A_depot * dt;
      let A_depot = state.A_depot - dA_dep;
      let A_central = state.A_central + dA_dep;
      const dA_elim = ke * A_central * dt;
      A_central -= dA_elim;
      if (this.sigma > 0) {
        const ito_correction = -0.5 * this.sigma * this.sigma * dt;
        A_central *= Math.exp(this.sigma * dW + ito_correction);
      }
      return {
        A_depot: Math.max(0, A_depot),
        A_central: Math.max(0, A_central)
      };
    }
  };

  // src/lib/pkpd/solvers/Symplectic.ts
  var SymplecticSolver = class {
    constructor(sigma) {
      this.sigma = sigma;
    }
    step(state, CL, Vd, ka, dt, dW) {
      const ke = CL / Vd;
      const A_central_half = state.A_central * Math.exp(-ke * dt / 2);
      const dA_dep = ka * state.A_depot * dt;
      const A_depot_new = state.A_depot - dA_dep;
      const A_central_mid = A_central_half + dA_dep;
      const A_central_new = A_central_mid * Math.exp(-ke * dt / 2);
      return {
        A_depot: Math.max(0, A_depot_new),
        A_central: Math.max(0, A_central_new)
      };
    }
  };

  // src/lib/pkpd/models/OneCompartment.ts
  var OneCompartmentModel = class {
    constructor(drug, sdeConfig) {
      this.drug = drug;
      this.CL = drug.CL;
      this.Vd = drug.Vd;
      this.ka = drug.ka;
      this.F = drug.F;
      this.solver = sdeConfig.model === "symplectic" ? new SymplecticSolver(sdeConfig.sigma) : new EulerMaruyamaSolver(sdeConfig.sigma);
    }
    randn() {
      const u1 = Math.random();
      const u2 = Math.random();
      return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }
    toUnit(val) {
      if (this.drug.unit === "pg/mL") return val * 1e3;
      if (this.drug.unit === "ng/dL") return val * 100;
      return val;
    }
    simulateMultiDose(dose_mg, interval_h, nDoses, totalDuration_h, dt_h) {
      const n = Math.ceil(totalDuration_h / dt_h);
      const t = [];
      const C = [];
      let state = { A_depot: 0, A_central: 0 };
      const doseTimes = [];
      for (let d = 0; d < nDoses; d++) {
        doseTimes.push(d * interval_h);
      }
      let lastDoseIdx = -1;
      for (let i = 0; i < n; i++) {
        const currentTime = i * dt_h;
        t.push(currentTime);
        const doseIdx = doseTimes.findIndex((dt) => Math.abs(currentTime - dt) < dt_h / 2);
        if (doseIdx !== -1 && doseIdx > lastDoseIdx) {
          state.A_depot += dose_mg * 1e3 * this.F;
          lastDoseIdx = doseIdx;
        }
        const dW = this.randn() * Math.sqrt(dt_h);
        state = this.solver.step(state, this.CL, this.Vd, this.ka, dt_h, dW);
        const conc = this.toUnit(state.A_central / this.Vd);
        C.push(conc);
      }
      const steadyStateStart = Math.floor(C.length * 0.75);
      const steadyStateC = C.slice(steadyStateStart);
      const validC = steadyStateC.filter((c) => c > 1e-4);
      const Cmax = Math.max(...steadyStateC);
      const Cmin = validC.length > 0 ? Math.min(...validC) : 0;
      const Tmax = t[steadyStateStart + steadyStateC.indexOf(Cmax)];
      const AUC = C.reduce((sum, c, i) => i === 0 ? 0 : sum + (C[i] + C[i - 1]) * dt_h / 2, 0);
      return { t, C, Cmax, Cmin, Tmax, AUC };
    }
  };

  // src/lib/gpu/GPUODESolver.ts
  var GPUODESolver = class {
    constructor() {
      this.device = null;
      this.supported = false;
    }
    async init() {
      if (!navigator.gpu) {
        return false;
      }
      try {
        const adapter = await navigator.gpu.requestAdapter();
        if (!adapter) return false;
        this.device = await adapter.requestDevice();
        this.supported = true;
        return true;
      } catch (e) {
        console.warn("WebGPU initialization failed:", e);
        return false;
      }
    }
    isSupported() {
      return this.supported && this.device !== null;
    }
    async solvePK(config) {
      if (!this.device) {
        return { Cmax: new Float32Array(0), Cmin: new Float32Array(0), success: false };
      }
      const shader = `
      @group(0) @binding(0) var<storage, read> params: array<f32>;
      @group(0) @binding(1) var<storage, read_write> results: array<f32>;

      @compute @workgroup_size(64)
      fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
        let idx = global_id.x;
        let numSims = u32(params[9]);

        if (idx >= numSims) { return; }

        let CL = params[0];
        let Vd = params[1];
        let ka = params[2];
        let F = params[3];
        let dose = params[4];
        let interval = params[5];
        let duration = params[6];
        let dt = params[7];
        let sigma = params[8];

        let ke = CL / Vd;
        let n = u32(duration / dt);
        let nDoses = u32(duration / interval);

        var A_depot = 0.0;
        var A_central = 0.0;
        var Cmax = 0.0;
        var Cmin = 1e10;

        var seed = f32(idx) * 12345.0;

        for (var i = 0u; i < n; i++) {
          let t = f32(i) * dt;

          for (var d = 0u; d < nDoses; d++) {
            if (abs(t - f32(d) * interval) < dt / 2.0) {
              A_depot += dose * 1000.0 * F;
            }
          }

          seed = fract(sin(seed) * 43758.5453);
          let dW = (seed - 0.5) * 2.0 * sqrt(dt);

          let dA_dep = ka * A_depot * dt;
          A_depot -= dA_dep;
          A_central += dA_dep;

          let dA_elim = ke * A_central * dt;
          A_central -= dA_elim;

          if (sigma > 0.0) {
            let ito = -0.5 * sigma * sigma * dt;
            A_central *= exp(sigma * dW + ito);
          }

          A_depot = max(0.0, A_depot);
          A_central = max(0.0, A_central);

          let C = A_central / Vd * 1000.0;

          if (i > n / 2u) {
            Cmax = max(Cmax, C);
            if (C > 0.0001) {
              Cmin = min(Cmin, C);
            }
          }
        }

        results[idx * 2u] = Cmax;
        results[idx * 2u + 1u] = Cmin;
      }
    `;
      const shaderModule = this.device.createShaderModule({ code: shader });
      const paramsData = new Float32Array([
        config.CL,
        config.Vd,
        config.ka,
        config.F,
        config.dose,
        config.interval,
        config.duration,
        config.dt,
        config.sigma,
        config.numSims
      ]);
      const paramsBuffer = this.device.createBuffer({
        size: paramsData.byteLength,
        usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST
      });
      this.device.queue.writeBuffer(paramsBuffer, 0, paramsData);
      const resultsBuffer = this.device.createBuffer({
        size: config.numSims * 2 * 4,
        usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_SRC
      });
      const readBuffer = this.device.createBuffer({
        size: config.numSims * 2 * 4,
        usage: GPUBufferUsage.MAP_READ | GPUBufferUsage.COPY_DST
      });
      const bindGroupLayout = this.device.createBindGroupLayout({
        entries: [
          { binding: 0, visibility: GPUShaderStage.COMPUTE, buffer: { type: "read-only-storage" } },
          { binding: 1, visibility: GPUShaderStage.COMPUTE, buffer: { type: "storage" } }
        ]
      });
      const bindGroup = this.device.createBindGroup({
        layout: bindGroupLayout,
        entries: [
          { binding: 0, resource: { buffer: paramsBuffer } },
          { binding: 1, resource: { buffer: resultsBuffer } }
        ]
      });
      const pipelineLayout = this.device.createPipelineLayout({
        bindGroupLayouts: [bindGroupLayout]
      });
      const pipeline = this.device.createComputePipeline({
        layout: pipelineLayout,
        compute: { module: shaderModule, entryPoint: "main" }
      });
      const commandEncoder = this.device.createCommandEncoder();
      const passEncoder = commandEncoder.beginComputePass();
      passEncoder.setPipeline(pipeline);
      passEncoder.setBindGroup(0, bindGroup);
      passEncoder.dispatchWorkgroups(Math.ceil(config.numSims / 64));
      passEncoder.end();
      commandEncoder.copyBufferToBuffer(resultsBuffer, 0, readBuffer, 0, config.numSims * 2 * 4);
      this.device.queue.submit([commandEncoder.finish()]);
      await readBuffer.mapAsync(GPUMapMode.READ);
      const resultData = new Float32Array(readBuffer.getMappedRange());
      const Cmax = new Float32Array(config.numSims);
      const Cmin = new Float32Array(config.numSims);
      for (let i = 0; i < config.numSims; i++) {
        Cmax[i] = resultData[i * 2];
        Cmin[i] = resultData[i * 2 + 1];
      }
      readBuffer.unmap();
      return { Cmax, Cmin, success: true };
    }
  };

  // src/lib/pkpd/simulator.ts
  var PKPDSimulator = class {
    constructor() {
      this.gpuSolver = null;
      this.gpuInitialized = false;
    }
    async initGPU() {
      if (this.gpuInitialized) return this.gpuSolver?.isSupported() || false;
      this.gpuSolver = new GPUODESolver();
      this.gpuInitialized = await this.gpuSolver.init();
      return this.gpuInitialized;
    }
    isGPUAvailable() {
      return this.gpuSolver?.isSupported() || false;
    }
    simulate(config) {
      const model = new OneCompartmentModel(config.drug, config.sde);
      return model.simulateMultiDose(
        config.dose,
        config.interval,
        Math.ceil(config.duration / config.interval),
        config.duration,
        config.dt
      );
    }
    async monteCarloSimulation(config, numSims = 100) {
      if (this.gpuSolver?.isSupported()) {
        try {
          const gpuResult = await this.gpuSolver.solvePK({
            CL: config.drug.CL,
            Vd: config.drug.Vd,
            ka: config.drug.ka,
            F: config.drug.F,
            dose: config.dose,
            interval: config.interval,
            duration: config.duration,
            dt: config.dt,
            sigma: config.sde.sigma,
            numSims
          });
          if (gpuResult.success) {
            const results2 = [];
            for (let i = 0; i < numSims; i++) {
              results2.push({
                t: [],
                C: [],
                Cmax: gpuResult.Cmax[i],
                Cmin: gpuResult.Cmin[i],
                Tmax: 0,
                AUC: 0
              });
            }
            return results2;
          }
        } catch (e) {
          console.warn("GPU simulation failed, falling back to CPU:", e);
        }
      }
      const results = [];
      for (let i = 0; i < numSims; i++) {
        results.push(this.simulate(config));
      }
      return results;
    }
    calculateStatistics(results) {
      const allCmax = results.map((r) => r.Cmax);
      const allCmin = results.map((r) => r.Cmin);
      const allAUC = results.map((r) => r.AUC);
      const percentile = (arr, p) => {
        const sorted = [...arr].sort((a, b) => a - b);
        const idx = Math.floor(sorted.length * p / 100);
        return sorted[idx];
      };
      return {
        Cmax: {
          median: percentile(allCmax, 50),
          p25: percentile(allCmax, 25),
          p75: percentile(allCmax, 75)
        },
        Cmin: {
          median: percentile(allCmin, 50),
          p25: percentile(allCmin, 25),
          p75: percentile(allCmin, 75)
        },
        AUC: {
          median: percentile(allAUC, 50),
          p25: percentile(allAUC, 25),
          p75: percentile(allAUC, 75)
        }
      };
    }
  };

  // src/lib/drugs/estrogens.ts
  var ESTROGENS = {
    E2_oral: {
      name: "Estradiol Oral",
      therapeutic: [50, 200],
      unit: "pg/mL",
      CL: 60,
      Vd: 60,
      ka: 0.04,
      F: 0.03,
      halfLife: 1,
      halfLifeApparent: 17,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 2,
      defaultInterval: 12,
      ref: "PMID: 1548642 | PMC7878477"
    },
    E2V_oral: {
      name: "Estradiol Valerate Oral",
      therapeutic: [50, 200],
      unit: "pg/mL",
      CL: 60,
      Vd: 60,
      ka: 0.04,
      F: 0.03,
      halfLife: 1,
      halfLifeApparent: 17,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 2,
      defaultInterval: 12,
      ref: "PMID: 1548642 | PMID 9793623"
    },
    E2_subl: {
      name: "Estradiol Sublingual",
      therapeutic: [50, 200],
      unit: "pg/mL",
      CL: 15,
      Vd: 150,
      ka: 2.5,
      F: 0.1,
      halfLife: 12,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 2,
      defaultInterval: 12,
      ref: "PMID: 9052581"
    },
    E2_td: {
      name: "Estradiol Transdermal Patch",
      therapeutic: [50, 200],
      unit: "pg/mL",
      CL: 10,
      Vd: 150,
      ka: 0.03,
      F: 0.85,
      halfLife: 36,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 0.05,
      defaultInterval: 84,
      ref: "PMID: 9689205"
    },
    E2_td_gel: {
      name: "Estradiol Transdermal Gel",
      therapeutic: [10, 50],
      unit: "pg/mL",
      CL: 10,
      Vd: 120,
      ka: 0.05,
      F: 0.9,
      halfLife: 30,
      doseUnit: "\u03BCg",
      intervalUnit: "h",
      defaultDose: 0.72,
      defaultInterval: 24,
      ref: "Clinical pharmacokinetics"
    },
    E2V: {
      name: "Estradiol Valerate IM",
      therapeutic: [100, 400],
      unit: "pg/mL",
      CL: 100,
      Vd: 2400,
      ka: 0.012,
      F: 0.85,
      halfLife: 120,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 5,
      defaultInterval: 168,
      ref: "TfS Meta-analysis | PMID: 7169965"
    },
    E2C: {
      name: "Estradiol Cypionate IM",
      therapeutic: [100, 400],
      unit: "pg/mL",
      CL: 120,
      Vd: 2800,
      ka: 6e-3,
      F: 0.9,
      halfLife: 192,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 5,
      defaultInterval: 168,
      ref: "TfS Meta-analysis | PMID: 7389356"
    },
    E2E: {
      name: "Estradiol Enanthate IM",
      therapeutic: [100, 400],
      unit: "pg/mL",
      CL: 5,
      Vd: 2500,
      ka: 4e-3,
      F: 0.92,
      halfLife: 240,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 5,
      defaultInterval: 168,
      ref: "TfS Meta-analysis"
    }
  };

  // src/lib/drugs/progestogens.ts
  var PROGESTOGENS = {
    MPA_oral: {
      name: "Medroxyprogesterone Acetate Oral",
      therapeutic: [0.5, 3],
      unit: "ng/mL",
      CL: 20,
      Vd: 35,
      ka: 1.2,
      F: 0.95,
      halfLife: 30,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 10,
      defaultInterval: 24,
      cyp3a4: true,
      isProgestogen: true,
      hillEnzyme: {
        enzyme: "CYP3A4",
        Ki: 2.5,
        IC50: 5,
        hillCoef: 1,
        mechanism: "substrate_weak_inhibitor"
      },
      ref: "DrugBank DB00603"
    },
    CPA_oral: {
      name: "Cyproterone Acetate Oral",
      therapeutic: [20, 300],
      unit: "ng/mL",
      CL: 5,
      Vd: 3,
      ka: 0.8,
      F: 0.88,
      halfLife: 60,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 25,
      defaultInterval: 24,
      cyp3a4: true,
      isProgestogen: true,
      hillEnzyme: {
        enzyme: "CYP3A4",
        Ki: 0.8,
        IC50: 1.5,
        hillCoef: 1.2,
        mechanism: "substrate_moderate_inhibitor"
      },
      ref: "PMID: 8131397 | DrugBank DB04839"
    }
  };
  var ANDROGENS = {
    TEST_En: {
      name: "Testosterone Enanthate IM",
      therapeutic: [300, 1e3],
      unit: "ng/dL",
      CL: 80,
      Vd: 1900,
      ka: 0.015,
      F: 0.65,
      halfLife: 96,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 100,
      defaultInterval: 168,
      ref: "PMC4721027 | PMC9293229"
    },
    TEST_Cy: {
      name: "Testosterone Cypionate IM",
      therapeutic: [300, 1e3],
      unit: "ng/dL",
      CL: 6,
      Vd: 900,
      ka: 0.012,
      F: 0.65,
      halfLife: 120,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 100,
      defaultInterval: 168,
      ref: "Clinical data"
    }
  };

  // src/lib/drugs/interactions.ts
  var CYP3A4_FACTORS = {
    none: { name: "No Interaction", factor: 1, desc: "Normal metabolism" },
    inhibitor_mild: { name: "Mild Inhibitor", factor: 0.85, desc: "Grapefruit, Cimetidine" },
    inhibitor_moderate: { name: "Moderate Inhibitor", factor: 0.7, desc: "Fluconazole, Erythromycin" },
    inhibitor_strong: { name: "Strong Inhibitor", factor: 0.5, desc: "Ketoconazole, Ritonavir" },
    inducer_mild: { name: "Mild Inducer", factor: 1.3, desc: "Modafinil" },
    inducer_moderate: { name: "Moderate Inducer", factor: 1.6, desc: "Efavirenz, Nevirapine" },
    inducer_strong: { name: "Strong Inducer", factor: 2.5, desc: "Rifampin, Carbamazepine" }
  };

  // src/lib/drugs/index.ts
  var DRUG_DB = {
    ...ESTROGENS,
    ...PROGESTOGENS,
    ...ANDROGENS
  };
  return __toCommonJS(index_exports);
})();
//# sourceMappingURL=pkpd-bundle.js.map
