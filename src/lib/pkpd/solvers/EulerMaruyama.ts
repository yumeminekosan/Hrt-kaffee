import type { ODESolver, ODEState } from './types';

export class EulerMaruyamaSolver implements ODESolver {
  constructor(private sigma: number) {}

  step(state: ODEState, CL: number, Vd: number, ka: number, dt: number, dW: number): ODEState {
    const ke = CL / Vd;

    const dA_dep = ka * state.A_depot * dt;
    let A_depot = state.A_depot - dA_dep;
    let A_central = state.A_central + dA_dep;

    const dA_elim = ke * A_central * dt;
    A_central -= dA_elim;

    if (this.sigma > 0) {
      const ito_correction = -0.5 * this.sigma * this.sigma * dt;
      A_central *= Math.exp(this.sigma * dW + ito_correction);
    }

    return {
      A_depot: Math.max(0, A_depot),
      A_central: Math.max(0, A_central)
    };
  }
}
