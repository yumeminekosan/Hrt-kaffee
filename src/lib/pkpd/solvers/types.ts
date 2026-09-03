export interface PKState {
  /** Amount remaining at the administration site (micrograms active-moiety equivalent). */
  A_site: number;
  /** Amount in the central compartment (micrograms). */
  A_central: number;
  /** Amount lost before reaching the central compartment (micrograms). */
  A_unavailable: number;
  /** Cumulative eliminated amount from the central compartment (micrograms). */
  A_eliminated: number;
  /** Cumulative administered active-moiety-equivalent amount (micrograms). */
  A_administered: number;
}

export interface LinearPKStep {
  CL: number;
  Vd: number;
  ka: number;
  F: number;
  dt: number;
}

export interface PKSolver {
  step(state: PKState, parameters: LinearPKStep): PKState;
}
