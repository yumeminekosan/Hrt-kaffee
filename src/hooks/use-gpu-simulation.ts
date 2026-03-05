'use client';

import { useState, useEffect } from 'react';
import { GPUODESolver } from '@/lib/gpu';
import { PKPDSimulator } from '@/lib/pkpd/simulator';
import type { SimulationConfig, SimulationResult } from '@/lib/pkpd/types';

export function useGPUSimulation() {
  const [gpuSolver, setGpuSolver] = useState<GPUODESolver | null>(null);
  const [gpuSupported, setGpuSupported] = useState(false);
  const [cpuSimulator] = useState(() => new PKPDSimulator());

  useEffect(() => {
    const initGPU = async () => {
      const solver = new GPUODESolver();
      const supported = await solver.init();
      setGpuSupported(supported);
      if (supported) {
        setGpuSolver(solver);
      }
    };
    initGPU();
  }, []);

  const runSimulation = async (
    config: SimulationConfig,
    numSims: number = 100
  ): Promise<{ results: SimulationResult[]; usedGPU: boolean }> => {
    if (gpuSupported && gpuSolver && numSims >= 100) {
      try {
        const gpuConfig = {
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
        };

        const gpuResult = await gpuSolver.solvePK(gpuConfig);

        if (gpuResult.success) {
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
          return { results, usedGPU: true };
        }
      } catch (e) {
        console.warn('GPU simulation failed, falling back to CPU:', e);
      }
    }

    const results = cpuSimulator.monteCarloSimulation(config, numSims);
    return { results, usedGPU: false };
  };

  return { runSimulation, gpuSupported };
}
