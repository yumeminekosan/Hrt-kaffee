import { describe, it, expect } from '@jest/globals';
import { DRUG_DB } from '../src/lib/drugs';
import { PKPDSimulator } from '../src/lib/pkpd/simulator';
import { OneCompartmentModel } from '../src/lib/pkpd/models';

describe('药物数据库', () => {
  it('应包含E2V药物', () => {
    expect(DRUG_DB.E2V).toBeDefined();
    expect(DRUG_DB.E2V.name).toBe('Estradiol Valerate IM');
  });

  it('E2V参数应正确', () => {
    const drug = DRUG_DB.E2V;
    expect(drug.CL).toBe(100);
    expect(drug.Vd).toBe(2400);
    expect(drug.ka).toBe(0.012);
    expect(drug.F).toBe(0.85);
  });
});

describe('OneCompartment模型', () => {
  it('确定性模拟应接近预期值', () => {
    const model = new OneCompartmentModel(
      DRUG_DB.E2V,
      { sigma: 0, model: 'deterministic' }
    );

    const result = model.simulateMultiDose(5, 168, 6, 1008, 0.1);

    expect(result.Cmax).toBeGreaterThan(350);
    expect(result.Cmax).toBeLessThan(400);
    expect(result.Cmin).toBeGreaterThan(100);
    expect(result.Cmin).toBeLessThan(150);
  });
});

describe('PKPDSimulator', () => {
  it('蒙特卡洛模拟应返回正确数量的结果', () => {
    const simulator = new PKPDSimulator();
    const config = {
      drug: DRUG_DB.E2V,
      dose: 5,
      interval: 168,
      duration: 1008,
      dt: 0.1,
      sde: { sigma: 0.15, model: 'sde' as const }
    };

    const results = simulator.monteCarloSimulation(config, 10);
    expect(results).toHaveLength(10);
  });

  it('统计计算应返回中位数', () => {
    const simulator = new PKPDSimulator();
    const mockResults = [
      { t: [], C: [], Cmax: 100, Cmin: 50, Tmax: 0, AUC: 0 },
      { t: [], C: [], Cmax: 200, Cmin: 60, Tmax: 0, AUC: 0 },
      { t: [], C: [], Cmax: 300, Cmin: 70, Tmax: 0, AUC: 0 }
    ];

    const stats = simulator.calculateStatistics(mockResults);
    expect(stats.Cmax.median).toBe(200);
  });
});
