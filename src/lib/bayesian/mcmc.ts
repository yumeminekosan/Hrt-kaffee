import { OneCompartmentModel } from '../pkpd/models';
import { SeededRandom } from '../pkpd/random';
import type { DrugInfo, SampledPKParameters } from '../pkpd/types';

const LOG_TWO_PI = Math.log(2 * Math.PI);
const DEFAULT_SEED = 0x4D434D43;
const PARAMETER_NAMES = ['CL', 'Vd', 'ka', 'F'] as const;

export type BayesianParameterName = typeof PARAMETER_NAMES[number];

export interface ObservedConcentration {
  /** Hours from the first modelled dose. */
  time: number;
  /** Concentration in config.drug.unit. */
  concentration: number;
}

export interface LogNormalPrior {
  /** Median on the natural parameter scale. */
  median: number;
  /** Coefficient of variation as a fraction. */
  cv: number;
}

export interface LogisticNormalPrior {
  /** Median fraction, strictly between zero and one. */
  median: number;
  /** Standard deviation on the logit scale. */
  logitSD: number;
}

export interface BayesianPriors {
  CL: LogNormalPrior;
  Vd: LogNormalPrior;
  ka: LogNormalPrior;
  F: LogisticNormalPrior;
}

export interface BayesianConfig {
  observedData: ObservedConcentration[];
  drug: DrugInfo;
  priors: BayesianPriors;
  dose: number;
  interval: number;
  /** Defaults to all scheduled doses through the last observation. */
  nDoses?: number;
  observationError: {
    model: 'lognormal-proportional';
    /** Fixed proportional CV; it is never silently hard-coded or estimated. */
    proportionalCV: number;
  };
  seed?: number;
}

export interface MCMCOptions {
  /** Retained posterior draws per chain. */
  draws?: number;
  /** Adaptive warmup iterations per chain. */
  warmup?: number;
  /** At least four chains are required for the convergence report. */
  chains?: number;
  maxAutocorrelationLag?: number;
  posteriorPredictiveDraws?: number;
}

export interface ChainSamples {
  CL: number[];
  Vd: number[];
  ka: number[];
  F: number[];
  logLikelihood: number[];
  logPosterior: number[];
  acceptance: number;
  seed: number;
}

export interface ScalarChainDiagnostics {
  /** Rank-normalized split R-hat, folded when that is larger. */
  rhat: number;
  bulkESS: number;
  tailESS: number;
  autocorrelation: { lag: number; value: number }[];
}

export interface PosteriorSummary extends ScalarChainDiagnostics {
  mean: number;
  std: number;
  median: number;
  ci95: [number, number];
  mcseMean: number;
}

export interface MCMCDiagnostics {
  byParameter: Record<BayesianParameterName, ScalarChainDiagnostics>;
  maxRhat: number;
  minBulkESS: number;
  minTailESS: number;
  converged: boolean;
  warnings: string[];
}

export interface PosteriorPredictiveCheck {
  time: number[];
  observed: number[];
  median: number[];
  lower95: number[];
  upper95: number[];
  coverage95: number;
  rmseMedian: number;
  /** P(T(y_rep,theta) >= T(y,theta)); extreme values warn of misfit. */
  bayesianPValue: number;
  draws: number;
}

export interface MCMCResult {
  chains: ChainSamples[];
  /** Flattened retained draws, kept for export and backward-compatible plots. */
  samples: {
    CL: number[];
    Vd: number[];
    ka: number[];
    F: number[];
    logLikelihood: number[];
    logPosterior: number[];
  };
  acceptance: number;
  acceptanceByChain: number[];
  posteriorStats: Record<BayesianParameterName, PosteriorSummary>;
  diagnostics: MCMCDiagnostics;
  posteriorPredictive: PosteriorPredictiveCheck;
}

const mean = (values: number[]): number =>
  values.reduce((sum, value) => sum + value, 0) / values.length;

const variance = (values: number[], sample = true): number => {
  if (values.length < (sample ? 2 : 1)) return 0;
  const center = mean(values);
  const denominator = sample ? values.length - 1 : values.length;
  return values.reduce((sum, value) => sum + (value - center) ** 2, 0) / denominator;
};

const percentile = (values: number[], probability: number): number => {
  if (values.length === 0) throw new RangeError('percentile requires samples');
  const sorted = [...values].sort((a, b) => a - b);
  const position = (sorted.length - 1) * probability;
  const lower = Math.floor(position);
  const upper = Math.ceil(position);
  if (lower === upper) return sorted[lower];
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
};

const clamp = (value: number, lower: number, upper: number): number =>
  Math.max(lower, Math.min(upper, value));

const logit = (value: number): number => Math.log(value / (1 - value));
const logistic = (value: number): number => 1 / (1 + Math.exp(-value));
const logNormalSD = (cv: number): number => Math.sqrt(Math.log1p(cv * cv));

/** Acklam's inverse-standard-normal approximation. */
const inverseNormal = (probability: number): number => {
  const p = clamp(probability, Number.EPSILON, 1 - Number.EPSILON);
  const a = [
    -3.969683028665376e1, 2.209460984245205e2, -2.759285104469687e2,
    1.38357751867269e2, -3.066479806614716e1, 2.506628277459239
  ];
  const b = [
    -5.447609879822406e1, 1.615858368580409e2, -1.556989798598866e2,
    6.680131188771972e1, -1.328068155288572e1
  ];
  const c = [
    -7.784894002430293e-3, -3.223964580411365e-1, -2.400758277161838,
    -2.549732539343734, 4.374664141464968, 2.938163982698783
  ];
  const d = [
    7.784695709041462e-3, 3.224671290700398e-1,
    2.445134137142996, 3.754408661907416
  ];
  const low = 0.02425;
  const high = 1 - low;

  if (p < low) {
    const q = Math.sqrt(-2 * Math.log(p));
    return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
      / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
  }
  if (p > high) {
    const q = Math.sqrt(-2 * Math.log(1 - p));
    return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
      / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
  }

  const q = p - 0.5;
  const r = q * q;
  return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
    / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
};

const splitChains = (chains: number[][]): number[][] => {
  if (chains.length < 2) throw new RangeError('diagnostics require multiple chains');
  const length = Math.min(...chains.map(chain => chain.length));
  const half = Math.floor(length / 2);
  if (half < 2) throw new RangeError('diagnostics require at least four draws per chain');
  return chains.flatMap(chain => [
    chain.slice(0, half),
    chain.slice(length - half, length)
  ]);
};

const rankNormalize = (chains: number[][]): number[][] => {
  const lengths = chains.map(chain => chain.length);
  const pooled = chains.flat();
  const ordered = pooled
    .map((value, index) => ({ value, index }))
    .sort((left, right) => left.value - right.value);
  const ranks = new Array<number>(pooled.length);

  for (let start = 0; start < ordered.length;) {
    let end = start + 1;
    while (end < ordered.length && ordered[end].value === ordered[start].value) end++;
    const averageRank = ((start + 1) + end) / 2;
    for (let i = start; i < end; i++) ranks[ordered[i].index] = averageRank;
    start = end;
  }

  const normalized = ranks.map(rank =>
    inverseNormal((rank - 3 / 8) / (pooled.length + 1 / 4))
  );
  let offset = 0;
  return lengths.map(length => {
    const chain = normalized.slice(offset, offset + length);
    offset += length;
    return chain;
  });
};

const basicRhat = (chains: number[][]): number => {
  const chainLength = chains[0].length;
  const within = mean(chains.map(chain => variance(chain)));
  const between = chainLength * variance(chains.map(chain => mean(chain)));
  if (within === 0) return between === 0 ? 1 : Number.POSITIVE_INFINITY;
  const variancePlus = (chainLength - 1) / chainLength * within + between / chainLength;
  return Math.sqrt(Math.max(0, variancePlus / within));
};

const effectiveSampleSize = (chains: number[][]): number => {
  const chainCount = chains.length;
  const chainLength = chains[0].length;
  const total = chainCount * chainLength;
  const chainMeans = chains.map(chain => mean(chain));
  const within = mean(chains.map(chain => variance(chain)));
  const between = chainLength * variance(chainMeans);
  const variancePlus = (chainLength - 1) / chainLength * within + between / chainLength;
  if (variancePlus <= Number.EPSILON) return total;

  const autocorrelation = [1];
  for (let lag = 1; lag < chainLength; lag++) {
    const meanAutocovariance = mean(chains.map((chain, chainIndex) => {
      let covariance = 0;
      for (let i = 0; i < chainLength - lag; i++) {
        covariance += (chain[i] - chainMeans[chainIndex])
          * (chain[i + lag] - chainMeans[chainIndex]);
      }
      return covariance / chainLength;
    }));
    autocorrelation.push(1 - (within - meanAutocovariance) / variancePlus);
  }

  const pairs: number[] = [];
  for (let pair = 0; pair * 2 + 1 < autocorrelation.length; pair++) {
    const value = autocorrelation[pair * 2] + autocorrelation[pair * 2 + 1];
    if (pair > 0 && value < 0) break;
    pairs.push(value);
  }
  for (let i = 1; i < pairs.length; i++) pairs[i] = Math.min(pairs[i], pairs[i - 1]);
  const tau = Math.max(1, -1 + 2 * pairs.reduce((sum, value) => sum + value, 0));
  return Math.min(total, total / tau);
};

const averageAutocorrelation = (
  chains: number[][],
  maxLag: number
): { lag: number; value: number }[] => {
  const length = Math.min(...chains.map(chain => chain.length));
  const requested = [1, 5, 10, 20, 50]
    .filter((lag, index, values) => lag <= maxLag && lag < length && values.indexOf(lag) === index);
  return requested.map(lag => ({
    lag,
    value: mean(chains.map(chain => {
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

export const calculateChainDiagnostics = (
  chains: number[][],
  maxAutocorrelationLag = 50
): ScalarChainDiagnostics => {
  const split = splitChains(chains);
  const ranked = rankNormalize(split);
  const pooled = split.flat();
  const center = percentile(pooled, 0.5);
  const foldedRanked = rankNormalize(split.map(chain =>
    chain.map(value => Math.abs(value - center))
  ));
  const lower = percentile(pooled, 0.05);
  const upper = percentile(pooled, 0.95);
  const lowerIndicators = split.map(chain => chain.map(value => value <= lower ? 1 : 0));
  const upperIndicators = split.map(chain => chain.map(value => value >= upper ? 1 : 0));

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

const emptyChain = (seed: number): ChainSamples => ({
  CL: [], Vd: [], ka: [], F: [], logLikelihood: [], logPosterior: [],
  acceptance: 0,
  seed
});

export class BayesianEstimator {
  private readonly config: BayesianConfig;
  private readonly observations: ObservedConcentration[];
  private readonly priorCenters: number[];
  private readonly priorSDs: number[];
  private readonly nDoses: number;
  private readonly observationLogSD: number;

  constructor(config: BayesianConfig) {
    this.validateConfig(config);
    this.config = config;
    this.observations = [...config.observedData].sort((left, right) => left.time - right.time);
    this.priorCenters = [
      Math.log(config.priors.CL.median),
      Math.log(config.priors.Vd.median),
      Math.log(config.priors.ka.median),
      logit(config.priors.F.median)
    ];
    this.priorSDs = [
      logNormalSD(config.priors.CL.cv),
      logNormalSD(config.priors.Vd.cv),
      logNormalSD(config.priors.ka.cv),
      config.priors.F.logitSD
    ];
    const lastObservation = Math.max(...this.observations.map(observation => observation.time));
    this.nDoses = config.nDoses
      ?? Math.floor((lastObservation + 1e-9) / config.interval) + 1;
    this.observationLogSD = logNormalSD(config.observationError.proportionalCV);
  }

  private validateConfig(config: BayesianConfig): void {
    if (config.observedData.length < 2) {
      throw new RangeError('at least two observed concentrations are required');
    }
    for (const observation of config.observedData) {
      if (!Number.isFinite(observation.time) || observation.time < 0) {
        throw new RangeError('observation times must be finite and non-negative');
      }
      if (!Number.isFinite(observation.concentration) || observation.concentration <= 0) {
        throw new RangeError('observed concentrations must be finite and positive');
      }
    }
    if (!Number.isFinite(config.dose) || config.dose <= 0) throw new RangeError('dose must be positive');
    if (!Number.isFinite(config.interval) || config.interval <= 0) {
      throw new RangeError('interval must be positive');
    }
    if (config.nDoses !== undefined && (!Number.isInteger(config.nDoses) || config.nDoses <= 0)) {
      throw new RangeError('nDoses must be a positive integer');
    }
    for (const name of ['CL', 'Vd', 'ka'] as const) {
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
      throw new RangeError('F prior median must be in (0, 1)');
    }
    if (!Number.isFinite(fPrior.logitSD) || fPrior.logitSD <= 0 || fPrior.logitSD > 5) {
      throw new RangeError('F prior logitSD must be in (0, 5]');
    }
    const cv = config.observationError.proportionalCV;
    if (config.observationError.model !== 'lognormal-proportional'
      || !Number.isFinite(cv) || cv <= 0 || cv > 3) {
      throw new RangeError('an explicit proportional observation CV in (0, 3] is required');
    }
    if (config.seed !== undefined && !Number.isFinite(config.seed)) {
      throw new RangeError('seed must be finite');
    }
  }

  private naturalParameters(transformed: number[]): SampledPKParameters {
    return {
      CL: Math.exp(transformed[0]),
      Vd: Math.exp(transformed[1]),
      ka: Math.exp(transformed[2]),
      F: logistic(transformed[3]),
      activeMoietyFraction: this.config.drug.activeMoietyFraction ?? 1
    };
  }

  private logPrior(transformed: number[]): number {
    return transformed.reduce((sum, value, index) => {
      const standardized = (value - this.priorCenters[index]) / this.priorSDs[index];
      return sum - 0.5 * standardized ** 2 - Math.log(this.priorSDs[index])
        - 0.5 * LOG_TWO_PI;
    }, 0);
  }

  private predictions(parameters: SampledPKParameters): number[] {
    return new OneCompartmentModel(this.config.drug, parameters).predictAtTimes(
      this.config.dose,
      this.config.interval,
      this.nDoses,
      this.observations.map(observation => observation.time)
    );
  }

  private logLikelihood(transformed: number[]): number {
    const predicted = this.predictions(this.naturalParameters(transformed));
    const sigma = this.observationLogSD;
    let result = 0;
    for (let i = 0; i < predicted.length; i++) {
      if (!(predicted[i] > 0)) return Number.NEGATIVE_INFINITY;
      const observed = this.observations[i].concentration;
      const location = Math.log(predicted[i]) - 0.5 * sigma ** 2;
      const standardized = (Math.log(observed) - location) / sigma;
      result += -0.5 * standardized ** 2 - Math.log(observed * sigma)
        - 0.5 * LOG_TWO_PI;
    }
    return result;
  }

  private logTarget(transformed: number[]): { posterior: number; likelihood: number } {
    const likelihood = this.logLikelihood(transformed);
    return { posterior: likelihood + this.logPrior(transformed), likelihood };
  }

  private initializeChain(rng: SeededRandom): {
    state: number[];
    target: { posterior: number; likelihood: number };
  } {
    for (let attempt = 0; attempt < 100; attempt++) {
      const state = this.priorCenters.map((center, index) =>
        center + this.priorSDs[index] * rng.normal()
      );
      const target = this.logTarget(state);
      if (Number.isFinite(target.posterior)) return { state, target };
    }
    throw new Error('could not initialize a finite chain; check dosing history and sample times');
  }

  private runChain(seed: number, draws: number, warmup: number): ChainSamples {
    const rng = new SeededRandom(seed);
    let { state, target } = this.initializeChain(rng);
    const result = emptyChain(seed);
    const proposalSD = this.priorSDs.map(value => Math.max(0.01, value * 0.15));
    const adaptationAccepted = new Array<number>(PARAMETER_NAMES.length).fill(0);
    let accepted = 0;
    let proposals = 0;
    const adaptationWindow = 50;

    for (let iteration = 0; iteration < warmup + draws; iteration++) {
      for (let parameter = 0; parameter < PARAMETER_NAMES.length; parameter++) {
        const proposed = [...state];
        proposed[parameter] += proposalSD[parameter] * rng.normal();
        const proposedTarget = this.logTarget(proposed);
        proposals++;
        if (Math.log(Math.max(Number.MIN_VALUE, rng.uniform()))
          < proposedTarget.posterior - target.posterior) {
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
            this.priorSDs[parameter] * 0.005,
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

  private posteriorPredictive(
    samples: MCMCResult['samples'],
    requestedDraws: number,
    seed: number
  ): PosteriorPredictiveCheck {
    const total = samples.CL.length;
    const draws = Math.min(total, requestedDraws);
    const rng = new SeededRandom(seed ^ 0x50504331);
    const replicated = this.observations.map(() => [] as number[]);
    let replicatedMoreExtreme = 0;

    for (let draw = 0; draw < draws; draw++) {
      const index = Math.min(total - 1, Math.floor(rng.uniform() * total));
      const parameters: SampledPKParameters = {
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
        observedDiscrepancy += ((Math.log(this.observations[i].concentration) - location)
          / this.observationLogSD) ** 2;
        replicatedDiscrepancy += ((Math.log(simulated) - location)
          / this.observationLogSD) ** 2;
      }
      if (replicatedDiscrepancy >= observedDiscrepancy) replicatedMoreExtreme++;
    }

    const predictiveMedian = replicated.map(values => percentile(values, 0.5));
    const lower95 = replicated.map(values => percentile(values, 0.025));
    const upper95 = replicated.map(values => percentile(values, 0.975));
    const observed = this.observations.map(observation => observation.concentration);
    const covered = observed.filter((value, index) =>
      value >= lower95[index] && value <= upper95[index]
    ).length;

    return {
      time: this.observations.map(observation => observation.time),
      observed,
      median: predictiveMedian,
      lower95,
      upper95,
      coverage95: covered / observed.length,
      rmseMedian: Math.sqrt(mean(observed.map((value, index) =>
        (value - predictiveMedian[index]) ** 2
      ))),
      bayesianPValue: replicatedMoreExtreme / draws,
      draws
    };
  }

  runMCMC(options: MCMCOptions = {}): MCMCResult {
    const draws = options.draws ?? 1000;
    const warmup = options.warmup ?? 1000;
    const chainCount = options.chains ?? 4;
    const maxLag = options.maxAutocorrelationLag ?? 50;
    const predictiveDraws = options.posteriorPredictiveDraws ?? 400;
    for (const [name, value, minimum, maximum] of [
      ['draws', draws, 100, 100_000],
      ['warmup', warmup, 100, 100_000],
      ['chains', chainCount, 4, 16],
      ['maxAutocorrelationLag', maxLag, 1, 1_000],
      ['posteriorPredictiveDraws', predictiveDraws, 20, 10_000]
    ] as const) {
      if (!Number.isInteger(value) || value < minimum || value > maximum) {
        throw new RangeError(`${name} must be an integer between ${minimum} and ${maximum}`);
      }
    }

    const master = new SeededRandom(this.config.seed ?? DEFAULT_SEED);
    const chains = Array.from({ length: chainCount }, () => {
      const seed = master.nextUint32();
      return this.runChain(seed, draws, warmup);
    });
    const samples = {
      CL: chains.flatMap(chain => chain.CL),
      Vd: chains.flatMap(chain => chain.Vd),
      ka: chains.flatMap(chain => chain.ka),
      F: chains.flatMap(chain => chain.F),
      logLikelihood: chains.flatMap(chain => chain.logLikelihood),
      logPosterior: chains.flatMap(chain => chain.logPosterior)
    };

    const byParameter = Object.fromEntries(PARAMETER_NAMES.map(name => [
      name,
      calculateChainDiagnostics(chains.map(chain => chain[name]), maxLag)
    ])) as Record<BayesianParameterName, ScalarChainDiagnostics>;
    const maxRhat = Math.max(...PARAMETER_NAMES.map(name => byParameter[name].rhat));
    const minBulkESS = Math.min(...PARAMETER_NAMES.map(name => byParameter[name].bulkESS));
    const minTailESS = Math.min(...PARAMETER_NAMES.map(name => byParameter[name].tailESS));
    const requiredESS = 100 * chainCount;
    const warnings: string[] = [];
    if (maxRhat > 1.05 || !Number.isFinite(maxRhat)) {
      warnings.push('R-hat exceeds 1.05; chains have not demonstrated convergence.');
    }
    if (minBulkESS < requiredESS || minTailESS < requiredESS) {
      warnings.push(`Bulk/tail ESS should each reach at least ${requiredESS} for ${chainCount} chains.`);
    }
    if (this.observations.length <= PARAMETER_NAMES.length) {
      warnings.push('Sparse data relative to four free PK parameters; F, V and CL may remain weakly identified.');
    }
    const acceptanceByChain = chains.map(chain => chain.acceptance);
    if (acceptanceByChain.some(rate => rate < 0.1 || rate > 0.8)) {
      warnings.push('At least one random-walk chain has an extreme acceptance rate.');
    }
    const diagnostics: MCMCDiagnostics = {
      byParameter,
      maxRhat,
      minBulkESS,
      minTailESS,
      converged: warnings.length === 0,
      warnings
    };

    const posteriorStats = Object.fromEntries(PARAMETER_NAMES.map(name => {
      const values = samples[name];
      const std = Math.sqrt(variance(values));
      const chainDiagnostics = byParameter[name];
      return [name, {
        mean: mean(values),
        std,
        median: percentile(values, 0.5),
        ci95: [percentile(values, 0.025), percentile(values, 0.975)],
        mcseMean: std / Math.sqrt(Math.max(1, chainDiagnostics.bulkESS)),
        ...chainDiagnostics
      }];
    })) as Record<BayesianParameterName, PosteriorSummary>;

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
        this.config.seed ?? DEFAULT_SEED
      )
    };
  }
}
