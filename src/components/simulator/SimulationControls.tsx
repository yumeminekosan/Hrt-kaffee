'use client';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface SimulationControlsProps {
  isRunning: boolean;
  gpuSupported: boolean;
  onRun: () => void;
}

export function SimulationControls({ isRunning, gpuSupported, onRun }: SimulationControlsProps) {
  return (
    <div className="space-y-4">
      <Button onClick={onRun} disabled={isRunning} className="w-full">
        {isRunning ? '运行中...' : '运行模拟'}
      </Button>
      {gpuSupported && (
        <Badge variant="outline" className="w-full justify-center">
          WebGPU 加速可用
        </Badge>
      )}
    </div>
  );
}
