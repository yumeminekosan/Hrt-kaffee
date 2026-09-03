// Core PK and simulation types. Concentrations are returned in DrugInfo.unit.

export type DosingRoute =
  | 'oral'
  | 'sublingual'
  | 'transdermal'
  | 'intramuscular-depot'
  | 'subcutaneous-depot'
  | 'intravenous-bolus';

export type ParameterizationStatus =
  | 'population'
  | 'illustrative'
  | 'individualized';

export interface PKParameters {
  CL: number; // clearance (L/h)
  Vd: number; // apparent central volume (L)
  ka: number; // absorption/depot-release rate (1/h); ignored for IV bolus
  F: number; // fraction reaching the central compartment (0, 1]
}

export interface PopulationVariability {
  /** Coefficients of variation, expressed as fractions rather than percentages. */
  clCV: number;
  vdCV: number;
  kaCV: number;
  fCV: number;
  /** Correlation between the normal random effects for CL and Vd. */
  clVdCorrelation?: number;
}

export interface DrugInfo extends PKParameters {
  name: string;
  route: DosingRoute;
  therapeutic: [number, number];
  unit: string;
  halfLife: number;
  halfLifeApparent?: number;
  doseUnit: string;
  intervalUnit: string;
  defaultDose: number;
  defaultInterval: number;
  ref: string;
  /**
   * Converts administered ester/prodrug mass to active-moiety mass. A value of
   * one means that the empirical parameterization already uses active-moiety
   * equivalent dose. It is never inferred silently.
   */
  activeMoietyFraction?: number;
  parameterization?: ParameterizationStatus;
  variability?: PopulationVariability;
}

export interface ObservationErrorConfig {
  model: 'none' | 'additive' | 'proportional' | 'combined';
  /** Additive standard deviation in the same unit as the returned concentration. */
  additiveSD?: number;
  /** Proportional coefficient of variation as a fraction. */
  proportionalCV?: number;
}

export interface SimulationConfig {
  drug: DrugInfo;
  dose: number;
  interval: number;
  duration: number;
  dt: number;
  /** Deterministic solves one typical profile; population samples PK parameters. */
  mode?: 'deterministic' | 'population';
  population?: Partial<PopulationVariability>;
  observationError?: ObservationErrorConfig;
  seed?: number;
  numSimulations?: number;
}

export interface SampledPKParameters extends PKParameters {
  activeMoietyFraction: number;
}

export interface SimulationResult {
  t: number[];
  /** Latent structural-model concentration. */
  C: number[];
  /** Optional simulated measurement, kept separate from the latent state. */
  observedC?: number[];
  Cmax: number;
  Cmin: number;
  /** Time from the start of the analysed dosing interval to Cmax (h). */
  Tmax: number;
  /** AUC over the complete simulated horizon. */
  AUC: number;
  /** AUC over the final complete dosing interval (or full horizon if shorter). */
  AUCtau: number;
  analysisWindow: [number, number];
  route: DosingRoute;
  sampledParameters: SampledPKParameters;
  massBalanceError: number;
  seed?: number;
}

export interface MetricSummary {
  median: number;
  p05: number;
  p25: number;
  p75: number;
  p95: number;
  mean: number;
  standardError: number;
}

export interface MonteCarloStatistics {
  Cmax: MetricSummary;
  Cmin: MetricSummary;
  Tmax: MetricSummary;
  AUCtau: MetricSummary;
}
