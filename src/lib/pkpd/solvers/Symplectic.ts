import type { ODESolver, ODEState } from './types';

export class SymplecticSolver implements ODESolver {
  constructor(private sigma: number) {}

  step(state: ODEState, CL: number, Vd: number, ka: number, dt: number, dW: number): ODEState {
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
}
