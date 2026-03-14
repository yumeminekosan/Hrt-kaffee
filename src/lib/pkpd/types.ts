// 核心 PK/PD 类型定义

export interface PKParameters {
  CL: number;    // 清除率 (L/h)
  Vd: number;    // 分布容积 (L)
  ka: number;    // 吸收速率常数 (1/h)
  F: number;     // 生物利用度 (0-1)
}

export interface DrugInfo extends PKParameters {
  name: string;
  therapeutic: [number, number];  // 治疗窗 [min, max]
  unit: string;
  halfLife: number;               // 半衰期 (h)
  halfLifeApparent?: number;      // 表观半衰期 (h)
  doseUnit: string;
  intervalUnit: string;
  defaultDose: number;
  defaultInterval: number;
  ref: string;
}

export interface SDEConfig {
  sigma: number;
  model: 'deterministic' | 'sde' | 'stratonovich' | 'symplectic';
  symplecticMethod?: 'stormer-verlet' | 'yoshida4' | 'forest-ruth';
  stratonovichMethod?: 'heun' | 'milstein' | 'runge-kutta';
}

export interface SimulationConfig {
  drug: DrugInfo;
  dose: number;
  interval: number;
  duration: number;
  dt: number;
  sde: SDEConfig;
  numSimulations?: number;
}

export interface SimulationResult {
  t: number[];
  C: number[];
  Cmax: number;
  Cmin: number;
  Tmax: number;
  AUC: number;
}
