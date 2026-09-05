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
    BayesianEstimator: () => BayesianEstimator,
    DRUG_DB: () => DRUG_DB,
    OneCompartmentModel: () => OneCompartmentModel,
    PKPDSimulator: () => PKPDSimulator,
    PROGESTOGENS: () => PROGESTOGENS,
    calculateChainDiagnostics: () => calculateChainDiagnostics
  });

  // src/lib/pkpd/solvers/ExactLinear.ts
  var RATE_EPSILON = 1e-10;
  var ExactLinearSolver = class {
    step(state, parameters) {
      const { CL, Vd, ka, F, dt } = parameters;
      if (dt < 0 || CL <= 0 || Vd <= 0 || ka <= 0 || F <= 0 || F > 1) {
        throw new RangeError("ExactLinearSolver requires dt >= 0, positive PK rates, and 0 < F <= 1");
      }
      if (dt === 0) return { ...state };
      const ke = CL / Vd;
      const siteBefore = state.A_site;
      const centralBefore = state.A_central;
      const expKa = Math.exp(-ka * dt);
      const expKe = Math.exp(-ke * dt);
      const A_site = siteBefore * expKa;
      const released = siteBefore - A_site;
      const centralInput = Math.abs(ka - ke) < RATE_EPSILON ? F * ka * siteBefore * dt * expKe : F * ka * siteBefore * (expKa - expKe) / (ke - ka);
      const A_central = centralBefore * expKe + centralInput;
      const unavailableIncrement = (1 - F) * released;
      const eliminatedIncrement = centralBefore + F * released - A_central;
      return {
        A_site: Math.max(0, A_site),
        A_central: Math.max(0, A_central),
        A_unavailable: state.A_unavailable + Math.max(0, unavailableIncrement),
        A_eliminated: state.A_eliminated + Math.max(0, eliminatedIncrement),
        A_administered: state.A_administered
      };
    }
  };

  // src/lib/pkpd/models/OneCompartment.ts
  var TIME_EPSILON = 1e-9;
  var emptyState = () => ({
    A_site: 0,
    A_central: 0,
    A_unavailable: 0,
    A_eliminated: 0,
    A_administered: 0
  });
  var trapezoid = (t, y) => {
    let area = 0;
    for (let i = 1; i < t.length; i++) {
      area += (t[i] - t[i - 1]) * (y[i] + y[i - 1]) / 2;
    }
    return area;
  };
  var OneCompartmentModel = class {
    constructor(drug, parameterOverride = {}) {
      this.drug = drug;
      this.solver = new ExactLinearSolver();
      this.parameters = {
        CL: parameterOverride.CL ?? drug.CL,
        Vd: parameterOverride.Vd ?? drug.Vd,
        ka: parameterOverride.ka ?? drug.ka,
        F: parameterOverride.F ?? drug.F,
        activeMoietyFraction: parameterOverride.activeMoietyFraction ?? drug.activeMoietyFraction ?? 1
      };
      this.validateParameters();
    }
    validateParameters() {
      const { CL, Vd, ka, F, activeMoietyFraction } = this.parameters;
      if (!Number.isFinite(CL) || CL <= 0) throw new RangeError("CL must be positive");
      if (!Number.isFinite(Vd) || Vd <= 0) throw new RangeError("Vd must be positive");
      if (!Number.isFinite(ka) || ka <= 0) throw new RangeError("ka must be positive");
      if (!Number.isFinite(F) || F <= 0 || F > 1) throw new RangeError("F must be in (0, 1]");
      if (!Number.isFinite(activeMoietyFraction) || activeMoietyFraction <= 0 || activeMoietyFraction > 1) {
        throw new RangeError("activeMoietyFraction must be in (0, 1]");
      }
    }
    toDisplayUnit(concentrationNgPerMl) {
      if (this.drug.unit === "pg/mL") return concentrationNgPerMl * 1e3;
      if (this.drug.unit === "ng/dL") return concentrationNgPerMl * 100;
      return concentrationNgPerMl;
    }
    applyDose(state, doseMg) {
      const activeDoseMicrograms = doseMg * 1e3 * this.parameters.activeMoietyFraction;
      const next = {
        ...state,
        A_administered: state.A_administered + activeDoseMicrograms
      };
      if (this.drug.route === "intravenous-bolus") {
        return {
          ...next,
          A_central: next.A_central + activeDoseMicrograms * this.parameters.F,
          A_unavailable: next.A_unavailable + activeDoseMicrograms * (1 - this.parameters.F)
        };
      }
      return { ...next, A_site: next.A_site + activeDoseMicrograms };
    }
    advance(state, dt) {
      if (dt <= TIME_EPSILON) return state;
      return this.solver.step(state, { ...this.parameters, dt });
    }
    /**
     * Exact superposition at arbitrary observation times. This avoids rounding
     * blood-sample times onto an integration grid inside the Bayesian module.
     */
    predictAtTimes(doseMg, interval, nDoses, times) {
      if (!Number.isFinite(doseMg) || doseMg <= 0) throw new RangeError("dose must be positive");
      if (!Number.isFinite(interval) || interval <= 0) throw new RangeError("interval must be positive");
      if (!Number.isInteger(nDoses) || nDoses <= 0) throw new RangeError("nDoses must be a positive integer");
      if (times.some((time) => !Number.isFinite(time) || time < 0)) {
        throw new RangeError("observation times must be finite and non-negative");
      }
      const { CL, Vd, ka, F, activeMoietyFraction } = this.parameters;
      const ke = CL / Vd;
      const activeDoseMicrograms = doseMg * 1e3 * activeMoietyFraction;
      return times.map((time) => {
        let centralAmount = 0;
        for (let doseIndex = 0; doseIndex < nDoses; doseIndex++) {
          const elapsed = time - doseIndex * interval;
          if (elapsed < -TIME_EPSILON) break;
          if (this.drug.route === "intravenous-bolus") {
            centralAmount += activeDoseMicrograms * F * Math.exp(-ke * Math.max(0, elapsed));
          } else if (Math.abs(ka - ke) < 1e-10) {
            centralAmount += activeDoseMicrograms * F * ka * Math.max(0, elapsed) * Math.exp(-ke * Math.max(0, elapsed));
          } else {
            centralAmount += activeDoseMicrograms * F * ka * (Math.exp(-ka * Math.max(0, elapsed)) - Math.exp(-ke * Math.max(0, elapsed))) / (ke - ka);
          }
        }
        return this.toDisplayUnit(Math.max(0, centralAmount) / Vd);
      });
    }
    analysisWindow(interval, nDoses, duration) {
      if (duration <= interval || nDoses <= 1) return [0, duration];
      const lastCompleteIndex = Math.max(
        0,
        Math.min(nDoses - 1, Math.floor((duration - interval + TIME_EPSILON) / interval))
      );
      const start = lastCompleteIndex * interval;
      return [start, Math.min(duration, start + interval)];
    }
    outputTimes(dt, interval, nDoses, duration, window) {
      const times = /* @__PURE__ */ new Set([0, duration, window[0], window[1]]);
      const steps = Math.ceil(duration / dt);
      for (let i = 1; i < steps; i++) times.add(Math.min(duration, i * dt));
      for (let d = 0; d < nDoses; d++) {
        const doseTime = d * interval;
        if (doseTime <= duration + TIME_EPSILON) times.add(Math.min(duration, doseTime));
      }
      return [...times].sort((a, b) => a - b);
    }
    simulateMultiDose(doseMg, interval, nDoses, duration, dt) {
      if (!Number.isFinite(doseMg) || doseMg <= 0) throw new RangeError("dose must be positive");
      if (!Number.isFinite(interval) || interval <= 0) throw new RangeError("interval must be positive");
      if (!Number.isInteger(nDoses) || nDoses <= 0) throw new RangeError("nDoses must be a positive integer");
      if (!Number.isFinite(duration) || duration <= 0) throw new RangeError("duration must be positive");
      if (!Number.isFinite(dt) || dt <= 0) throw new RangeError("dt must be positive");
      const analysisWindow = this.analysisWindow(interval, nDoses, duration);
      const t = this.outputTimes(dt, interval, nDoses, duration, analysisWindow);
      const C = [];
      const intervalC = [];
      let state = emptyState();
      let currentTime = 0;
      let nextDoseIndex = 0;
      for (const targetTime of t) {
        state = this.advance(state, targetTime - currentTime);
        currentTime = targetTime;
        const concentrationBeforeDose = this.toDisplayUnit(
          state.A_central / this.parameters.Vd
        );
        while (nextDoseIndex < nDoses) {
          const doseTime = nextDoseIndex * interval;
          if (Math.abs(doseTime - targetTime) > TIME_EPSILON) break;
          state = this.applyDose(state, doseMg);
          nextDoseIndex++;
        }
        const concentrationAfterDose = this.toDisplayUnit(
          state.A_central / this.parameters.Vd
        );
        C.push(concentrationAfterDose);
        intervalC.push(
          targetTime > analysisWindow[0] + TIME_EPSILON && Math.abs(targetTime - analysisWindow[1]) <= TIME_EPSILON ? concentrationBeforeDose : concentrationAfterDose
        );
      }
      const [windowStart, windowEnd] = analysisWindow;
      const windowIndices = t.map((time, index) => ({ time, index })).filter(({ time }) => time >= windowStart - TIME_EPSILON && time <= windowEnd + TIME_EPSILON).map(({ index }) => index);
      const windowT = windowIndices.map((index) => t[index]);
      const windowC = windowIndices.map((index) => intervalC[index]);
      const Cmax = Math.max(...windowC);
      const Cmin = Math.min(...windowC);
      const peakIndex = windowC.indexOf(Cmax);
      const Tmax = windowT[peakIndex] - windowStart;
      const accounted = state.A_site + state.A_central + state.A_unavailable + state.A_eliminated;
      const massBalanceError = Math.abs(state.A_administered - accounted) / Math.max(1, state.A_administered);
      return {
        t,
        C,
        Cmax,
        Cmin,
        Tmax,
        AUC: trapezoid(t, C),
        AUCtau: trapezoid(windowT, windowC),
        analysisWindow,
        route: this.drug.route,
        sampledParameters: { ...this.parameters },
        massBalanceError
      };
    }
  };

  // src/lib/pkpd/random.ts
  var SeededRandom = class {
    constructor(seed) {
      this.spareNormal = null;
      if (!Number.isFinite(seed)) throw new RangeError("seed must be finite");
      this.state = seed >>> 0;
    }
    nextUint32() {
      this.state = this.state + 1831565813 >>> 0;
      let value = this.state;
      value = Math.imul(value ^ value >>> 15, value | 1);
      value ^= value + Math.imul(value ^ value >>> 7, value | 61);
      return (value ^ value >>> 14) >>> 0;
    }
    uniform() {
      return this.nextUint32() / 4294967296;
    }
    normal() {
      if (this.spareNormal !== null) {
        const value = this.spareNormal;
        this.spareNormal = null;
        return value;
      }
      const u1 = Math.max(Number.MIN_VALUE, this.uniform());
      const u2 = this.uniform();
      const radius = Math.sqrt(-2 * Math.log(u1));
      const angle = 2 * Math.PI * u2;
      this.spareNormal = radius * Math.sin(angle);
      return radius * Math.cos(angle);
    }
  };

  // src/lib/pkpd/simulator.ts
  var DEFAULT_SEED = 1213355083;
  var DEFAULT_VARIABILITY = {
    clCV: 0,
    vdCV: 0,
    kaCV: 0,
    fCV: 0,
    clVdCorrelation: 0
  };
  var assertCV = (name, value) => {
    if (!Number.isFinite(value) || value < 0 || value > 3) {
      throw new RangeError(`${name} must be a finite fraction between 0 and 3`);
    }
  };
  var logNormalSigmaFromCV = (cv) => Math.sqrt(Math.log1p(cv * cv));
  var logNormalFactor = (rng, cv) => cv === 0 ? 1 : Math.exp(logNormalSigmaFromCV(cv) * rng.normal());
  var logistic = (value) => 1 / (1 + Math.exp(-value));
  var logit = (value) => Math.log(value / (1 - value));
  var boundedFraction = (rng, typical, cv) => {
    if (cv === 0 || typical === 1) return typical;
    const logitSD = logNormalSigmaFromCV(cv) / Math.max(1e-6, 1 - typical);
    return logistic(logit(typical) + logitSD * rng.normal());
  };
  var percentile = (values, probability) => {
    if (values.length === 0) throw new RangeError("percentile requires at least one value");
    const sorted = [...values].sort((a, b) => a - b);
    const position = (sorted.length - 1) * probability;
    const lower = Math.floor(position);
    const upper = Math.ceil(position);
    if (lower === upper) return sorted[lower];
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
  };
  var summarize = (values) => {
    if (values.length === 0) throw new RangeError("summary requires at least one result");
    const mean2 = values.reduce((sum, value) => sum + value, 0) / values.length;
    const variance2 = values.length > 1 ? values.reduce((sum, value) => sum + (value - mean2) ** 2, 0) / (values.length - 1) : 0;
    return {
      median: percentile(values, 0.5),
      p05: percentile(values, 0.05),
      p25: percentile(values, 0.25),
      p75: percentile(values, 0.75),
      p95: percentile(values, 0.95),
      mean: mean2,
      standardError: Math.sqrt(variance2 / values.length)
    };
  };
  var PKPDSimulator = class {
    /**
     * Kept for compatibility with the old page controller. The former shader
     * used a non-normal pseudo-random increment and returned incomplete exposure
     * summaries, so audited simulations deliberately stay on the CPU.
     */
    async initGPU() {
      return false;
    }
    isGPUAvailable() {
      return false;
    }
    doseCount(config) {
      if (config.interval <= 0 || config.duration <= 0) {
        throw new RangeError("interval and duration must be positive");
      }
      return Math.max(1, Math.ceil(config.duration / config.interval));
    }
    simulateParameters(config, parameters) {
      const model = new OneCompartmentModel(config.drug, parameters);
      return model.simulateMultiDose(
        config.dose,
        config.interval,
        this.doseCount(config),
        config.duration,
        config.dt
      );
    }
    simulate(config) {
      return this.simulateParameters(config, {});
    }
    resolveVariability(config) {
      const variability = {
        ...DEFAULT_VARIABILITY,
        ...config.drug.variability,
        ...config.population
      };
      assertCV("clCV", variability.clCV);
      assertCV("vdCV", variability.vdCV);
      assertCV("kaCV", variability.kaCV);
      assertCV("fCV", variability.fCV);
      const rho = variability.clVdCorrelation ?? 0;
      if (!Number.isFinite(rho) || rho < -0.99 || rho > 0.99) {
        throw new RangeError("clVdCorrelation must be between -0.99 and 0.99");
      }
      return { ...variability, clVdCorrelation: rho };
    }
    sampleParameters(config, variability, rng) {
      const zCL = rng.normal();
      const rho = variability.clVdCorrelation ?? 0;
      const zVd = rho * zCL + Math.sqrt(1 - rho * rho) * rng.normal();
      const clSigma = logNormalSigmaFromCV(variability.clCV);
      const vdSigma = logNormalSigmaFromCV(variability.vdCV);
      return {
        CL: config.drug.CL * (variability.clCV === 0 ? 1 : Math.exp(clSigma * zCL)),
        Vd: config.drug.Vd * (variability.vdCV === 0 ? 1 : Math.exp(vdSigma * zVd)),
        ka: config.drug.ka * logNormalFactor(rng, variability.kaCV),
        F: boundedFraction(rng, config.drug.F, variability.fCV),
        activeMoietyFraction: config.drug.activeMoietyFraction ?? 1
      };
    }
    addObservationError(latent, error, rng) {
      if (!error || error.model === "none") return void 0;
      const additiveSD = error.additiveSD ?? 0;
      const proportionalCV = error.proportionalCV ?? 0;
      assertCV("proportionalCV", proportionalCV);
      if (!Number.isFinite(additiveSD) || additiveSD < 0) {
        throw new RangeError("additiveSD must be finite and non-negative");
      }
      const proportionalSigma = logNormalSigmaFromCV(proportionalCV);
      return latent.map((value) => {
        const proportional = error.model === "proportional" || error.model === "combined" ? value * Math.exp(proportionalSigma * rng.normal() - 0.5 * proportionalSigma ** 2) : value;
        const additive = error.model === "additive" || error.model === "combined" ? additiveSD * rng.normal() : 0;
        return Math.max(0, proportional + additive);
      });
    }
    async monteCarloSimulation(config, numSims = config.numSimulations ?? 100) {
      if (!Number.isInteger(numSims) || numSims <= 0 || numSims > 1e5) {
        throw new RangeError("numSims must be an integer between 1 and 100000");
      }
      const variability = this.resolveVariability(config);
      const masterRng = new SeededRandom(config.seed ?? DEFAULT_SEED);
      const results = [];
      for (let i = 0; i < numSims; i++) {
        const seed = masterRng.nextUint32();
        const rng = new SeededRandom(seed);
        const sampledParameters = config.mode === "deterministic" ? {
          CL: config.drug.CL,
          Vd: config.drug.Vd,
          ka: config.drug.ka,
          F: config.drug.F,
          activeMoietyFraction: config.drug.activeMoietyFraction ?? 1
        } : this.sampleParameters(config, variability, rng);
        const result = this.simulateParameters(config, sampledParameters);
        const observedC = this.addObservationError(result.C, config.observationError, rng);
        results.push({ ...result, observedC, seed });
      }
      return results;
    }
    calculateStatistics(results) {
      return {
        Cmax: summarize(results.map((result) => result.Cmax)),
        Cmin: summarize(results.map((result) => result.Cmin)),
        Tmax: summarize(results.map((result) => result.Tmax)),
        AUCtau: summarize(results.map((result) => result.AUCtau))
      };
    }
  };

  // src/lib/drugs/database.ts
  var DRUG_DB = {
    E2_oral: {
      name: "Estradiol Oral",
      route: "oral",
      parameterization: "population",
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
      ref: "PMID: 1548642"
    },
    E2V_oral: {
      name: "Estradiol Valerate Oral",
      route: "oral",
      parameterization: "population",
      therapeutic: [50, 200],
      unit: "pg/mL",
      CL: 60,
      Vd: 60,
      ka: 0.04,
      F: 0.03,
      activeMoietyFraction: 0.764,
      halfLife: 1,
      halfLifeApparent: 17,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 2,
      defaultInterval: 12,
      ref: "PMID: 9793623"
    },
    E2_subl: {
      name: "Estradiol Sublingual",
      route: "sublingual",
      parameterization: "illustrative",
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
      route: "transdermal",
      parameterization: "population",
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
      route: "transdermal",
      parameterization: "illustrative",
      therapeutic: [50, 200],
      unit: "pg/mL",
      CL: 12,
      Vd: 180,
      ka: 0.15,
      F: 0.1,
      halfLife: 24,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 1.5,
      defaultInterval: 24,
      ref: "PMID: 17143811"
    },
    E2V: {
      name: "Estradiol Valerate IM",
      route: "intramuscular-depot",
      parameterization: "population",
      therapeutic: [100, 400],
      unit: "pg/mL",
      CL: 100,
      Vd: 2400,
      ka: 0.012,
      F: 0.85,
      activeMoietyFraction: 0.764,
      halfLife: 120,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 5,
      defaultInterval: 168,
      ref: "PMID: 7169965"
    },
    E2C: {
      name: "Estradiol Cypionate IM",
      route: "intramuscular-depot",
      parameterization: "illustrative",
      therapeutic: [100, 400],
      unit: "pg/mL",
      CL: 120,
      Vd: 2800,
      ka: 6e-3,
      F: 0.9,
      activeMoietyFraction: 0.687,
      halfLife: 192,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 5,
      defaultInterval: 168,
      ref: "Illustrative parameters; route evidence PMID: 10640167"
    },
    E2E: {
      name: "Estradiol Enanthate IM",
      route: "intramuscular-depot",
      parameterization: "illustrative",
      therapeutic: [100, 400],
      unit: "pg/mL",
      CL: 5,
      Vd: 2500,
      ka: 4e-3,
      F: 0.92,
      activeMoietyFraction: 0.708,
      halfLife: 240,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 5,
      defaultInterval: 168,
      ref: "Illustrative parameterization; no individualized calibration"
    },
    MPA_oral: {
      name: "Medroxyprogesterone Acetate Oral",
      route: "oral",
      parameterization: "population",
      therapeutic: [1, 10],
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
      ref: "DrugBank DB00603 | Pfizer PROVERA"
    },
    CPA_oral: {
      name: "Cyproterone Acetate Oral",
      route: "oral",
      parameterization: "population",
      therapeutic: [50, 300],
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
      ref: "PMID: 3127499"
    },
    TEST_En: {
      name: "Testosterone Enanthate IM",
      route: "intramuscular-depot",
      parameterization: "population",
      therapeutic: [300, 1e3],
      unit: "ng/dL",
      CL: 80,
      Vd: 1900,
      ka: 0.015,
      F: 0.65,
      activeMoietyFraction: 0.72,
      halfLife: 96,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 100,
      defaultInterval: 168,
      ref: "PMC4721027 | PMC9293229"
    },
    TEST_Cy: {
      name: "Testosterone Cypionate IM",
      route: "intramuscular-depot",
      parameterization: "illustrative",
      therapeutic: [300, 1e3],
      unit: "ng/dL",
      CL: 6,
      Vd: 900,
      ka: 0.012,
      F: 0.65,
      activeMoietyFraction: 0.699,
      halfLife: 120,
      doseUnit: "mg",
      intervalUnit: "h",
      defaultDose: 100,
      defaultInterval: 168,
      ref: "Illustrative parameterization; no individualized calibration"
    }
  };

  // src/lib/drugs/progestogens.ts
  var PROGESTOGENS = {
    MPA_oral: {
      name: "Medroxyprogesterone Acetate Oral",
      route: "oral",
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
      route: "oral",
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
      route: "intramuscular-depot",
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
      route: "intramuscular-depot",
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

  // src/lib/bayesian/mcmc.ts
  var LOG_TWO_PI = Math.log(2 * Math.PI);
  var DEFAULT_SEED2 = 1296256323;
  var PARAMETER_NAMES = ["CL", "Vd", "ka", "F"];
  var mean = (values) => values.reduce((sum, value) => sum + value, 0) / values.length;
  var variance = (values, sample = true) => {
    if (values.length < (sample ? 2 : 1)) return 0;
    const center = mean(values);
    const denominator = sample ? values.length - 1 : values.length;
    return values.reduce((sum, value) => sum + (value - center) ** 2, 0) / denominator;
  };
  var percentile2 = (values, probability) => {
    if (values.length === 0) throw new RangeError("percentile requires samples");
    const sorted = [...values].sort((a, b) => a - b);
    const position = (sorted.length - 1) * probability;
    const lower = Math.floor(position);
    const upper = Math.ceil(position);
    if (lower === upper) return sorted[lower];
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
  };
  var clamp = (value, lower, upper) => Math.max(lower, Math.min(upper, value));
  var logit2 = (value) => Math.log(value / (1 - value));
  var logistic2 = (value) => 1 / (1 + Math.exp(-value));
  var logNormalSD = (cv) => Math.sqrt(Math.log1p(cv * cv));
  var inverseNormal = (probability) => {
    const p = clamp(probability, Number.EPSILON, 1 - Number.EPSILON);
    const a = [
      -39.69683028665376,
      220.9460984245205,
      -275.9285104469687,
      138.357751867269,
      -30.66479806614716,
      2.506628277459239
    ];
    const b = [
      -54.47609879822406,
      161.5858368580409,
      -155.6989798598866,
      66.80131188771972,
      -13.28068155288572
    ];
    const c = [
      -0.007784894002430293,
      -0.3223964580411365,
      -2.400758277161838,
      -2.549732539343734,
      4.374664141464968,
      2.938163982698783
    ];
    const d = [
      0.007784695709041462,
      0.3224671290700398,
      2.445134137142996,
      3.754408661907416
    ];
    const low = 0.02425;
    const high = 1 - low;
    if (p < low) {
      const q2 = Math.sqrt(-2 * Math.log(p));
      return (((((c[0] * q2 + c[1]) * q2 + c[2]) * q2 + c[3]) * q2 + c[4]) * q2 + c[5]) / ((((d[0] * q2 + d[1]) * q2 + d[2]) * q2 + d[3]) * q2 + 1);
    }
    if (p > high) {
      const q2 = Math.sqrt(-2 * Math.log(1 - p));
      return -(((((c[0] * q2 + c[1]) * q2 + c[2]) * q2 + c[3]) * q2 + c[4]) * q2 + c[5]) / ((((d[0] * q2 + d[1]) * q2 + d[2]) * q2 + d[3]) * q2 + 1);
    }
    const q = p - 0.5;
    const r = q * q;
    return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
  };
  var splitChains = (chains) => {
    if (chains.length < 2) throw new RangeError("diagnostics require multiple chains");
    const length = Math.min(...chains.map((chain) => chain.length));
    const half = Math.floor(length / 2);
    if (half < 2) throw new RangeError("diagnostics require at least four draws per chain");
    return chains.flatMap((chain) => [
      chain.slice(0, half),
      chain.slice(length - half, length)
    ]);
  };
  var rankNormalize = (chains) => {
    const lengths = chains.map((chain) => chain.length);
    const pooled = chains.flat();
    const ordered = pooled.map((value, index) => ({ value, index })).sort((left, right) => left.value - right.value);
    const ranks = new Array(pooled.length);
    for (let start = 0; start < ordered.length; ) {
      let end = start + 1;
      while (end < ordered.length && ordered[end].value === ordered[start].value) end++;
      const averageRank = (start + 1 + end) / 2;
      for (let i = start; i < end; i++) ranks[ordered[i].index] = averageRank;
      start = end;
    }
    const normalized = ranks.map(
      (rank) => inverseNormal((rank - 3 / 8) / (pooled.length + 1 / 4))
    );
    let offset = 0;
    return lengths.map((length) => {
      const chain = normalized.slice(offset, offset + length);
      offset += length;
      return chain;
    });
  };
  var basicRhat = (chains) => {
    const chainLength = chains[0].length;
    const within = mean(chains.map((chain) => variance(chain)));
    const between = chainLength * variance(chains.map((chain) => mean(chain)));
    if (within === 0) return between === 0 ? 1 : Number.POSITIVE_INFINITY;
    const variancePlus = (chainLength - 1) / chainLength * within + between / chainLength;
    return Math.sqrt(Math.max(0, variancePlus / within));
  };
  var effectiveSampleSize = (chains) => {
    const chainCount = chains.length;
    const chainLength = chains[0].length;
    const total = chainCount * chainLength;
    const chainMeans = chains.map((chain) => mean(chain));
    const within = mean(chains.map((chain) => variance(chain)));
    const between = chainLength * variance(chainMeans);
    const variancePlus = (chainLength - 1) / chainLength * within + between / chainLength;
    if (variancePlus <= Number.EPSILON) return total;
    const autocorrelation = [1];
    for (let lag = 1; lag < chainLength; lag++) {
      const meanAutocovariance = mean(chains.map((chain, chainIndex) => {
        let covariance = 0;
        for (let i = 0; i < chainLength - lag; i++) {
          covariance += (chain[i] - chainMeans[chainIndex]) * (chain[i + lag] - chainMeans[chainIndex]);
        }
        return covariance / chainLength;
      }));
      autocorrelation.push(1 - (within - meanAutocovariance) / variancePlus);
    }
    const pairs = [];
    for (let pair = 0; pair * 2 + 1 < autocorrelation.length; pair++) {
      const value = autocorrelation[pair * 2] + autocorrelation[pair * 2 + 1];
      if (pair > 0 && value < 0) break;
      pairs.push(value);
    }
    for (let i = 1; i < pairs.length; i++) pairs[i] = Math.min(pairs[i], pairs[i - 1]);
    const tau = Math.max(1, -1 + 2 * pairs.reduce((sum, value) => sum + value, 0));
    return Math.min(total, total / tau);
  };
  var averageAutocorrelation = (chains, maxLag) => {
    const length = Math.min(...chains.map((chain) => chain.length));
    const requested = [1, 5, 10, 20, 50].filter((lag, index, values) => lag <= maxLag && lag < length && values.indexOf(lag) === index);
    return requested.map((lag) => ({
      lag,
      value: mean(chains.map((chain) => {
        const center = mean(chain);
        const denominator = chain.reduce((sum, value) => sum + (value - center) ** 2, 0);
        if (denominator <= Number.EPSILON) return 0;
        let numerator = 0;
        for (let i = 0; i < chain.length - lag; i++) {
          numerator += (chain[i] - center) * (chain[i + lag] - center);
        }
        return numerator / denominator;
      }))
    }));
  };
  var calculateChainDiagnostics = (chains, maxAutocorrelationLag = 50) => {
    const split = splitChains(chains);
    const ranked = rankNormalize(split);
    const pooled = split.flat();
    const center = percentile2(pooled, 0.5);
    const foldedRanked = rankNormalize(split.map(
      (chain) => chain.map((value) => Math.abs(value - center))
    ));
    const lower = percentile2(pooled, 0.05);
    const upper = percentile2(pooled, 0.95);
    const lowerIndicators = split.map((chain) => chain.map((value) => value <= lower ? 1 : 0));
    const upperIndicators = split.map((chain) => chain.map((value) => value >= upper ? 1 : 0));
    return {
      rhat: Math.max(basicRhat(ranked), basicRhat(foldedRanked)),
      bulkESS: effectiveSampleSize(ranked),
      tailESS: Math.min(
        effectiveSampleSize(lowerIndicators),
        effectiveSampleSize(upperIndicators)
      ),
      autocorrelation: averageAutocorrelation(chains, maxAutocorrelationLag)
    };
  };
  var emptyChain = (seed) => ({
    CL: [],
    Vd: [],
    ka: [],
    F: [],
    logLikelihood: [],
    logPosterior: [],
    acceptance: 0,
    seed
  });
  var BayesianEstimator = class {
    constructor(config) {
      this.validateConfig(config);
      this.config = config;
      this.observations = [...config.observedData].sort((left, right) => left.time - right.time);
      this.priorCenters = [
        Math.log(config.priors.CL.median),
        Math.log(config.priors.Vd.median),
        Math.log(config.priors.ka.median),
        logit2(config.priors.F.median)
      ];
      this.priorSDs = [
        logNormalSD(config.priors.CL.cv),
        logNormalSD(config.priors.Vd.cv),
        logNormalSD(config.priors.ka.cv),
        config.priors.F.logitSD
      ];
      const lastObservation = Math.max(...this.observations.map((observation) => observation.time));
      this.nDoses = config.nDoses ?? Math.floor((lastObservation + 1e-9) / config.interval) + 1;
      this.observationLogSD = logNormalSD(config.observationError.proportionalCV);
    }
    validateConfig(config) {
      if (config.observedData.length < 2) {
        throw new RangeError("at least two observed concentrations are required");
      }
      for (const observation of config.observedData) {
        if (!Number.isFinite(observation.time) || observation.time < 0) {
          throw new RangeError("observation times must be finite and non-negative");
        }
        if (!Number.isFinite(observation.concentration) || observation.concentration <= 0) {
          throw new RangeError("observed concentrations must be finite and positive");
        }
      }
      if (!Number.isFinite(config.dose) || config.dose <= 0) throw new RangeError("dose must be positive");
      if (!Number.isFinite(config.interval) || config.interval <= 0) {
        throw new RangeError("interval must be positive");
      }
      if (config.nDoses !== void 0 && (!Number.isInteger(config.nDoses) || config.nDoses <= 0)) {
        throw new RangeError("nDoses must be a positive integer");
      }
      for (const name of ["CL", "Vd", "ka"]) {
        const prior = config.priors[name];
        if (!Number.isFinite(prior.median) || prior.median <= 0) {
          throw new RangeError(`${name} prior median must be positive`);
        }
        if (!Number.isFinite(prior.cv) || prior.cv <= 0 || prior.cv > 3) {
          throw new RangeError(`${name} prior CV must be in (0, 3]`);
        }
      }
      const fPrior = config.priors.F;
      if (!Number.isFinite(fPrior.median) || fPrior.median <= 0 || fPrior.median >= 1) {
        throw new RangeError("F prior median must be in (0, 1)");
      }
      if (!Number.isFinite(fPrior.logitSD) || fPrior.logitSD <= 0 || fPrior.logitSD > 5) {
        throw new RangeError("F prior logitSD must be in (0, 5]");
      }
      const cv = config.observationError.proportionalCV;
      if (config.observationError.model !== "lognormal-proportional" || !Number.isFinite(cv) || cv <= 0 || cv > 3) {
        throw new RangeError("an explicit proportional observation CV in (0, 3] is required");
      }
      if (config.seed !== void 0 && !Number.isFinite(config.seed)) {
        throw new RangeError("seed must be finite");
      }
    }
    naturalParameters(transformed) {
      return {
        CL: Math.exp(transformed[0]),
        Vd: Math.exp(transformed[1]),
        ka: Math.exp(transformed[2]),
        F: logistic2(transformed[3]),
        activeMoietyFraction: this.config.drug.activeMoietyFraction ?? 1
      };
    }
    logPrior(transformed) {
      return transformed.reduce((sum, value, index) => {
        const standardized = (value - this.priorCenters[index]) / this.priorSDs[index];
        return sum - 0.5 * standardized ** 2 - Math.log(this.priorSDs[index]) - 0.5 * LOG_TWO_PI;
      }, 0);
    }
    predictions(parameters) {
      return new OneCompartmentModel(this.config.drug, parameters).predictAtTimes(
        this.config.dose,
        this.config.interval,
        this.nDoses,
        this.observations.map((observation) => observation.time)
      );
    }
    logLikelihood(transformed) {
      const predicted = this.predictions(this.naturalParameters(transformed));
      const sigma = this.observationLogSD;
      let result = 0;
      for (let i = 0; i < predicted.length; i++) {
        if (!(predicted[i] > 0)) return Number.NEGATIVE_INFINITY;
        const observed = this.observations[i].concentration;
        const location = Math.log(predicted[i]) - 0.5 * sigma ** 2;
        const standardized = (Math.log(observed) - location) / sigma;
        result += -0.5 * standardized ** 2 - Math.log(observed * sigma) - 0.5 * LOG_TWO_PI;
      }
      return result;
    }
    logTarget(transformed) {
      const likelihood = this.logLikelihood(transformed);
      return { posterior: likelihood + this.logPrior(transformed), likelihood };
    }
    initializeChain(rng) {
      for (let attempt = 0; attempt < 100; attempt++) {
        const state = this.priorCenters.map(
          (center, index) => center + this.priorSDs[index] * rng.normal()
        );
        const target = this.logTarget(state);
        if (Number.isFinite(target.posterior)) return { state, target };
      }
      throw new Error("could not initialize a finite chain; check dosing history and sample times");
    }
    runChain(seed, draws, warmup) {
      const rng = new SeededRandom(seed);
      let { state, target } = this.initializeChain(rng);
      const result = emptyChain(seed);
      const proposalSD = this.priorSDs.map((value) => Math.max(0.01, value * 0.15));
      const adaptationAccepted = new Array(PARAMETER_NAMES.length).fill(0);
      let accepted = 0;
      let proposals = 0;
      const adaptationWindow = 50;
      for (let iteration = 0; iteration < warmup + draws; iteration++) {
        for (let parameter = 0; parameter < PARAMETER_NAMES.length; parameter++) {
          const proposed = [...state];
          proposed[parameter] += proposalSD[parameter] * rng.normal();
          const proposedTarget = this.logTarget(proposed);
          proposals++;
          if (Math.log(Math.max(Number.MIN_VALUE, rng.uniform())) < proposedTarget.posterior - target.posterior) {
            state = proposed;
            target = proposedTarget;
            accepted++;
            if (iteration < warmup) adaptationAccepted[parameter]++;
          }
        }
        if (iteration < warmup && (iteration + 1) % adaptationWindow === 0) {
          for (let parameter = 0; parameter < PARAMETER_NAMES.length; parameter++) {
            const rate = adaptationAccepted[parameter] / adaptationWindow;
            proposalSD[parameter] = clamp(
              proposalSD[parameter] * Math.exp(clamp(rate - 0.44, -0.5, 0.5)),
              this.priorSDs[parameter] * 5e-3,
              this.priorSDs[parameter] * 3
            );
            adaptationAccepted[parameter] = 0;
          }
        }
        if (iteration >= warmup) {
          const natural = this.naturalParameters(state);
          result.CL.push(natural.CL);
          result.Vd.push(natural.Vd);
          result.ka.push(natural.ka);
          result.F.push(natural.F);
          result.logLikelihood.push(target.likelihood);
          result.logPosterior.push(target.posterior);
        }
      }
      result.acceptance = accepted / proposals;
      return result;
    }
    posteriorPredictive(samples, requestedDraws, seed) {
      const total = samples.CL.length;
      const draws = Math.min(total, requestedDraws);
      const rng = new SeededRandom(seed ^ 1347437361);
      const replicated = this.observations.map(() => []);
      let replicatedMoreExtreme = 0;
      for (let draw = 0; draw < draws; draw++) {
        const index = Math.min(total - 1, Math.floor(rng.uniform() * total));
        const parameters = {
          CL: samples.CL[index],
          Vd: samples.Vd[index],
          ka: samples.ka[index],
          F: samples.F[index],
          activeMoietyFraction: this.config.drug.activeMoietyFraction ?? 1
        };
        const predicted = this.predictions(parameters);
        let observedDiscrepancy = 0;
        let replicatedDiscrepancy = 0;
        for (let i = 0; i < predicted.length; i++) {
          const location = Math.log(predicted[i]) - 0.5 * this.observationLogSD ** 2;
          const simulated = Math.exp(location + this.observationLogSD * rng.normal());
          replicated[i].push(simulated);
          observedDiscrepancy += ((Math.log(this.observations[i].concentration) - location) / this.observationLogSD) ** 2;
          replicatedDiscrepancy += ((Math.log(simulated) - location) / this.observationLogSD) ** 2;
        }
        if (replicatedDiscrepancy >= observedDiscrepancy) replicatedMoreExtreme++;
      }
      const predictiveMedian = replicated.map((values) => percentile2(values, 0.5));
      const lower95 = replicated.map((values) => percentile2(values, 0.025));
      const upper95 = replicated.map((values) => percentile2(values, 0.975));
      const observed = this.observations.map((observation) => observation.concentration);
      const covered = observed.filter(
        (value, index) => value >= lower95[index] && value <= upper95[index]
      ).length;
      return {
        time: this.observations.map((observation) => observation.time),
        observed,
        median: predictiveMedian,
        lower95,
        upper95,
        coverage95: covered / observed.length,
        rmseMedian: Math.sqrt(mean(observed.map(
          (value, index) => (value - predictiveMedian[index]) ** 2
        ))),
        bayesianPValue: replicatedMoreExtreme / draws,
        draws
      };
    }
    runMCMC(options = {}) {
      const draws = options.draws ?? 1e3;
      const warmup = options.warmup ?? 1e3;
      const chainCount = options.chains ?? 4;
      const maxLag = options.maxAutocorrelationLag ?? 50;
      const predictiveDraws = options.posteriorPredictiveDraws ?? 400;
      for (const [name, value, minimum, maximum] of [
        ["draws", draws, 100, 1e5],
        ["warmup", warmup, 100, 1e5],
        ["chains", chainCount, 4, 16],
        ["maxAutocorrelationLag", maxLag, 1, 1e3],
        ["posteriorPredictiveDraws", predictiveDraws, 20, 1e4]
      ]) {
        if (!Number.isInteger(value) || value < minimum || value > maximum) {
          throw new RangeError(`${name} must be an integer between ${minimum} and ${maximum}`);
        }
      }
      const master = new SeededRandom(this.config.seed ?? DEFAULT_SEED2);
      const chains = Array.from({ length: chainCount }, () => {
        const seed = master.nextUint32();
        return this.runChain(seed, draws, warmup);
      });
      const samples = {
        CL: chains.flatMap((chain) => chain.CL),
        Vd: chains.flatMap((chain) => chain.Vd),
        ka: chains.flatMap((chain) => chain.ka),
        F: chains.flatMap((chain) => chain.F),
        logLikelihood: chains.flatMap((chain) => chain.logLikelihood),
        logPosterior: chains.flatMap((chain) => chain.logPosterior)
      };
      const byParameter = Object.fromEntries(PARAMETER_NAMES.map((name) => [
        name,
        calculateChainDiagnostics(chains.map((chain) => chain[name]), maxLag)
      ]));
      const maxRhat = Math.max(...PARAMETER_NAMES.map((name) => byParameter[name].rhat));
      const minBulkESS = Math.min(...PARAMETER_NAMES.map((name) => byParameter[name].bulkESS));
      const minTailESS = Math.min(...PARAMETER_NAMES.map((name) => byParameter[name].tailESS));
      const requiredESS = 100 * chainCount;
      const warnings = [];
      if (maxRhat > 1.05 || !Number.isFinite(maxRhat)) {
        warnings.push("R-hat exceeds 1.05; chains have not demonstrated convergence.");
      }
      if (minBulkESS < requiredESS || minTailESS < requiredESS) {
        warnings.push(`Bulk/tail ESS should each reach at least ${requiredESS} for ${chainCount} chains.`);
      }
      if (this.observations.length <= PARAMETER_NAMES.length) {
        warnings.push("Sparse data relative to four free PK parameters; F, V and CL may remain weakly identified.");
      }
      const acceptanceByChain = chains.map((chain) => chain.acceptance);
      if (acceptanceByChain.some((rate) => rate < 0.1 || rate > 0.8)) {
        warnings.push("At least one random-walk chain has an extreme acceptance rate.");
      }
      const diagnostics = {
        byParameter,
        maxRhat,
        minBulkESS,
        minTailESS,
        converged: warnings.length === 0,
        warnings
      };
      const posteriorStats = Object.fromEntries(PARAMETER_NAMES.map((name) => {
        const values = samples[name];
        const std = Math.sqrt(variance(values));
        const chainDiagnostics = byParameter[name];
        return [name, {
          mean: mean(values),
          std,
          median: percentile2(values, 0.5),
          ci95: [percentile2(values, 0.025), percentile2(values, 0.975)],
          mcseMean: std / Math.sqrt(Math.max(1, chainDiagnostics.bulkESS)),
          ...chainDiagnostics
        }];
      }));
      return {
        chains,
        samples,
        acceptance: mean(acceptanceByChain),
        acceptanceByChain,
        posteriorStats,
        diagnostics,
        posteriorPredictive: this.posteriorPredictive(
          samples,
          predictiveDraws,
          this.config.seed ?? DEFAULT_SEED2
        )
      };
    }
  };
  return __toCommonJS(index_exports);
})();
//# sourceMappingURL=pkpd-bundle.js.map
