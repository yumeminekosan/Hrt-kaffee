'use client';

import { PKPDSimulator } from '@/lib/pkpd/simulator';
import type { SimulationResult } from '@/lib/pkpd/types';
import { Badge } from '@/components/ui/badge';
import { ChartDisplay } from './ChartDisplay';

interface ResultsPanelProps {
  results: SimulationResult[] | null;
  usedGPU: boolean;
}

export function ResultsPanel({ results, usedGPU }: ResultsPanelProps) {
  if (!results) {
    return <div className="text-muted-foreground">运行模拟以查看结果</div>;
  }

  const simulator = new PKPDSimulator();
  const stats = simulator.calculateStatistics(results);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <h3 className="text-lg font-semibold">模拟结果</h3>
        {usedGPU && <Badge>GPU 加速</Badge>}
      </div>

      <ChartDisplay results={results} />

      <div className="grid grid-cols-2 gap-4">
        <div className="p-4 border rounded">
          <div className="text-sm text-muted-foreground">Cmax (中位数)</div>
          <div className="text-2xl font-bold">{stats.Cmax.median.toFixed(1)}</div>
          <div className="text-xs text-muted-foreground">
            [{stats.Cmax.p25.toFixed(1)} - {stats.Cmax.p75.toFixed(1)}]
          </div>
        </div>
        <div className="p-4 border rounded">
          <div className="text-sm text-muted-foreground">Cmin (中位数)</div>
          <div className="text-2xl font-bold">{stats.Cmin.median.toFixed(1)}</div>
          <div className="text-xs text-muted-foreground">
            [{stats.Cmin.p25.toFixed(1)} - {stats.Cmin.p75.toFixed(1)}]
          </div>
        </div>
      </div>
    </div>
  );
}
