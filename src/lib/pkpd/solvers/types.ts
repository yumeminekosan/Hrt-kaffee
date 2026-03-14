export interface ODEState {
  A_depot: number;
  A_central: number;
}

export interface ODESolver {
  step(state: ODEState, CL: number, Vd: number, ka: number, dt: number, dW: number): ODEState;
}
