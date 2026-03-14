'use client';

import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { SimulationResult } from '@/lib/pkpd/types';

interface ChartDisplayProps {
  results: SimulationResult[];
}

export function ChartDisplay({ results }: ChartDisplayProps) {
  if (!results || results.length === 0) {
    return null;
  }

  const firstResult = results[0];
  const data = firstResult.t.map((time, i) => ({
    time,
    concentration: firstResult.C[i]
  }));

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis
          dataKey="time"
          label={{ value: '时间 (h)', position: 'insideBottom', offset: -5 }}
        />
        <YAxis
          label={{ value: '浓度 (pg/mL)', angle: -90, position: 'insideLeft' }}
        />
        <Tooltip />
        <Legend />
        <Line
          type="monotone"
          dataKey="concentration"
          stroke="#8884d8"
          dot={false}
          name="浓度"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
