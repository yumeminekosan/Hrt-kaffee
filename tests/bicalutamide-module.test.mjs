import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync('public/pkpd-simulator-v3.html', 'utf8');
const model = readFileSync(
  'ar-kotlin/rigor-core/src/main/kotlin/dev/hrtkaffee/ar/model/BicalutamideModel.kt',
  'utf8',
);
const bridge = readFileSync(
  'ar-kotlin/rigor-core/src/main/kotlin/dev/hrtkaffee/ar/model/BicalutamideEstradiolBridge.kt',
  'utf8',
);
const embeddedBridge = readFileSync(
  'ar-kotlin/embeddedEngine/src/commonMain/kotlin/dev/hrtkaffee/ar/embedded/EmbeddedBicalutamideEstradiolBridge.kt',
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

test('estradiol and bicalutamide modules are linked through a residual-androgen evidence gate', () => {
  assert.match(html, /id="bicBridgeMode"/);
  assert.match(html, /id="bicLinkedE2Average"/);
  assert.match(html, /id="bicCentralMinimumDose"/);
  assert.match(html, /id="bicConservativeMinimumDose"/);
  assert.match(html, /href="#transdermalEstradiolModule"/);
  assert.match(html, /href="#bicalutamideModule"/);
  assert.match(html, /E2 暴露本身不能识别个人残余雄激素/);
  assert.match(html, /普通血清总 T\/DHT 不能直接当作组织等效游离浓度/);
  assert.match(html, /bvae108/);
  assert.match(html, /20420188241305022/);
});

test('minimum-dose inversion is gated, grid-based, and mirrored for browser parity', () => {
  for (const source of [bridge, embeddedBridge]) {
    assert.match(source, /ESTRADIOL_EXPOSURE_ONLY/);
    assert.match(source, /LAB_ANCHORED_TISSUE_EQUIVALENTS/);
    assert.match(source, /DISPLAY_DOSES_MG = listOf\(0\.0, 5\.0, 10\.0, 25\.0, 50\.0\)/);
    assert.match(source, /不能识别个人 AR 信号或最低比卡鲁胺剂量/);
    assert.match(source, /conservativeMinimumEquivalentDoseMg/);
  }
  assert.match(html, /不等于 HRT 充分性、处方建议或安全剂量/);
});

test('every source-library question has a row-level disposition', () => {
  assert.equal((coverage.match(/^\| AR-\d{3} \|/gm) ?? []).length, 50);
  assert.equal(
    (coverage.match(/^\| (?:M|B|S|ST|MD|QC|PK|BM|PD|T|SP|STATS|FORM|PC|SYS)\d{2} \|/gm) ?? [])
      .length,
    107,
  );
  assert.match(coverage, /不可升级为生物验证结论的外部门/);
});
