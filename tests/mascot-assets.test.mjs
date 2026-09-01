import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const html = readFileSync(join(root, 'public/pkpd-simulator-v3.html'), 'utf8');
const controller = readFileSync(join(root, 'public/mascots/kaffee-cats.js'), 'utf8');

const modules = [
  'presets',
  'five-ar',
  'bicalutamide',
  'transdermal-e2',
  'drug-settings',
  'model-selection',
  'bayesian',
  'progestogen',
  'interactions',
  'monte-carlo',
  'pk-parameters',
  'simulation-controls',
  'results',
  'stats-summary',
  'time-curves',
  'event-log',
  'research-footer',
];

test('every simulator module has one mascot host and controller entry', () => {
  const hosts = [...html.matchAll(/data-cat-module="([^"]+)"/g)].map((match) => match[1]);
  assert.deepEqual(hosts, modules);
  for (const moduleName of modules) {
    assert.match(controller, new RegExp(`(?:['"]${moduleName}['"]|\\b${moduleName}):`));
  }
});

test('the page loads the mascot controller and stylesheet', () => {
  assert.match(html, /href="mascots\/kaffee-cats\.css"/);
  assert.match(html, /src="mascots\/kaffee-cats\.js"/);
});

test('every cat has the three web animation layers and a layered PSD source', () => {
  for (const moduleName of modules) {
    for (const layer of ['body', 'head', 'tail']) {
      assert.equal(
        existsSync(join(root, 'public/mascots/cats', moduleName, `${layer}.png`)),
        true,
        `${moduleName}/${layer}.png is missing`,
      );
    }
    assert.equal(
      existsSync(join(root, 'design/mascots/psd', `${moduleName}.psd`)),
      true,
      `${moduleName}.psd is missing`,
    );
  }
});
