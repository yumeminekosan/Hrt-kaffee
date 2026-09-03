import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const bundlePath = new URL('../public/pkpd-bundle.js', import.meta.url);
const pagePath = new URL('../public/pkpd-simulator-v3.html', import.meta.url);
const redirectPath = new URL('../public/pkpd-simulator-integrated.html', import.meta.url);

const context = vm.createContext({ console });
vm.runInContext(readFileSync(bundlePath, 'utf8'), context, {
  filename: 'pkpd-bundle.js'
});
const { DRUG_DB, OneCompartmentModel, PKPDSimulator } = context.PKPD;

const syntheticDrug = (overrides = {}) => ({
  name: 'Synthetic audit drug',
  route: 'oral',
  therapeutic: [0, 1_000],
  unit: 'ng/mL',
  CL: 10,
  Vd: 100,
  ka: 1,
  F: 0.8,
  activeMoietyFraction: 0.75,
  halfLife: Math.log(2) * 10,
  doseUnit: 'mg',
  intervalUnit: 'h',
  defaultDose: 1,
  defaultInterval: 12,
  ref: 'Synthetic machine-check fixture',
  ...overrides
});

const at = (result, time) => {
  const index = result.t.findIndex(value => Math.abs(value - time) < 1e-10);
  assert.notEqual(index, -1, `missing event-aligned output at ${time} h`);
  return result.C[index];
};

test('canonical drug database declares route, provenance status and ester mass conversion', () => {
  assert.equal(DRUG_DB.E2V.route, 'intramuscular-depot');
  assert.equal(DRUG_DB.E2V.parameterization, 'population');
  assert.equal(DRUG_DB.E2C.parameterization, 'illustrative');
  assert.ok(DRUG_DB.E2V.activeMoietyFraction > 0);
  assert.ok(DRUG_DB.E2V.activeMoietyFraction < 1);
});

test('exact route model is positive, mass-balanced and invariant at shared output times', () => {
  const model = new OneCompartmentModel(syntheticDrug());
  const coarse = model.simulateMultiDose(1, 12, 8, 96, 0.5);
  const fine = model.simulateMultiDose(1, 12, 8, 96, 0.125);

  assert.ok(coarse.C.every(value => value >= 0));
  assert.ok(fine.C.every(value => value >= 0));
  assert.ok(coarse.massBalanceError < 1e-12);
  assert.ok(fine.massBalanceError < 1e-12);
  for (let time = 0; time <= 96; time += 0.5) {
    assert.ok(Math.abs(at(coarse, time) - at(fine, time)) < 1e-11);
  }
});

test('dose events are inserted even when dt does not divide the interval', () => {
  const result = new OneCompartmentModel(syntheticDrug())
    .simulateMultiDose(1, 7, 4, 24, 2.3);
  for (const doseTime of [0, 7, 14, 21]) at(result, doseTime);
  assert.deepEqual(Array.from(result.analysisWindow), [14, 21]);
});

test('an IV bolus at the closing boundary does not enter the preceding interval metrics', () => {
  const drug = syntheticDrug({
    route: 'intravenous-bolus', F: 1, activeMoietyFraction: 1
  });
  const result = new OneCompartmentModel(drug)
    .simulateMultiDose(1, 24, 3, 50, 0.25);

  assert.deepEqual(Array.from(result.analysisWindow), [24, 48]);
  assert.ok(at(result, 48) > result.Cmax);
  assert.ok(result.massBalanceError < 1e-12);
});

test('long-run AUCtau approaches administered active amount times F over CL', () => {
  const drug = syntheticDrug();
  const result = new OneCompartmentModel(drug)
    .simulateMultiDose(1, 12, 100, 1200, 0.05);
  const expected = 1000 * drug.activeMoietyFraction * drug.F / drug.CL;
  assert.ok(Math.abs(result.AUCtau - expected) / expected < 2e-4);
});

test('population Monte Carlo is seeded and keeps measurement error off latent metrics', async () => {
  const simulator = new PKPDSimulator();
  const base = {
    drug: syntheticDrug(),
    dose: 1,
    interval: 12,
    duration: 72,
    dt: 0.5,
    mode: 'population',
    population: {
      clCV: 0.3, vdCV: 0.2, kaCV: 0.4, fCV: 0.15,
      clVdCorrelation: 0.4
    },
    seed: 730_2026
  };
  const first = await simulator.monteCarloSimulation(base, 24);
  const repeated = await simulator.monteCarloSimulation(base, 24);
  const changed = await simulator.monteCarloSimulation({ ...base, seed: base.seed + 1 }, 24);
  const observed = await simulator.monteCarloSimulation({
    ...base,
    observationError: { model: 'combined', proportionalCV: 0.2, additiveSD: 0.1 }
  }, 24);

  assert.deepEqual(first, repeated);
  assert.notEqual(first[0].sampledParameters.CL, changed[0].sampledParameters.CL);
  assert.ok(first.every(result => result.sampledParameters.F > 0 && result.sampledParameters.F <= 1));
  assert.deepEqual(
    observed.map(({ C, Cmax, Cmin, Tmax, AUCtau }) => ({ C, Cmax, Cmin, Tmax, AUCtau })),
    first.map(({ C, Cmax, Cmin, Tmax, AUCtau }) => ({ C, Cmax, Cmin, Tmax, AUCtau }))
  );
  assert.ok(observed.every(result => result.observedC?.length === result.C.length));
  assert.ok(observed.some(result => result.observedC.some((value, i) => value !== result.C[i])));
});

test('deployed page exposes only the audited route/uncertainty surface', () => {
  const page = readFileSync(pagePath, 'utf8');
  assert.match(page, /PKPD\.DRUG_DB/);
  assert.match(page, /data-model="route-one-compartment"/);
  assert.match(page, /data-model="population"/);
  assert.match(page, /data-model="deterministic"/);
  assert.doesNotMatch(
    page,
    /Störmer|Stormer|Yoshida|Forest-Ruth|Stratonovich|Milstein|GARCH|Jump-Diff|SDE\+Itô|class OneCompartment\s*\{/
  );

  const redirect = readFileSync(redirectPath, 'utf8');
  assert.match(redirect, /url=pkpd-simulator-v3\.html/);
});
