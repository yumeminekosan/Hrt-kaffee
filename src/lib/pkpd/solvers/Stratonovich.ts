import type { ODESolver, PKState } from './types';

export class StratonovichSolver implements ODESolver {
  constructor(private sigma: number) {}

  step(state: PKState, CL: number, Vd: number, ka: number, dt: number, dW: number): PKState {
    const ke = CL / Vd;
    
    const k1_depot = -ka * state.A_depot;
    const k1_central = ka * state.A_depot - ke * state.A_central;
    
    const mid_depot = state.A_depot + 0.5 * dt * k1_depot;
    const mid_central = state.A_central + 0.5 * dt * k1_central;
    
    const k2_depot = -ka * mid_depot;
    const k2_central = ka * mid_depot - ke * mid_central;
    
    return {
      A_depot: state.A_depot + dt * k2_depot,
      A_central: state.A_central + dt * k2_central + this.sigma * state.A_central * dW
    };
  }
}
