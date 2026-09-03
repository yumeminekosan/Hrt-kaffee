'use client';

import { useState } from 'react';
import { PKPDSimulator } from '@/lib/pkpd/simulator';
import type { SimulationConfig, SimulationResult } from '@/lib/pkpd/types';

/**
 * Compatibility hook for the React shell. Audited population simulation stays
 * on the CPU until a GPU implementation reproduces the same parameter draws,
 * event-aligned trajectories, AUCtau, and mass-balance checks.
 */
export function useGPUSimulation() {
  const [simulator] = useState(() => new PKPDSimulator());

  const runSimulation = async (
    config: SimulationConfig,
    numSims: number = 100
  ): Promise<{ results: SimulationResult[]; usedGPU: boolean }> => ({
    results: await simulator.monteCarloSimulation(config, numSims),
    usedGPU: false
  });

  return { runSimulation, gpuSupported: false };
}
