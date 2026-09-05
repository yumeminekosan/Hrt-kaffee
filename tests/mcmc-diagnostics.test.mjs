import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const context = vm.createContext({ console });
vm.runInContext(
  readFileSync(new URL('../public/pkpd-bundle.js', import.meta.url), 'utf8'),
  context,
  { filename: 'pkpd-bundle.js' }
);
const {
  BayesianEstimator,
  OneCompartmentModel,
  calculateChainDiagnostics
} = context.PKPD;

const auditDrug = {
  name: 'MCMC fixture',
  route: 'oral',
  therapeutic: [0, 100],
  unit: 'ng/mL',
  CL: 10,
  Vd: 100,
  ka: 1,
  F: 0.8,
  activeMoietyFraction: 1,
  halfLife: Math.log(2) * 10,
  doseUnit: 'mg',
  intervalUnit: 'h',
  defaultDose: 1,
  defaultInterval: 12,
  ref: 'Synthetic machine-check fixture',
  parameterization: 'illustrative'
};

const normalChains = (shiftLast = false) => {
  let state = 123456789;
  const uniform = () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return (state + 0.5) / 0x100000000;
  };
  const normal = () =>
    Math.sqrt(-2 * Math.log(uniform())) * Math.cos(2 * Math.PI * uniform());
  return Array.from({ length: 4 }, (_, chain) =>
    Array.from({ length: 1200 }, () => normal() + (shiftLast && chain === 3 ? 3 : 0))
  );
};

const syntheticConfig = () => {
  const times = [1, 2, 4, 8, 12, 18, 24, 36, 48];
  const latent = new OneCompartmentModel(auditDrug)
    .predictAtTimes(1, 12, 5, times);
  const factors = [1.02, 0.97, 1.03, 0.96, 1.01, 1.04, 0.98, 1.02, 0.99];
  return {
    observedData: times.map((time, index) => ({
      time,
      concentration: latent[index] * factors[index]
    })),
    drug: auditDrug,
    priors: {
      CL: { median: 11, cv: 0.35 },
      Vd: { median: 90, cv: 0.35 },
      ka: { median: 0.8, cv: 0.5 },
      F: { median: 0.75, logitSD: 0.6 }
    },
    dose: 1,
    interval: 12,
    nDoses: 5,
    observationError: {
      model: 'lognormal-proportional',
      proportionalCV: 0.1
    },
    seed: 8128
  };
};

test('rank-normalized split diagnostics distinguish mixed from shifted chains', () => {
  const mixed = calculateChainDiagnostics(normalChains(false), 20);
  const shifted = calculateChainDiagnostics(normalChains(true), 20);

  assert.ok(mixed.rhat < 1.02);
  assert.ok(mixed.bulkESS > 1000);
  assert.ok(mixed.tailESS > 1000);
  assert.ok(shifted.rhat > 1.1);
  assert.deepEqual(
    Array.from(mixed.autocorrelation, item => item.lag),
    [1, 5, 10, 20]
  );
});

test('four-chain transformed MCMC is reproducible and returns all required diagnostics', () => {
  const options = {
    chains: 4,
    warmup: 300,
    draws: 400,
    maxAutocorrelationLag: 20,
    posteriorPredictiveDraws: 80
  };
  const first = new BayesianEstimator(syntheticConfig()).runMCMC(options);
  const repeated = new BayesianEstimator(syntheticConfig()).runMCMC(options);

  assert.equal(first.chains.length, 4);
  assert.ok(first.chains.every(chain => chain.CL.length === options.draws));
  assert.deepEqual(first.samples, repeated.samples);
  assert.deepEqual(first.posteriorPredictive, repeated.posteriorPredictive);
  assert.ok(first.samples.CL.every(value => value > 0));
  assert.ok(first.samples.Vd.every(value => value > 0));
  assert.ok(first.samples.ka.every(value => value > 0));
  assert.ok(first.samples.F.every(value => value > 0 && value < 1));

  for (const name of ['CL', 'Vd', 'ka', 'F']) {
    const summary = first.posteriorStats[name];
    assert.ok(Number.isFinite(summary.rhat));
    assert.ok(summary.bulkESS > 0);
    assert.ok(summary.tailESS > 0);
    assert.ok(summary.autocorrelation.length > 0);
    assert.ok(summary.ci95[0] <= summary.median);
    assert.ok(summary.median <= summary.ci95[1]);
  }

  const ppc = first.posteriorPredictive;
  assert.equal(ppc.time.length, syntheticConfig().observedData.length);
  assert.equal(ppc.draws, options.posteriorPredictiveDraws);
  assert.ok(ppc.coverage95 >= 0 && ppc.coverage95 <= 1);
  assert.ok(ppc.bayesianPValue >= 0 && ppc.bayesianPValue <= 1);
  assert.ok(ppc.lower95.every((value, index) => value <= ppc.median[index]));
  assert.ok(ppc.upper95.every((value, index) => value >= ppc.median[index]));
});

test('observation error must be explicit and the live page fails closed on diagnostics', () => {
  const invalid = syntheticConfig();
  invalid.observationError.proportionalCV = 0;
  assert.throws(() => new BayesianEstimator(invalid), /explicit proportional observation CV/);

  const source = readFileSync(
    new URL('../src/lib/bayesian/mcmc.ts', import.meta.url),
    'utf8'
  );
  const page = readFileSync(
    new URL('../public/pkpd-simulator-v3.html', import.meta.url),
    'utf8'
  );
  assert.doesNotMatch(source, /Math\.random|const sigma\s*=\s*10/);
  assert.match(page, /new PKPD\.BayesianEstimator/);
  assert.match(page, /R-hat ≤ 1\.05/);
  assert.match(page, /applyBayesianBtn[^>]*disabled/);
  assert.match(page, /Application is blocked until all multi-chain and posterior-predictive gates are green/);
});
