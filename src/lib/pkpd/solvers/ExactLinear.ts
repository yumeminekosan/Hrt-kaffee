import type { LinearPKStep, PKSolver, PKState } from './types';

const RATE_EPSILON = 1e-10;

/**
 * Closed-form transition for a first-order administration site feeding a
 * one-compartment, first-order elimination model. The transition is positive
 * for every dt >= 0 and does not need clipping to hide an unstable Euler step.
 */
export class ExactLinearSolver implements PKSolver {
  step(state: PKState, parameters: LinearPKStep): PKState {
    const { CL, Vd, ka, F, dt } = parameters;
    if (dt < 0 || CL <= 0 || Vd <= 0 || ka <= 0 || F <= 0 || F > 1) {
      throw new RangeError('ExactLinearSolver requires dt >= 0, positive PK rates, and 0 < F <= 1');
    }
    if (dt === 0) return { ...state };

    const ke = CL / Vd;
    const siteBefore = state.A_site;
    const centralBefore = state.A_central;
    const expKa = Math.exp(-ka * dt);
    const expKe = Math.exp(-ke * dt);
    const A_site = siteBefore * expKa;
    const released = siteBefore - A_site;

    const centralInput = Math.abs(ka - ke) < RATE_EPSILON
      ? F * ka * siteBefore * dt * expKe
      : F * ka * siteBefore * (expKa - expKe) / (ke - ka);
    const A_central = centralBefore * expKe + centralInput;
    const unavailableIncrement = (1 - F) * released;
    const eliminatedIncrement = centralBefore + F * released - A_central;

    return {
      A_site: Math.max(0, A_site),
      A_central: Math.max(0, A_central),
      A_unavailable: state.A_unavailable + Math.max(0, unavailableIncrement),
      A_eliminated: state.A_eliminated + Math.max(0, eliminatedIncrement),
      A_administered: state.A_administered
    };
  }
}
