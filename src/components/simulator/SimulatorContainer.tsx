'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/card';
import { DrugSelector } from './DrugSelector';
import { ParameterInputs } from './ParameterInputs';
import { SimulationControls } from './SimulationControls';
import { ResultsPanel } from './ResultsPanel';
import { useGPUSimulation } from '@/hooks/use-gpu-simulation';
import { DRUG_DB } from '@/lib/drugs';
import type { SimulationResult } from '@/lib/pkpd/types';

export function SimulatorContainer() {
  const [selectedDrug, setSelectedDrug] = useState('E2V');
  const [dose, setDose] = useState(5);
  const [interval, setInterval] = useState(168);
  const [numSims, setNumSims] = useState(100);
  const [sigma, setSigma] = useState(0.15);
  const [results, setResults] = useState<SimulationResult[] | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [usedGPU, setUsedGPU] = useState(false);

  const { runSimulation, gpuSupported } = useGPUSimulation();

  const handleRun = async () => {
    setIsRunning(true);
    const drug = DRUG_DB[selectedDrug];

    const config = {
      drug,
      dose,
      interval,
      duration: 1008,
      dt: 0.1,
      sde: { sigma, model: 'sde' as const }
    };

    const { results: simResults, usedGPU: gpu } = await runSimulation(config, numSims);
    setResults(simResults);
    setUsedGPU(gpu);
    setIsRunning(false);
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 p-4">
      <div className="lg:col-span-1 space-y-4">
        <Card className="p-4">
          <DrugSelector value={selectedDrug} onChange={setSelectedDrug} />
        </Card>
        <Card className="p-4">
          <ParameterInputs
            dose={dose}
            interval={interval}
            numSims={numSims}
            sigma={sigma}
            onDoseChange={setDose}
            onIntervalChange={setInterval}
            onNumSimsChange={setNumSims}
            onSigmaChange={setSigma}
          />
        </Card>
        <Card className="p-4">
          <SimulationControls
            isRunning={isRunning}
            gpuSupported={gpuSupported}
            onRun={handleRun}
          />
        </Card>
      </div>
      <div className="lg:col-span-2">
        <Card className="p-4">
          <ResultsPanel results={results} usedGPU={usedGPU} />
        </Card>
      </div>
    </div>
  );
}
