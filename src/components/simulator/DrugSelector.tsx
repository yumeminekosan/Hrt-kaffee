'use client';

import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { DRUG_DB } from '@/lib/drugs';

interface DrugSelectorProps {
  value: string;
  onChange: (value: string) => void;
}

export function DrugSelector({ value, onChange }: DrugSelectorProps) {
  return (
    <div className="space-y-2">
      <Label>药物选择</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {Object.entries(DRUG_DB).map(([key, drug]) => (
            <SelectItem key={key} value={key}>
              {drug.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
