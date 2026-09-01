import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync('public/pkpd-simulator-v3.html', 'utf8');
const model = readFileSync(
  'ar-kotlin/rigor-core/src/main/kotlin/dev/hrtkaffee/ar/model/BicalutamideModel.kt',
  'utf8',
);
const coverage = readFileSync(
  'ar-kotlin/docs/BICALUTAMIDE_QUESTION_COVERAGE.md',
  'utf8',
);

test('bicalutamide is a direct AR module distinct from upstream 5-alpha-reductase', () => {
  assert.match(html, /id="fiveArModule"[^]*data-cat-module="five-ar"/);
  assert.match(html, /id="bicalutamideModule"[^]*data-cat-module="bicalutamide"/);
  assert.match(html, /同雄激素反事实下的直接信号抑制/);
  assert.match(html, /不是 T\/DHT 浓度/);
});

test('PK and binding anchors remain explicit and tissue transfer remains an input', () => {
  assert.match(model, /LABEL_SINGLE_DOSE_CMAX_UG_ML = 0\.768/);
  assert.match(model, /LABEL_SINGLE_DOSE_TMAX_HOURS = 31\.3/);
  assert.match(model, /UNBOUND_PLASMA_FRACTION = 0\.04/);
  assert.match(model, /R_BICALUTAMIDE_KI_NM = 11\.0/);
  assert.match(model, /val tissueUnboundPartition: Double/);
  assert.match(model, /sameAndrogenCounterfactualActivationFraction/);
});

test('every source-library question has a row-level disposition', () => {
  assert.equal((coverage.match(/^\| AR-\d{3} \|/gm) ?? []).length, 50);
  assert.equal(
    (coverage.match(/^\| (?:M|B|S|ST|MD|QC|PK|BM|PD|T|SP|STATS|FORM|PC|SYS)\d{2} \|/gm) ?? [])
      .length,
    103,
  );
  assert.match(coverage, /不可打绿的验收门/);
});
