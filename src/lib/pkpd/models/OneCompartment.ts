import type { DrugInfo, SDEConfig, SimulationResult } from '../types';
import { EulerMaruyamaSolver, SymplecticSolver } from '../solvers';
import type { ODESolver } from '../solvers/types';

export class OneCompartmentModel {
  private solver: ODESolver;
  private drug: DrugInfo;
  private CL: number;
  private Vd: number;
  private ka: number;
  private F: number;

  constructor(drug: DrugInfo, sdeConfig: SDEConfig) {
    this.drug = drug;
    this.CL = drug.CL;
    this.Vd = drug.Vd;
    this.ka = drug.ka;
    this.F = drug.F;

    this.solver = sdeConfig.model === 'symplectic'
      ? new SymplecticSolver(sdeConfig.sigma)
      : new EulerMaruyamaSolver(sdeConfig.sigma);
  }

  private randn(): number {
    const u1 = Math.random();
    const u2 = Math.random();
    return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
  }

  private toUnit(val: number): number {
    if (this.drug.unit === 'pg/mL') return val * 1000;
    if (this.drug.unit === 'ng/dL') return val * 100;
    return val;
  }

  simulateMultiDose(
    dose_mg: number,
    interval_h: number,
    nDoses: number,
    totalDuration_h: number,
    dt_h: number
  ): SimulationResult {
    const n = Math.ceil(totalDuration_h / dt_h);
    const t: number[] = [];
    const C: number[] = [];

    let state = { A_depot: 0, A_central: 0 };

    const doseTimes: number[] = [];
    for (let d = 0; d < nDoses; d++) {
      doseTimes.push(d * interval_h);
    }

    let lastDoseIdx = -1;

    for (let i = 0; i < n; i++) {
      const currentTime = i * dt_h;
      t.push(currentTime);

      const doseIdx = doseTimes.findIndex(dt => Math.abs(currentTime - dt) < dt_h / 2);
      if (doseIdx !== -1 && doseIdx > lastDoseIdx) {
        state.A_depot += dose_mg * 1000 * this.F;
        lastDoseIdx = doseIdx;
      }

      const dW = this.randn() * Math.sqrt(dt_h);
      state = this.solver.step(state, this.CL, this.Vd, this.ka, dt_h, dW);

      const conc = this.toUnit(state.A_central / this.Vd);
      C.push(conc);
    }

    const steadyStateStart = Math.floor(C.length * 0.75);
    const steadyStateC = C.slice(steadyStateStart);
    const validC = steadyStateC.filter(c => c > 0.0001);

    const Cmax = Math.max(...steadyStateC);
    const Cmin = validC.length > 0 ? Math.min(...validC) : 0;
    const Tmax = t[steadyStateStart + steadyStateC.indexOf(Cmax)];
    const AUC = C.reduce((sum, c, i) => i === 0 ? 0 : sum + (C[i] + C[i - 1]) * dt_h / 2, 0);

    return { t, C, Cmax, Cmin, Tmax, AUC };
  }
}
