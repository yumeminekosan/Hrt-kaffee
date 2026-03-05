export interface GPUSimulationConfig {
  CL: number;
  Vd: number;
  ka: number;
  F: number;
  dose: number;
  interval: number;
  duration: number;
  dt: number;
  sigma: number;
  numSims: number;
}

export interface GPUSimulationResult {
  Cmax: Float32Array;
  Cmin: Float32Array;
  success: boolean;
}
