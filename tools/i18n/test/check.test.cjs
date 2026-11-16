'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');

const {
  IcuError,
  check,
  compareCatalogs,
  findUnusedKeys,
  parseIcu,
  validateCatalog,
} = require('../lib.cjs');

test('parses simple and plural messages', () => {
  assert.deepEqual(parseIcu('Hello {name}'), { arguments: ['name'], categories: {} });

  const parsed = parseIcu('{count, plural, one {# plugin} other {# plugins}}');
  assert.deepEqual(parsed.arguments, ['count']);
  assert.deepEqual(parsed.categories, { count: ['one', 'other'] });
});

test('parses placeholders nested inside plural branches', () => {
  const parsed = parseIcu('{count, plural, one {# plugin for {name}} other {# plugins}}');

  assert.deepEqual(parsed.arguments, ['count', 'name']);
});

test('rejects unbalanced braces and missing other branches', () => {
  assert.throws(() => parseIcu('Hello {name'), IcuError);
  assert.throws(() => parseIcu('Hello }'), IcuError);
  assert.throws(() => parseIcu('{count, plural, one {# plugin}}'), IcuError);
});

test('detects placeholder mismatches', () => {
  const source = validateCatalog('en', { greeting: 'Hello {name}' });
  const target = validateCatalog('ja', { greeting: 'こんにちは {nome}' });
  const result = compareCatalogs('ja', source, target);

  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0].message, /missing \{name\}/);
  assert.match(result.errors[0].message, /unknown \{nome\}/);
});

test('reports missing translations as warnings', () => {
  const source = validateCatalog('en', { greeting: 'Hello' });
  const target = validateCatalog('ja', {});
  const result = compareCatalogs('ja', source, target);

  assert.equal(result.errors.length, 0);
  assert.equal(result.warnings.length, 1);
  assert.match(result.warnings[0].message, /missing translation/);
});

test('reports unused keys when source does not reference them', () => {
  assert.deepEqual(findUnusedKeys(['app.title', 'app.unused'], "t('app.title')"), ['app.unused']);
});

test('checks catalogs end to end and writes the status table', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'deploymc-i18n-'));
  const catalogDir = path.join(root, 'web', 'src', 'locales');
  const sourceDir = path.join(root, 'web', 'src');
  fs.mkdirSync(catalogDir, { recursive: true });

  const meta = {
    reviewStatus: 'source',
    reviewedBy: 'woxlo1',
    reviewedAt: '2026-11-16',
  };
  fs.writeFileSync(
    path.join(catalogDir, 'en.json'),
    JSON.stringify({
      '@meta': { ...meta, locale: 'en' },
      'app.title': 'DeployMC',
      'app.count': '{count, plural, one {# plugin} other {# plugins}}',
    }),
  );
  fs.writeFileSync(
    path.join(catalogDir, 'ja.json'),
    JSON.stringify({
      '@meta': { ...meta, locale: 'ja', reviewStatus: 'machine-translated-draft' },
      'app.title': 'DeployMC',
      'app.count': '{count, plural, other {# 個のプラグイン}}',
    }),
  );
  fs.writeFileSync(path.join(sourceDir, 'App.tsx'), "t('app.title'); t('app.count');");

  const passing = check(root, { writeStatus: true });
  assert.equal(passing.errors.length, 0);
  assert.equal(passing.warnings.length, 0);
  assert.match(passing.statusMarkdown, /\| ja \| 2 \| 0 \| 0 \| machine-translated-draft \|/);
  assert.ok(fs.existsSync(path.join(root, 'docs', 'i18n', 'STATUS.md')));

  fs.writeFileSync(
    path.join(catalogDir, 'ja.json'),
    JSON.stringify({
      '@meta': { ...meta, locale: 'ja' },
      'app.title': '{nombre}',
      'app.count': '{count, plural, other {# 個のプラグイン}}',
    }),
  );

  const failing = check(root);
  assert.ok(failing.errors.some((error) => /placeholder mismatch/.test(error.message)));
});
