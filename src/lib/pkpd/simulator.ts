import { OneCompartmentModel } from './models';
import { GPUODESolver } from '../gpu/GPUODESolver';
import type { SimulationConfig, SimulationResult } from './types';

export class PKPDSimulator {
  private gpuSolver: GPUODESolver | null = null;
  private gpuInitialized: boolean = false;

  async initGPU(): Promise<boolean> {
    if (this.gpuInitialized) return this.gpuSolver?.isSupported() || false;

    this.gpuSolver = new GPUODESolver();
    this.gpuInitialized = await this.gpuSolver.init();
    return this.gpuInitialized;
  }

  isGPUAvailable(): boolean {
    return this.gpuSolver?.isSupported() || false;
  }

  simulate(config: SimulationConfig): SimulationResult {
    const model = new OneCompartmentModel(config.drug, config.sde);

    return model.simulateMultiDose(
      config.dose,
      config.interval,
      Math.ceil(config.duration / config.interval),
      config.duration,
      config.dt
    );
  }

  async monteCarloSimulation(
    config: SimulationConfig,
    numSims: number = 100
  ): Promise<SimulationResult[]> {
    // 尝试使用GPU加速
    if (this.gpuSolver?.isSupported()) {
      try {
        const gpuResult = await this.gpuSolver.solvePK({
          CL: config.drug.CL,
          Vd: config.drug.Vd,
          ka: config.drug.ka,
          F: config.drug.F,
          dose: config.dose,
          interval: config.interval,
          duration: config.duration,
          dt: config.dt,
          sigma: config.sde.sigma,
          numSims
        });

        if (gpuResult.success) {
          // 将GPU结果转换为SimulationResult格式
          const results: SimulationResult[] = [];
          for (let i = 0; i < numSims; i++) {
            results.push({
              t: [],
              C: [],
              Cmax: gpuResult.Cmax[i],
              Cmin: gpuResult.Cmin[i],
              Tmax: 0,
              AUC: 0
            });
          }
          return results;
        }
      } catch (e) {
        console.warn('GPU simulation failed, falling back to CPU:', e);
      }
    }

    // CPU fallback
    const results: SimulationResult[] = [];
    for (let i = 0; i < numSims; i++) {
      results.push(this.simulate(config));
    }
    return results;
  }

  calculateStatistics(results: SimulationResult[]) {
    const allCmax = results.map(r => r.Cmax);
    const allCmin = results.map(r => r.Cmin);
    const allAUC = results.map(r => r.AUC);

    const percentile = (arr: number[], p: number) => {
      const sorted = [...arr].sort((a, b) => a - b);
      const idx = Math.floor(sorted.length * p / 100);
      return sorted[idx];
    };

    return {
      Cmax: {
        median: percentile(allCmax, 50),
        p25: percentile(allCmax, 25),
        p75: percentile(allCmax, 75)
      },
      Cmin: {
        median: percentile(allCmin, 50),
        p25: percentile(allCmin, 25),
        p75: percentile(allCmin, 75)
      },
      AUC: {
        median: percentile(allAUC, 50),
        p25: percentile(allAUC, 25),
        p75: percentile(allAUC, 75)
      }
    };
  }
}
