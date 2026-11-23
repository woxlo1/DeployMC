'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');

const { AREAS, BUDGETS, UsageError, run } = require('../lib.cjs');

const GZIP_MAGIC = Buffer.from([0x1f, 0x8b]);
const JAR_MAGIC = Buffer.from([0x50, 0x4b, 0x03, 0x04]);

function makeRoot() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'deploymc-quality-'));
}

function writeArtifact(root, relativePath, size, magic) {
  const target = path.join(root, relativePath);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  const descriptor = fs.openSync(target, 'w');
  try {
    if (magic) fs.writeSync(descriptor, magic, 0, magic.length, 0);
    fs.ftruncateSync(descriptor, size);
  } finally {
    fs.closeSync(descriptor);
  }
  return target;
}

function writeWebArtifact(root, size = 1000, name = 'index-test.js.gz', magic = GZIP_MAGIC) {
  return writeArtifact(root, path.join('web', 'dist', 'assets', name), size, magic);
}

function writeJarArtifact(root, size = 1000, magic = JAR_MAGIC) {
  return writeArtifact(root, 'plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar', size, magic);
}

function areaRow(report, areaName) {
  const row = report.split('\n').find((line) => line.startsWith(`| ${areaName} |`));
  assert.ok(row, `missing row for ${areaName}`);
  return row;
}

test('reports every PART 41 area and never marks an unmeasured area as passing', () => {
  const root = makeRoot();
  const result = run([], { root, cwd: root });

  assert.equal(result.exitCode, 1);
  assert.equal(result.budgetResults.length, BUDGETS.length);
  for (const area of AREAS) {
    assert.ok(result.report.includes(`| ${area.name} |`), `missing area: ${area.name}`);
  }
  for (const area of AREAS.filter((item) => item.id !== 'bundle-sizes')) {
    assert.match(areaRow(result.report, area.name), /\| NOT MEASURED \|/);
  }
  assert.match(areaRow(result.report, 'Bundle sizes'), /\| NOT MEASURED \|/);
  assert.match(result.report, /missing artifact: web\/dist\/assets\/\*\.js\.gz/);
  assert.match(
    result.report,
    /missing artifact: plugin\/build\/libs\/DeployMC-0\.1\.0-SNAPSHOT\.jar/,
  );
  assert.match(result.report, /- Unmeasured PART 41 areas: 12/);
});

test('enforces the two derived budgets from the recorded local artifacts', () => {
  const root = makeRoot();
  writeWebArtifact(root, BUDGETS[0].baselineBytes);
  writeJarArtifact(root, BUDGETS[1].baselineBytes);

  const result = run([], { root, cwd: root });

  assert.equal(result.exitCode, 0);
  assert.match(areaRow(result.report, 'Bundle sizes'), /\| PASS \|/);
  assert.match(result.report, /\| 50632 bytes \| <= 60000 bytes \| PASS \| within budget \|/);
  assert.match(result.report, /\| 4721584 bytes \| <= 5500000 bytes \| PASS \| within budget \|/);
  assert.match(result.report, /- Unmeasured PART 41 areas: 11/);
  assert.equal(run([], { root, cwd: root }).report, result.report);
});

test('fails closed when a measured artifact exceeds its budget', () => {
  const root = makeRoot();
  writeWebArtifact(root, BUDGETS[0].budgetBytes + 1);
  writeJarArtifact(root, BUDGETS[1].budgetBytes + 1);

  const result = run([], { root, cwd: root });

  assert.equal(result.exitCode, 1);
  assert.match(areaRow(result.report, 'Bundle sizes'), /\| FAIL \|/);
  assert.match(result.report, /\| 60001 bytes \| <= 60000 bytes \| FAIL \| over budget by 1 bytes \|/);
  assert.match(
    result.report,
    /\| 5500001 bytes \| <= 5500000 bytes \| FAIL \| over budget by 1 bytes \|/,
  );
});

test('fails closed for ambiguous or invalid artifact files', () => {
  const ambiguousRoot = makeRoot();
  writeWebArtifact(ambiguousRoot, 1000, 'index-a.js.gz');
  writeWebArtifact(ambiguousRoot, 1000, 'index-b.js.gz');
  writeJarArtifact(ambiguousRoot);

  const ambiguous = run([], { root: ambiguousRoot, cwd: ambiguousRoot });
  assert.equal(ambiguous.exitCode, 1);
  assert.match(areaRow(ambiguous.report, 'Bundle sizes'), /\| NOT MEASURED \|/);
  assert.match(ambiguous.report, /ambiguous artifact: expected exactly one .* found 2/);

  const invalidRoot = makeRoot();
  writeWebArtifact(invalidRoot, 1000, 'index-test.js.gz', Buffer.from([0x00, 0x00]));
  writeJarArtifact(invalidRoot);

  const invalid = run([], { root: invalidRoot, cwd: invalidRoot });
  assert.equal(invalid.exitCode, 1);
  assert.match(invalid.report, /invalid gzip signature: web\/dist\/assets\/index-test\.js\.gz/);
});

test('--report writes the generated report and never reads measurements from it', () => {
  const root = makeRoot();
  writeWebArtifact(root);
  writeJarArtifact(root);
  const reportPath = path.join(root, 'reports', 'quality.md');
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, 'fake measurement: 1 bytes\n');

  const result = run(['--report', 'reports/quality.md'], { root, cwd: root });

  assert.equal(result.exitCode, 0);
  assert.equal(fs.readFileSync(reportPath, 'utf8'), result.report);
  assert.ok(!fs.readFileSync(reportPath, 'utf8').includes('fake measurement'));
  assert.throws(() => run(['--measured-bytes', '1'], { root, cwd: root }), UsageError);
  assert.throws(() => run(['--report'], { root, cwd: root }), /requires a path/);
  assert.throws(
    () => run(['--report', 'a.md', '--report', 'b.md'], { root, cwd: root }),
    /only once/,
  );
});
