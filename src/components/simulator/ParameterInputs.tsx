'use client';

import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';

interface ParameterInputsProps {
  dose: number;
  interval: number;
  numSims: number;
  sigma: number;
  onDoseChange: (value: number) => void;
  onIntervalChange: (value: number) => void;
  onNumSimsChange: (value: number) => void;
  onSigmaChange: (value: number) => void;
}

export function ParameterInputs({
  dose, interval, numSims, sigma,
  onDoseChange, onIntervalChange, onNumSimsChange, onSigmaChange
}: ParameterInputsProps) {
  return (
    <div className="space-y-4">
      <div>
        <Label>剂量 (mg)</Label>
        <Input type="number" value={dose} onChange={e => onDoseChange(Number(e.target.value))} />
      </div>
      <div>
        <Label>给药间隔 (h)</Label>
        <Input type="number" value={interval} onChange={e => onIntervalChange(Number(e.target.value))} />
      </div>
      <div>
        <Label>模拟次数</Label>
        <Input type="number" value={numSims} onChange={e => onNumSimsChange(Number(e.target.value))} />
      </div>
      <div>
        <Label>随机性 (σ)</Label>
        <Input type="number" step="0.01" value={sigma} onChange={e => onSigmaChange(Number(e.target.value))} />
      </div>
    </div>
  );
}
