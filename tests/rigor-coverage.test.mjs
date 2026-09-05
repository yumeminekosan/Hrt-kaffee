import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
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
const mcmcAudit = readFileSync(
  new URL('../ar-kotlin/docs/MCMC_DIAGNOSTICS.md', import.meta.url),
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

const expectedGeneralIds = [
  'M01', 'M02', 'M03', 'M04', 'M05', 'M06', 'M07', 'M08',
  'B01', 'B02', 'B03', 'B04', 'B05', 'B06', 'B07', 'B08', 'B09',
  'S01', 'S02', 'S03', 'S04', 'S05',
  'ST01', 'ST02', 'ST03', 'ST04', 'ST05', 'ST06',
  'MD01', 'MD02', 'MD03', 'MD04', 'MD05', 'MD06',
  'QC01', 'QC02', 'QC03', 'QC04', 'QC05', 'QC06', 'QC07', 'QC08', 'QC09', 'QC10',
  'PK01', 'PK02', 'PK03', 'PK04', 'PK05', 'PK06', 'PK07', 'PK08', 'PK09', 'PK10',
  'PK11', 'PK12', 'PK13', 'PK14',
  'BM01', 'BM02', 'BM03', 'BM04', 'BM05', 'BM06',
  'PD01', 'PD02', 'PD03', 'PD04', 'PD05', 'PD06', 'PD07', 'PD08',
  'T01', 'T02', 'T03', 'T04', 'T05', 'T06', 'T07', 'T08',
  'SP01', 'SP02', 'SP03', 'SP04', 'SP05',
  'STATS01', 'STATS02', 'STATS03', 'STATS04', 'STATS05', 'STATS06',
  'FORM01', 'FORM02', 'FORM03', 'FORM04', 'FORM05', 'FORM06',
  'PC01', 'PC02', 'PC03', 'PC04', 'PC05', 'PC06',
  'SYS01', 'SYS02', 'SYS03', 'SYS04'
];
const expectedQuestionDigest = '9d72e830950ff40fe06443e9e356eb520f89fa8a0142798c303f1174a7ad4c72';

test('coverage ledger contains exactly 50 AR and 107 unique general dispositions', () => {
  const arIds = rowIds(between('## AR 专库（50 条）', '## 通用配体–受体库（107 条）'));
  const generalSection = between(
    '## 通用配体–受体库（107 条）',
    '## 不可升级为生物验证结论的外部门'
  );
  const generalIds = rowIds(generalSection);
  const generalRows = generalSection.split('\n')
    .filter(line => /^\|\s*[A-Z]+(?:-[0-9]+|[0-9]+)\s*\|/.test(line))
    .map(line => line.split('|').slice(1, -1).map(cell => cell.trim()));

  assert.equal(arIds.length, 50);
  assert.equal(new Set(arIds).size, 50);
  assert.equal(generalIds.length, 107);
  assert.equal(new Set(generalIds).size, 107);
  assert.deepEqual(generalIds, expectedGeneralIds, 'the fixed 107-question manifest changed');
  assert.equal(generalRows.length, 107);
  const questionDigest = createHash('sha256')
    .update(generalRows.map(row => `${row[0]}\t${row[2]}`).join('\n'))
    .digest('hex');
  assert.equal(questionDigest, expectedQuestionDigest, 'a base question was replaced or edited');
  const dispositionEvidence = {
    VERIFIED: /实现|已|记录|控制|处置|归一化输出|分层实现|诊断门实现/,
    BOUNDED: /部分|边界|有界|降阶|条件|参数化|范围/,
    GUARDED: /未|无|不|禁止|不可|不能|开放|缺口|警告|仍/,
    EXCLUDED: /范围外|不适用|不强加|不需要|明确否定|移除/
  };
  for (const row of generalRows) {
    assert.equal(row.length, 5, `${row[0]} must have a dedicated CI-status cell`);
    assert.match(
      row[4],
      /^✅ GREEN · (VERIFIED|BOUNDED|GUARDED|EXCLUDED)$/,
      `${row[0]} is not GREEN`
    );
    const dispositionClass = row[4].split(' · ')[1];
    assert.match(
      row[3],
      dispositionEvidence[dispositionClass],
      `${row[0]} GREEN class is not supported by its disposition`
    );
  }
});

test('new question dispositions map to explicit rigor rows and an equation audit', () => {
  for (const row of [
    'Oral / extravascular PK',
    'IM / SC depot PK',
    'Population Monte Carlo',
    'Bayesian individual-PK explorer',
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
  for (const token of [
    'rank-normalized split', 'bulk ESS', 'tail ESS', 'autocorrelation',
    'posterior predictive', 'Apply exploratory inputs', 'cannot produce an individual dose recommendation'
  ]) {
    assert.ok(mcmcAudit.includes(token), `MCMC audit is missing ${token}`);
  }
});
