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
    PROGESTOGENS: () => PROGESTOGENS
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
    const mean = values.reduce((sum, value) => sum + value, 0) / values.length;
    const variance = values.length > 1 ? values.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (values.length - 1) : 0;
    return {
      median: percentile(values, 0.5),
      p05: percentile(values, 0.05),
      p25: percentile(values, 0.25),
      p75: percentile(values, 0.75),
      p95: percentile(values, 0.95),
      mean,
      standardError: Math.sqrt(variance / values.length)
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
  var BayesianEstimator = class {
    constructor(config) {
      this.config = config;
    }
    randn() {
      const u1 = Math.random();
      const u2 = Math.random();
      return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }
    pkModel(CL, Vd, ka) {
      const { dose, interval, nDoses, duration, dt, F } = this.config;
      const n = Math.ceil(duration / dt);
      const C = [];
      let A_depot = 0;
      let A_central = 0;
      const ke = CL / Vd;
      const doseTimes = [];
      for (let d = 0; d < nDoses; d++) {
        doseTimes.push(d * interval);
      }
      let lastDoseIdx = -1;
      for (let i = 0; i < n; i++) {
        const currentTime = i * dt;
        const doseIdx = doseTimes.findIndex((t) => Math.abs(currentTime - t) < dt / 2);
        if (doseIdx !== -1 && doseIdx > lastDoseIdx) {
          A_depot += dose * 1e3 * F;
          lastDoseIdx = doseIdx;
        }
        const dA_depot = -ka * A_depot * dt;
        const dA_central = (ka * A_depot - ke * A_central) * dt;
        A_depot += dA_depot;
        A_central += dA_central;
        C.push(A_central / Vd);
      }
      return C;
    }
    logLikelihood(CL, Vd, ka) {
      const predicted = this.pkModel(CL, Vd, ka);
      const { observedData, dt } = this.config;
      let logLik = 0;
      const sigma = 10;
      for (const obs of observedData) {
        const idx = Math.round(obs.time / dt);
        if (idx >= 0 && idx < predicted.length) {
          const pred = predicted[idx];
          const residual = obs.concentration - pred;
          logLik -= 0.5 * (residual * residual) / (sigma * sigma);
        }
      }
      return logLik;
    }
    runMCMC(nSamples = 5e3, burnIn = 1e3) {
      const { priorCL, priorVd, priorKa } = this.config;
      let CL = priorCL.mean;
      let Vd = priorVd.mean;
      let ka = priorKa.mean;
      let logLik = this.logLikelihood(CL, Vd, ka);
      const samples = {
        CL: [],
        Vd: [],
        ka: [],
        logLikelihood: []
      };
      let accepted = 0;
      const proposalStd = { CL: priorCL.std * 0.1, Vd: priorVd.std * 0.1, ka: priorKa.std * 0.1 };
      for (let i = 0; i < nSamples + burnIn; i++) {
        const CL_new = CL + this.randn() * proposalStd.CL;
        const Vd_new = Vd + this.randn() * proposalStd.Vd;
        const ka_new = ka + this.randn() * proposalStd.ka;
        if (CL_new > 0 && Vd_new > 0 && ka_new > 0) {
          const logLik_new = this.logLikelihood(CL_new, Vd_new, ka_new);
          const logPrior = -0.5 * (Math.pow((CL_new - priorCL.mean) / priorCL.std, 2) + Math.pow((Vd_new - priorVd.mean) / priorVd.std, 2) + Math.pow((ka_new - priorKa.mean) / priorKa.std, 2));
          const logPrior_old = -0.5 * (Math.pow((CL - priorCL.mean) / priorCL.std, 2) + Math.pow((Vd - priorVd.mean) / priorVd.std, 2) + Math.pow((ka - priorKa.mean) / priorKa.std, 2));
          const logAlpha = logLik_new + logPrior - (logLik + logPrior_old);
          if (Math.log(Math.random()) < logAlpha) {
            CL = CL_new;
            Vd = Vd_new;
            ka = ka_new;
            logLik = logLik_new;
            accepted++;
          }
        }
        if (i >= burnIn) {
          samples.CL.push(CL);
          samples.Vd.push(Vd);
          samples.ka.push(ka);
          samples.logLikelihood.push(logLik);
        }
      }
      const posteriorStats = this.calculatePosteriorStats(samples);
      return {
        samples,
        acceptance: accepted / (nSamples + burnIn),
        posteriorStats
      };
    }
    calculatePosteriorStats(samples) {
      const calcStats = (arr) => {
        const sorted = [...arr].sort((a, b) => a - b);
        const mean = arr.reduce((a, b) => a + b, 0) / arr.length;
        const variance = arr.reduce((sum, x) => sum + Math.pow(x - mean, 2), 0) / arr.length;
        const std = Math.sqrt(variance);
        const median = sorted[Math.floor(sorted.length / 2)];
        const ci95 = [
          sorted[Math.floor(sorted.length * 0.025)],
          sorted[Math.floor(sorted.length * 0.975)]
        ];
        return { mean, std, median, ci95 };
      };
      return {
        CL: calcStats(samples.CL),
        Vd: calcStats(samples.Vd),
        ka: calcStats(samples.ka)
      };
    }
  };
  return __toCommonJS(index_exports);
})();
//# sourceMappingURL=pkpd-bundle.js.map
