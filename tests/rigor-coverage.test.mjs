import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const coverage = readFileSync(
  new URL('../ar-kotlin/docs/BICALUTAMIDE_QUESTION_COVERAGE.md', import.meta.url),
  'utf8'
);
const matrix = readFileSync(
  new URL('../ar-kotlin/docs/RIGOR_MATRIX.md', import.meta.url),
  'utf8'
);
const routeAudit = readFileSync(
  new URL('../ar-kotlin/docs/PK_ROUTE_MONTE_CARLO.md', import.meta.url),
  'utf8'
);

const between = (start, end) => {
  const from = coverage.indexOf(start);
  const to = coverage.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, `missing coverage section ${start}`);
  return coverage.slice(from, to);
};

const rowIds = section => [...section.matchAll(/^\|\s*([A-Z]+(?:-[0-9]+|[0-9]+))\s*\|/gm)]
  .map(match => match[1]);

test('coverage ledger contains exactly 50 AR and 107 unique general dispositions', () => {
  const arIds = rowIds(between('## AR 专库（50 条）', '## 通用配体–受体库（107 条）'));
  const generalIds = rowIds(between('## 通用配体–受体库（107 条）', '## 不可打绿的验收门'));

  assert.equal(arIds.length, 50);
  assert.equal(new Set(arIds).size, 50);
  assert.equal(generalIds.length, 107);
  assert.equal(new Set(generalIds).size, 107);
  assert.deepEqual(generalIds.filter(id => /^PK1[1-4]$/.test(id)), [
    'PK11', 'PK12', 'PK13', 'PK14'
  ]);
});

test('new question dispositions map to explicit rigor rows and an equation audit', () => {
  for (const row of [
    'Oral / extravascular PK',
    'IM / SC depot PK',
    'Population Monte Carlo',
    'Solver applicability gate'
  ]) {
    assert.ok(matrix.includes(`| ${row} |`), `rigor matrix is missing ${row}`);
  }
  for (const token of [
    'A_{admin}', 'logistic-normal', 'AUCτ', 'Störmer–Verlet',
    'observation process', 'not a fitted individual model'
  ]) {
    assert.ok(routeAudit.includes(token), `route audit is missing ${token}`);
  }
});
