import { OneCompartmentModel } from './models';
import { SeededRandom } from './random';
import type {
  MetricSummary,
  MonteCarloStatistics,
  ObservationErrorConfig,
  PopulationVariability,
  SampledPKParameters,
  SimulationConfig,
  SimulationResult
} from './types';

const DEFAULT_SEED = 0x4852544b;
const DEFAULT_VARIABILITY: PopulationVariability = {
  clCV: 0,
  vdCV: 0,
  kaCV: 0,
  fCV: 0,
  clVdCorrelation: 0
};

const assertCV = (name: string, value: number): void => {
  if (!Number.isFinite(value) || value < 0 || value > 3) {
    throw new RangeError(`${name} must be a finite fraction between 0 and 3`);
  }
};

const logNormalSigmaFromCV = (cv: number): number => Math.sqrt(Math.log1p(cv * cv));

const logNormalFactor = (rng: SeededRandom, cv: number): number =>
  cv === 0 ? 1 : Math.exp(logNormalSigmaFromCV(cv) * rng.normal());

const logistic = (value: number): number => 1 / (1 + Math.exp(-value));
const logit = (value: number): number => Math.log(value / (1 - value));

/** Logistic-normal draw with delta-method scaling so F remains inside (0, 1). */
const boundedFraction = (rng: SeededRandom, typical: number, cv: number): number => {
  if (cv === 0 || typical === 1) return typical;
  const logitSD = logNormalSigmaFromCV(cv) / Math.max(1e-6, 1 - typical);
  return logistic(logit(typical) + logitSD * rng.normal());
};

const percentile = (values: number[], probability: number): number => {
  if (values.length === 0) throw new RangeError('percentile requires at least one value');
  const sorted = [...values].sort((a, b) => a - b);
  const position = (sorted.length - 1) * probability;
  const lower = Math.floor(position);
  const upper = Math.ceil(position);
  if (lower === upper) return sorted[lower];
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
};

const summarize = (values: number[]): MetricSummary => {
  if (values.length === 0) throw new RangeError('summary requires at least one result');
  const mean = values.reduce((sum, value) => sum + value, 0) / values.length;
  const variance = values.length > 1
    ? values.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (values.length - 1)
    : 0;
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

export class PKPDSimulator {
  /**
   * Kept for compatibility with the old page controller. The former shader
   * used a non-normal pseudo-random increment and returned incomplete exposure
   * summaries, so audited simulations deliberately stay on the CPU.
   */
  async initGPU(): Promise<boolean> {
    return false;
  }

  isGPUAvailable(): boolean {
    return false;
  }

  private doseCount(config: SimulationConfig): number {
    if (config.interval <= 0 || config.duration <= 0) {
      throw new RangeError('interval and duration must be positive');
    }
    return Math.max(1, Math.ceil(config.duration / config.interval));
  }

  private simulateParameters(
    config: SimulationConfig,
    parameters: Partial<SampledPKParameters>
  ): SimulationResult {
    const model = new OneCompartmentModel(config.drug, parameters);
    return model.simulateMultiDose(
      config.dose,
      config.interval,
      this.doseCount(config),
      config.duration,
      config.dt
    );
  }

  simulate(config: SimulationConfig): SimulationResult {
    return this.simulateParameters(config, {});
  }

  private resolveVariability(config: SimulationConfig): PopulationVariability {
    const variability = {
      ...DEFAULT_VARIABILITY,
      ...config.drug.variability,
      ...config.population
    };
    assertCV('clCV', variability.clCV);
    assertCV('vdCV', variability.vdCV);
    assertCV('kaCV', variability.kaCV);
    assertCV('fCV', variability.fCV);
    const rho = variability.clVdCorrelation ?? 0;
    if (!Number.isFinite(rho) || rho < -0.99 || rho > 0.99) {
      throw new RangeError('clVdCorrelation must be between -0.99 and 0.99');
    }
    return { ...variability, clVdCorrelation: rho };
  }

  private sampleParameters(
    config: SimulationConfig,
    variability: PopulationVariability,
    rng: SeededRandom
  ): SampledPKParameters {
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

  private addObservationError(
    latent: number[],
    error: ObservationErrorConfig | undefined,
    rng: SeededRandom
  ): number[] | undefined {
    if (!error || error.model === 'none') return undefined;
    const additiveSD = error.additiveSD ?? 0;
    const proportionalCV = error.proportionalCV ?? 0;
    assertCV('proportionalCV', proportionalCV);
    if (!Number.isFinite(additiveSD) || additiveSD < 0) {
      throw new RangeError('additiveSD must be finite and non-negative');
    }

    const proportionalSigma = logNormalSigmaFromCV(proportionalCV);
    return latent.map(value => {
      const proportional = error.model === 'proportional' || error.model === 'combined'
        ? value * Math.exp(proportionalSigma * rng.normal() - 0.5 * proportionalSigma ** 2)
        : value;
      const additive = error.model === 'additive' || error.model === 'combined'
        ? additiveSD * rng.normal()
        : 0;
      return Math.max(0, proportional + additive);
    });
  }

  async monteCarloSimulation(
    config: SimulationConfig,
    numSims: number = config.numSimulations ?? 100
  ): Promise<SimulationResult[]> {
    if (!Number.isInteger(numSims) || numSims <= 0 || numSims > 100_000) {
      throw new RangeError('numSims must be an integer between 1 and 100000');
    }

    const variability = this.resolveVariability(config);
    const masterRng = new SeededRandom(config.seed ?? DEFAULT_SEED);
    const results: SimulationResult[] = [];

    for (let i = 0; i < numSims; i++) {
      const seed = masterRng.nextUint32();
      const rng = new SeededRandom(seed);
      const sampledParameters = config.mode === 'deterministic'
        ? {
            CL: config.drug.CL,
            Vd: config.drug.Vd,
            ka: config.drug.ka,
            F: config.drug.F,
            activeMoietyFraction: config.drug.activeMoietyFraction ?? 1
          }
        : this.sampleParameters(config, variability, rng);
      const result = this.simulateParameters(config, sampledParameters);
      const observedC = this.addObservationError(result.C, config.observationError, rng);
      results.push({ ...result, observedC, seed });
    }

    return results;
  }

  calculateStatistics(results: SimulationResult[]): MonteCarloStatistics {
    return {
      Cmax: summarize(results.map(result => result.Cmax)),
      Cmin: summarize(results.map(result => result.Cmin)),
      Tmax: summarize(results.map(result => result.Tmax)),
      AUCtau: summarize(results.map(result => result.AUCtau))
    };
  }
}
