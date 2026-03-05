import type { DrugInfo, SimulationResult } from '../pkpd/types';

export interface BayesianConfig {
  observedData: { time: number; concentration: number }[];
  priorCL: { mean: number; std: number };
  priorVd: { mean: number; std: number };
  priorKa: { mean: number; std: number };
  dose: number;
  interval: number;
  nDoses: number;
  duration: number;
  dt: number;
  F: number;
}

export interface MCMCResult {
  samples: {
    CL: number[];
    Vd: number[];
    ka: number[];
    logLikelihood: number[];
  };
  acceptance: number;
  posteriorStats: {
    CL: { mean: number; std: number; median: number; ci95: [number, number] };
    Vd: { mean: number; std: number; median: number; ci95: [number, number] };
    ka: { mean: number; std: number; median: number; ci95: [number, number] };
  };
}

export class BayesianEstimator {
  constructor(private config: BayesianConfig) {}

  private randn(): number {
    const u1 = Math.random();
    const u2 = Math.random();
    return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
  }

  private pkModel(CL: number, Vd: number, ka: number): number[] {
    const { dose, interval, nDoses, duration, dt, F } = this.config;
    const n = Math.ceil(duration / dt);
    const C: number[] = [];
    let A_depot = 0;
    let A_central = 0;
    const ke = CL / Vd;

    const doseTimes: number[] = [];
    for (let d = 0; d < nDoses; d++) {
      doseTimes.push(d * interval);
    }

    let lastDoseIdx = -1;

    for (let i = 0; i < n; i++) {
      const currentTime = i * dt;
      const doseIdx = doseTimes.findIndex(t => Math.abs(currentTime - t) < dt / 2);
      if (doseIdx !== -1 && doseIdx > lastDoseIdx) {
        A_depot += dose * 1000 * F;
        lastDoseIdx = doseIdx;
      }

      const dA_depot = -ka * A_depot * dt;
      const dA_central = (ka * A_depot - ke * A_central) * dt;
      A_depot += dA_depot;
      A_central += dA_central;

      C.push(A_central / Vd);
    }

    return C;
  }

  private logLikelihood(CL: number, Vd: number, ka: number): number {
    const predicted = this.pkModel(CL, Vd, ka);
    const { observedData, dt } = this.config;
    let logLik = 0;
    const sigma = 10;

    for (const obs of observedData) {
      const idx = Math.round(obs.time / dt);
      if (idx >= 0 && idx < predicted.length) {
        const pred = predicted[idx];
        const residual = obs.concentration - pred;
        logLik -= 0.5 * (residual * residual) / (sigma * sigma);
      }
    }

    return logLik;
  }

  runMCMC(nSamples: number = 5000, burnIn: number = 1000): MCMCResult {
    const { priorCL, priorVd, priorKa } = this.config;

    let CL = priorCL.mean;
    let Vd = priorVd.mean;
    let ka = priorKa.mean;
    let logLik = this.logLikelihood(CL, Vd, ka);

    const samples = {
      CL: [] as number[],
      Vd: [] as number[],
      ka: [] as number[],
      logLikelihood: [] as number[]
    };

    let accepted = 0;
    const proposalStd = { CL: priorCL.std * 0.1, Vd: priorVd.std * 0.1, ka: priorKa.std * 0.1 };

    for (let i = 0; i < nSamples + burnIn; i++) {
      const CL_new = CL + this.randn() * proposalStd.CL;
      const Vd_new = Vd + this.randn() * proposalStd.Vd;
      const ka_new = ka + this.randn() * proposalStd.ka;

      if (CL_new > 0 && Vd_new > 0 && ka_new > 0) {
        const logLik_new = this.logLikelihood(CL_new, Vd_new, ka_new);
        const logPrior = -0.5 * (
          Math.pow((CL_new - priorCL.mean) / priorCL.std, 2) +
          Math.pow((Vd_new - priorVd.mean) / priorVd.std, 2) +
          Math.pow((ka_new - priorKa.mean) / priorKa.std, 2)
        );
        const logPrior_old = -0.5 * (
          Math.pow((CL - priorCL.mean) / priorCL.std, 2) +
          Math.pow((Vd - priorVd.mean) / priorVd.std, 2) +
          Math.pow((ka - priorKa.mean) / priorKa.std, 2)
        );

        const logAlpha = (logLik_new + logPrior) - (logLik + logPrior_old);

        if (Math.log(Math.random()) < logAlpha) {
          CL = CL_new;
          Vd = Vd_new;
          ka = ka_new;
          logLik = logLik_new;
          accepted++;
        }
      }

      if (i >= burnIn) {
        samples.CL.push(CL);
        samples.Vd.push(Vd);
        samples.ka.push(ka);
        samples.logLikelihood.push(logLik);
      }
    }

    const posteriorStats = this.calculatePosteriorStats(samples);
    return {
      samples,
      acceptance: accepted / (nSamples + burnIn),
      posteriorStats
    };
  }

  private calculatePosteriorStats(samples: MCMCResult['samples']): MCMCResult['posteriorStats'] {
    const calcStats = (arr: number[]) => {
      const sorted = [...arr].sort((a, b) => a - b);
      const mean = arr.reduce((a, b) => a + b, 0) / arr.length;
      const variance = arr.reduce((sum, x) => sum + Math.pow(x - mean, 2), 0) / arr.length;
      const std = Math.sqrt(variance);
      const median = sorted[Math.floor(sorted.length / 2)];
      const ci95: [number, number] = [
        sorted[Math.floor(sorted.length * 0.025)],
        sorted[Math.floor(sorted.length * 0.975)]
      ];
      return { mean, std, median, ci95 };
    };

    return {
      CL: calcStats(samples.CL),
      Vd: calcStats(samples.Vd),
      ka: calcStats(samples.ka)
    };
  }
}
