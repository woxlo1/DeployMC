'use strict';

const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_ROOT = path.resolve(__dirname, '..', '..');
const WEB_GZIP_PATTERN = 'web/dist/assets/*.js.gz';
const PLUGIN_JAR_PATH = 'plugin/build/libs/DeployMC-0.1.0-SNAPSHOT.jar';
const EVIDENCE_URL = 'https://github.com/woxlo1/DeployMC/pull/210';
const EVIDENCE_ACCESSED = '2026-11-23';
const SPIKE_PATH = 'docs/research/spikes/06-embedded-ui-size.md';

class UsageError extends Error {}

const BUDGETS = Object.freeze([
  Object.freeze({
    key: 'webJsGzip',
    label: 'Web main JS gzip',
    budgetBytes: 60000,
    baselineBytes: 50632,
    expectedPath: WEB_GZIP_PATTERN,
    kind: 'gzip',
  }),
  Object.freeze({
    key: 'pluginJar',
    label: 'Plugin JAR',
    budgetBytes: 5500000,
    baselineBytes: 4721584,
    expectedPath: PLUGIN_JAR_PATH,
    kind: 'jar',
  }),
]);

const AREAS = Object.freeze([
  Object.freeze({
    id: 'core-coverage',
    name: 'Core logic coverage (sandbox, RBAC, auth, deploy state machine, snapshot)',
    gate: 'Line >= 90%, branch >= 85%, mutation testing where feasible',
    evidence: 'No coverage or mutation report is read.',
  }),
  Object.freeze({
    id: 'java-coverage',
    name: 'Other Java modules',
    gate: 'Line >= 80%',
    evidence: 'No coverage report is read.',
  }),
  Object.freeze({
    id: 'web-ui',
    name: 'Web UI',
    gate: 'Unit >= 70%, all core workflows covered by E2E',
    evidence: 'No coverage or E2E result is read.',
  }),
  Object.freeze({
    id: 'cli-sdks',
    name: 'CLI / SDKs',
    gate: 'Line >= 80% plus conformance suite',
    evidence: 'No coverage or conformance result is read.',
  }),
  Object.freeze({
    id: 'static-analysis',
    name: 'Static analysis',
    gate: 'Zero new high-severity findings',
    evidence: 'No finding baseline is read.',
  }),
  Object.freeze({
    id: 'bundle-sizes',
    name: 'Bundle sizes',
    gate: 'Web main JS gzip <= 60000 bytes; plugin JAR <= 5500000 bytes',
    evidence: '',
  }),
  Object.freeze({
    id: 'startup',
    name: 'Startup overhead',
    gate: 'Enable time under 1 second excluding optional network checks',
    evidence: 'No server timing is read.',
  }),
  Object.freeze({
    id: 'idle',
    name: 'Idle overhead',
    gate: 'CPU and memory budget on a reference server profile',
    evidence: 'No reference profile measurement is read.',
  }),
  Object.freeze({
    id: 'api-stability',
    name: 'API stability',
    gate: 'No breaking changes to released API versions',
    evidence: 'No breaking-change result is read.',
  }),
  Object.freeze({
    id: 'docs',
    name: 'Docs',
    gate: 'Zero broken links; public config keys, permissions, and error codes documented',
    evidence: 'No link or key check result is read.',
  }),
  Object.freeze({
    id: 'i18n',
    name: 'i18n',
    gate: 'Zero structural errors; stale/missing tracked and reported',
    evidence: 'No catalog check result is read.',
  }),
  Object.freeze({
    id: 'flakiness',
    name: 'Flakiness',
    gate: 'Retry-free green rate >= 98% over rolling 30 runs',
    evidence: 'No run history is read.',
  }),
]);

function parseArgs(argv) {
  let reportPath;
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--help' || arg === '-h') return { help: true };
    if (arg === '--report') {
      if (reportPath !== undefined) throw new UsageError('--report may be used only once');
      const value = argv[index + 1];
      if (value === undefined || value.startsWith('--')) {
        throw new UsageError('--report requires a path');
      }
      reportPath = value;
      index += 1;
      continue;
    }
    throw new UsageError(`unknown argument: ${arg}`);
  }
  return { help: false, reportPath };
}

function helpText() {
  return [
    'Usage: node tools/quality/check.cjs [--report <path>]',
    '',
    'Prints the PART 41 quality report from existing Web and plugin artifacts.',
    'No builds, tests, network calls, or CI checks are run.',
    '--report <path> writes the generated report to the given path.',
    'Exit codes: 0 measured budgets within limits; 1 missing or over-budget artifact;',
    '2 invalid command line or report write error.',
    '',
  ].join('\n');
}

function displayPath(root, absolutePath) {
  return path.relative(root, absolutePath).split(path.sep).join('/');
}

function readSignature(filePath, length) {
  let descriptor;
  try {
    descriptor = fs.openSync(filePath, 'r');
    const buffer = Buffer.alloc(length);
    const bytesRead = fs.readSync(descriptor, buffer, 0, length, 0);
    return buffer.subarray(0, bytesRead);
  } finally {
    if (descriptor !== undefined) fs.closeSync(descriptor);
  }
}

function notMeasured(detail) {
  return { artifactPath: null, measuredBytes: null, status: 'NOT MEASURED', detail };
}

function measured(artifactPath, measuredBytes, budget) {
  const withinBudget = measuredBytes <= budget.budgetBytes;
  return {
    artifactPath,
    measuredBytes,
    status: withinBudget ? 'PASS' : 'FAIL',
    detail: withinBudget
      ? 'within budget'
      : `over budget by ${measuredBytes - budget.budgetBytes} bytes`,
  };
}

function checkGzipArtifact(root, budget) {
  const assetDir = path.join(root, 'web', 'dist', 'assets');
  let entries;
  try {
    entries = fs.readdirSync(assetDir, { withFileTypes: true });
  } catch (error) {
    return notMeasured(`missing artifact: ${budget.expectedPath}`);
  }

  const names = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith('.js.gz'))
    .map((entry) => entry.name)
    .sort();
  if (names.length === 0) return notMeasured(`missing artifact: ${budget.expectedPath}`);
  if (names.length > 1) {
    return notMeasured(
      `ambiguous artifact: expected exactly one ${budget.expectedPath}; found ${names.length}`,
    );
  }

  const candidate = path.join(assetDir, names[0]);
  const candidatePath = displayPath(root, candidate);
  try {
    const signature = readSignature(candidate, 2);
    if (signature.length < 2 || signature[0] !== 0x1f || signature[1] !== 0x8b) {
      return notMeasured(`invalid gzip signature: ${candidatePath}`);
    }
    const stats = fs.statSync(candidate);
    return measured(candidatePath, stats.size, budget);
  } catch (error) {
    return notMeasured(`unreadable artifact (${error.code || 'error'}): ${candidatePath}`);
  }
}

function checkJarArtifact(root, budget) {
  const jarPath = path.join(root, 'plugin', 'build', 'libs', 'DeployMC-0.1.0-SNAPSHOT.jar');
  let stats;
  try {
    stats = fs.statSync(jarPath);
    if (!stats.isFile()) return notMeasured(`missing artifact: ${budget.expectedPath}`);
  } catch (error) {
    return notMeasured(`missing artifact: ${budget.expectedPath}`);
  }

  const display = displayPath(root, jarPath);
  try {
    const signature = readSignature(jarPath, 4);
    const expected = Buffer.from([0x50, 0x4b, 0x03, 0x04]);
    if (!signature.equals(expected)) return notMeasured(`invalid jar signature: ${display}`);
    return measured(display, stats.size, budget);
  } catch (error) {
    return notMeasured(`unreadable artifact (${error.code || 'error'}): ${display}`);
  }
}

function inspectBudgets(root) {
  return BUDGETS.map((budget) => {
    const measurement =
      budget.kind === 'gzip' ? checkGzipArtifact(root, budget) : checkJarArtifact(root, budget);
    return { ...budget, ...measurement };
  });
}

function bundleStatus(results) {
  if (results.some((result) => result.status === 'FAIL')) return 'FAIL';
  if (results.every((result) => result.status === 'PASS')) return 'PASS';
  return 'NOT MEASURED';
}

function buildReport(results) {
  const status = bundleStatus(results);
  const bundleDetail = results.map((result) => result.detail).join('; ');
  const lines = [
    '# PART 41 quality report',
    '',
    'Generated from existing local artifacts only. No builds, tests, network calls, or CI checks',
    'are run. Areas without a local measurement are always `NOT MEASURED`.',
    '',
    '## Area status',
    '',
    '| Area | Gate | Status | Evidence |',
    '|---|---|---|---|',
  ];

  for (const area of AREAS) {
    const areaStatus = area.id === 'bundle-sizes' ? status : 'NOT MEASURED';
    const detail = area.id === 'bundle-sizes' ? bundleDetail : area.evidence;
    lines.push(`| ${area.name} | ${area.gate} | ${areaStatus} | ${detail} |`);
  }

  lines.push(
    '',
    '## Budget results',
    '',
    '| Artifact | Artifact path | Measured | Budget | Result | Detail |',
    '|---|---|---:|---:|---|---|',
  );
  for (const result of results) {
    const artifactPath = result.artifactPath || result.expectedPath;
    const measuredText =
      result.measuredBytes === null ? 'NOT MEASURED' : `${result.measuredBytes} bytes`;
    lines.push(
      `| ${result.label} | \`${artifactPath}\` | ${measuredText} | ` +
        `<= ${result.budgetBytes} bytes | ${result.status} | ${result.detail} |`,
    );
  }

  const measuredCount = results.filter((result) => result.status !== 'NOT MEASURED').length;
  const failedCount = results.filter((result) => result.status === 'FAIL').length;
  const unmeasuredAreas =
    AREAS.length - 1 + (results.some((result) => result.status === 'NOT MEASURED') ? 1 : 0);
  lines.push(
    '',
    '## Budget evidence',
    '',
    'The two enforced budgets are derived from local measurements recorded for #118: Web main JS',
    `gzip baseline ${BUDGETS[0].baselineBytes} bytes and plugin JAR baseline ` +
      `${BUDGETS[1].baselineBytes} bytes. These are local baselines, not support or`,
    `performance claims. Evidence: [PR #210](${EVIDENCE_URL}), accessed ${EVIDENCE_ACCESSED}.`,
    `The full spike at \`${SPIKE_PATH}\` will be available when PR #210 merges.`,
    '',
    '## Summary',
    '',
    `- Enforced budgets: ${BUDGETS.length}`,
    `- Measured budgets: ${measuredCount}`,
    `- Failed budgets: ${failedCount}`,
    `- Unmeasured PART 41 areas: ${unmeasuredAreas}`,
    '',
  );
  return lines.join('\n');
}

function run(argv, options = {}) {
  const args = parseArgs(argv);
  if (args.help) return { help: true, report: helpText(), exitCode: 0, budgetResults: [] };

  const root = options.root ? path.resolve(options.root) : DEFAULT_ROOT;
  const cwd = options.cwd ? path.resolve(options.cwd) : process.cwd();
  const budgetResults = inspectBudgets(root);
  const report = buildReport(budgetResults);

  if (args.reportPath !== undefined) {
    const target = path.resolve(cwd, args.reportPath);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, report, 'utf8');
  }

  return {
    help: false,
    report,
    exitCode: budgetResults.every((result) => result.status === 'PASS') ? 0 : 1,
    budgetResults,
  };
}

module.exports = { AREAS, BUDGETS, UsageError, buildReport, helpText, inspectBudgets, run };
