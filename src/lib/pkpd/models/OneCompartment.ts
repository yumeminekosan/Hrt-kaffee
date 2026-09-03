import type {
  DrugInfo,
  SampledPKParameters,
  SimulationResult
} from '../types';
import { ExactLinearSolver } from '../solvers';
import type { PKState } from '../solvers';

const TIME_EPSILON = 1e-9;

const emptyState = (): PKState => ({
  A_site: 0,
  A_central: 0,
  A_unavailable: 0,
  A_eliminated: 0,
  A_administered: 0
});

const trapezoid = (t: number[], y: number[]): number => {
  let area = 0;
  for (let i = 1; i < t.length; i++) {
    area += (t[i] - t[i - 1]) * (y[i] + y[i - 1]) / 2;
  }
  return area;
};

/**
 * Route-aware extravascular/IV one-compartment model.
 *
 * Oral, sublingual, transdermal, IM-depot, and SC-depot routes share the same
 * declared first-order input topology but keep their route identity and
 * parameters explicit. IV bolus enters the central compartment directly.
 * No Hamiltonian, financial-volatility, or uncalibrated state-noise process is
 * attached to this dissipative mass-balance model.
 */
export class OneCompartmentModel {
  private readonly solver = new ExactLinearSolver();
  private readonly parameters: SampledPKParameters;

  constructor(
    private readonly drug: DrugInfo,
    parameterOverride: Partial<SampledPKParameters> = {}
  ) {
    this.parameters = {
      CL: parameterOverride.CL ?? drug.CL,
      Vd: parameterOverride.Vd ?? drug.Vd,
      ka: parameterOverride.ka ?? drug.ka,
      F: parameterOverride.F ?? drug.F,
      activeMoietyFraction: parameterOverride.activeMoietyFraction
        ?? drug.activeMoietyFraction
        ?? 1
    };
    this.validateParameters();
  }

  private validateParameters(): void {
    const { CL, Vd, ka, F, activeMoietyFraction } = this.parameters;
    if (!Number.isFinite(CL) || CL <= 0) throw new RangeError('CL must be positive');
    if (!Number.isFinite(Vd) || Vd <= 0) throw new RangeError('Vd must be positive');
    if (!Number.isFinite(ka) || ka <= 0) throw new RangeError('ka must be positive');
    if (!Number.isFinite(F) || F <= 0 || F > 1) throw new RangeError('F must be in (0, 1]');
    if (!Number.isFinite(activeMoietyFraction) || activeMoietyFraction <= 0 || activeMoietyFraction > 1) {
      throw new RangeError('activeMoietyFraction must be in (0, 1]');
    }
  }

  private toDisplayUnit(concentrationNgPerMl: number): number {
    if (this.drug.unit === 'pg/mL') return concentrationNgPerMl * 1000;
    if (this.drug.unit === 'ng/dL') return concentrationNgPerMl * 100;
    return concentrationNgPerMl;
  }

  private applyDose(state: PKState, doseMg: number): PKState {
    const activeDoseMicrograms = doseMg * 1000 * this.parameters.activeMoietyFraction;
    const next = {
      ...state,
      A_administered: state.A_administered + activeDoseMicrograms
    };

    if (this.drug.route === 'intravenous-bolus') {
      return {
        ...next,
        A_central: next.A_central + activeDoseMicrograms * this.parameters.F,
        A_unavailable: next.A_unavailable + activeDoseMicrograms * (1 - this.parameters.F)
      };
    }

    return { ...next, A_site: next.A_site + activeDoseMicrograms };
  }

  private advance(state: PKState, dt: number): PKState {
    if (dt <= TIME_EPSILON) return state;
    return this.solver.step(state, { ...this.parameters, dt });
  }

  private analysisWindow(interval: number, nDoses: number, duration: number): [number, number] {
    if (duration <= interval || nDoses <= 1) return [0, duration];
    const lastCompleteIndex = Math.max(
      0,
      Math.min(nDoses - 1, Math.floor((duration - interval + TIME_EPSILON) / interval))
    );
    const start = lastCompleteIndex * interval;
    return [start, Math.min(duration, start + interval)];
  }

  private outputTimes(
    dt: number,
    interval: number,
    nDoses: number,
    duration: number,
    window: [number, number]
  ): number[] {
    const times = new Set<number>([0, duration, window[0], window[1]]);
    const steps = Math.ceil(duration / dt);
    for (let i = 1; i < steps; i++) times.add(Math.min(duration, i * dt));
    for (let d = 0; d < nDoses; d++) {
      const doseTime = d * interval;
      if (doseTime <= duration + TIME_EPSILON) times.add(Math.min(duration, doseTime));
    }
    return [...times].sort((a, b) => a - b);
  }

  simulateMultiDose(
    doseMg: number,
    interval: number,
    nDoses: number,
    duration: number,
    dt: number
  ): SimulationResult {
    if (!Number.isFinite(doseMg) || doseMg <= 0) throw new RangeError('dose must be positive');
    if (!Number.isFinite(interval) || interval <= 0) throw new RangeError('interval must be positive');
    if (!Number.isInteger(nDoses) || nDoses <= 0) throw new RangeError('nDoses must be a positive integer');
    if (!Number.isFinite(duration) || duration <= 0) throw new RangeError('duration must be positive');
    if (!Number.isFinite(dt) || dt <= 0) throw new RangeError('dt must be positive');

    const analysisWindow = this.analysisWindow(interval, nDoses, duration);
    const t = this.outputTimes(dt, interval, nDoses, duration, analysisWindow);
    const C: number[] = [];
    const intervalC: number[] = [];
    let state = emptyState();
    let currentTime = 0;
    let nextDoseIndex = 0;

    for (const targetTime of t) {
      // Dose times are part of the output grid, so the state can first be
      // advanced exactly to the event. Keep the left limit for an interval
      // endpoint: an IV bolus at the next interval boundary must not inflate
      // the preceding interval's Cmax/AUCtau.
      state = this.advance(state, targetTime - currentTime);
      currentTime = targetTime;
      const concentrationBeforeDose = this.toDisplayUnit(
        state.A_central / this.parameters.Vd
      );

      while (nextDoseIndex < nDoses) {
        const doseTime = nextDoseIndex * interval;
        if (Math.abs(doseTime - targetTime) > TIME_EPSILON) break;
        state = this.applyDose(state, doseMg);
        nextDoseIndex++;
      }

      const concentrationAfterDose = this.toDisplayUnit(
        state.A_central / this.parameters.Vd
      );
      C.push(concentrationAfterDose);
      intervalC.push(
        targetTime > analysisWindow[0] + TIME_EPSILON
          && Math.abs(targetTime - analysisWindow[1]) <= TIME_EPSILON
          ? concentrationBeforeDose
          : concentrationAfterDose
      );
    }

    const [windowStart, windowEnd] = analysisWindow;
    const windowIndices = t
      .map((time, index) => ({ time, index }))
      .filter(({ time }) => time >= windowStart - TIME_EPSILON && time <= windowEnd + TIME_EPSILON)
      .map(({ index }) => index);
    const windowT = windowIndices.map(index => t[index]);
    const windowC = windowIndices.map(index => intervalC[index]);
    const Cmax = Math.max(...windowC);
    const Cmin = Math.min(...windowC);
    const peakIndex = windowC.indexOf(Cmax);
    const Tmax = windowT[peakIndex] - windowStart;
    const accounted = state.A_site + state.A_central + state.A_unavailable + state.A_eliminated;
    const massBalanceError = Math.abs(state.A_administered - accounted)
      / Math.max(1, state.A_administered);

    return {
      t,
      C,
      Cmax,
      Cmin,
      Tmax,
      AUC: trapezoid(t, C),
      AUCtau: trapezoid(windowT, windowC),
      analysisWindow,
      route: this.drug.route,
      sampledParameters: { ...this.parameters },
      massBalanceError
    };
  }
}
