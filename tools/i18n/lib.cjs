'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

class IcuError extends Error {}

/**
 * Parses the subset of ICU MessageFormat used by DeployMC catalogs and returns the referenced
 * arguments plus plural/select categories. Unsupported complex nested formats are reported as
 * errors rather than silently accepted.
 */
function parseIcu(message) {
  const args = new Set();
  const categoriesByArgument = new Map();
  let index = 0;

  function skipWhitespace() {
    while (index < message.length && /\s/.test(message[index])) {
      index += 1;
    }
  }

  function readName() {
    const start = index;
    while (index < message.length && /[A-Za-z0-9_]/.test(message[index])) {
      index += 1;
    }
    if (start === index) {
      throw new IcuError(`expected an argument name at offset ${start}`);
    }
    return message.slice(start, index);
  }

  function skipQuoted() {
    index += 1;
    while (index < message.length) {
      if (message[index] === "'") {
        if (message[index + 1] === "'") {
          index += 2;
          continue;
        }
        index += 1;
        return;
      }
      index += 1;
    }
    // An unmatched apostrophe is treated as a literal by ICU; accept it.
  }

  function parseArgument() {
    skipWhitespace();
    const name = readName();
    args.add(name);
    skipWhitespace();

    if (message[index] === '}') {
      index += 1;
      return;
    }
    if (message[index] !== ',') {
      throw new IcuError(`expected ',' or '}' after argument "${name}" at offset ${index}`);
    }

    index += 1;
    skipWhitespace();
    const typeStart = index;
    while (index < message.length && /[A-Za-z]/.test(message[index])) {
      index += 1;
    }
    const type = message.slice(typeStart, index);
    if (!type) {
      throw new IcuError(`missing argument type for "${name}"`);
    }

    if (type === 'plural' || type === 'select' || type === 'selectordinal') {
      const categories = [];
      skipWhitespace();
      if (message.startsWith('offset:', index)) {
        index += 'offset:'.length;
        while (index < message.length && message[index] !== ',' && message[index] !== '}') {
          index += 1;
        }
      }

      for (;;) {
        skipWhitespace();
        if (message[index] === ',') {
          index += 1;
          continue;
        }
        if (message[index] === '}') {
          index += 1;
          break;
        }
        if (index >= message.length) {
          throw new IcuError(`unclosed ${type} argument "${name}"`);
        }

        const selectorStart = index;
        while (index < message.length && /[A-Za-z0-9_=.-]/.test(message[index])) {
          index += 1;
        }
        const selector = message.slice(selectorStart, index);
        if (!selector) {
          throw new IcuError(`expected a selector for "${name}" at offset ${index}`);
        }
        skipWhitespace();
        if (message[index] !== '{') {
          throw new IcuError(`expected '{' after selector "${selector}"`);
        }
        index += 1;
        readMessage(true);
        index += 1;
        categories.push(selector);
      }

      if (!categories.includes('other')) {
        throw new IcuError(`${type} argument "${name}" must include an "other" branch`);
      }
      categoriesByArgument.set(name, categories);
      return;
    }

    while (index < message.length && message[index] !== '}') {
      index += 1;
    }
    if (message[index] !== '}') {
      throw new IcuError(`unclosed argument "${name}"`);
    }
    index += 1;
  }

  function readMessage(stopAtBrace) {
    while (index < message.length) {
      const character = message[index];
      if (character === "'") {
        skipQuoted();
        continue;
      }
      if (character === '{') {
        index += 1;
        parseArgument();
        continue;
      }
      if (character === '}') {
        if (stopAtBrace) {
          return;
        }
        throw new IcuError(`unexpected '}' at offset ${index}`);
      }
      index += 1;
    }
    if (stopAtBrace) {
      throw new IcuError('unclosed argument');
    }
  }

  readMessage(false);

  return {
    arguments: [...args].sort(),
    categories: Object.fromEntries(categoriesByArgument),
  };
}

function readCatalog(file) {
  const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error(`${file} must contain a JSON object`);
  }
  return raw;
}

function messageEntries(catalog) {
  return Object.entries(catalog).filter(([key]) => !key.startsWith('@'));
}

function validateCatalog(locale, catalog) {
  const errors = [];
  const parsed = new Map();

  for (const [key, value] of messageEntries(catalog)) {
    if (typeof value !== 'string') {
      errors.push({ locale, key, message: 'message value must be a string' });
      continue;
    }
    try {
      parsed.set(key, parseIcu(value));
    } catch (error) {
      errors.push({
        locale,
        key,
        message: error instanceof IcuError ? error.message : String(error),
      });
    }
  }

  return { parsed, errors };
}

function compareCatalogs(locale, source, target) {
  const errors = [];
  const warnings = [];

  for (const [key, sourceMessage] of source.parsed) {
    const targetMessage = target.parsed.get(key);
    if (!targetMessage) {
      warnings.push({ locale, key, message: 'missing translation; English fallback applies' });
      continue;
    }

    const sourceArguments = new Set(sourceMessage.arguments);
    const targetArguments = new Set(targetMessage.arguments);
    const missing = [...sourceArguments].filter((argument) => !targetArguments.has(argument));
    const extra = [...targetArguments].filter((argument) => !sourceArguments.has(argument));

    if (missing.length > 0 || extra.length > 0) {
      const details = [];
      if (missing.length > 0) {
        details.push(`missing ${missing.map((name) => `{${name}}`).join(', ')}`);
      }
      if (extra.length > 0) {
        details.push(`unknown ${extra.map((name) => `{${name}}`).join(', ')}`);
      }
      errors.push({ locale, key, message: `placeholder mismatch: ${details.join('; ')}` });
    }
  }

  for (const key of target.parsed.keys()) {
    if (!source.parsed.has(key)) {
      warnings.push({ locale, key, message: 'key does not exist in the English source' });
    }
  }

  return { errors, warnings };
}

function collectSourceText(directory) {
  let text = '';
  if (!fs.existsSync(directory)) {
    return text;
  }

  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'locales' || entry.name === 'node_modules') {
        continue;
      }
      text += collectSourceText(fullPath);
    } else if (/\.(ts|tsx|js|jsx)$/.test(entry.name)) {
      text += fs.readFileSync(fullPath, 'utf8');
    }
  }

  return text;
}

function findUnusedKeys(keys, sourceText) {
  return keys.filter(
    (key) =>
      !sourceText.includes(`'${key}'`) &&
      !sourceText.includes(`"${key}"`) &&
      !sourceText.includes(`\`${key}\``),
  );
}

function gitCommitTime(root, relativePath) {
  try {
    const output = execFileSync('git', ['log', '-1', '--format=%ct', '--', relativePath], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
    return output ? Number(output) : null;
  } catch {
    return null;
  }
}

function metaFor(catalog, fallbackLocale) {
  const meta = catalog['@meta'];
  if (meta === null || typeof meta !== 'object' || Array.isArray(meta)) {
    return { locale: fallbackLocale, reviewStatus: 'unknown', reviewedBy: null, reviewedAt: null };
  }
  return {
    locale: typeof meta.locale === 'string' ? meta.locale : fallbackLocale,
    reviewStatus: typeof meta.reviewStatus === 'string' ? meta.reviewStatus : 'unknown',
    reviewedBy: typeof meta.reviewedBy === 'string' ? meta.reviewedBy : null,
    reviewedAt: typeof meta.reviewedAt === 'string' ? meta.reviewedAt : null,
  };
}

function buildStatusMarkdown(rows) {
  const lines = [
    '<!-- Generated by tools/i18n/check --write-status. Do not edit by hand. -->',
    '# Translation Status',
    '',
    'Source catalog: `web/src/locales/en.json`',
    '',
    '| Locale | Keys | Missing | Structural errors | Review status | Reviewed by | Reviewed at |',
    '| --- | ---: | ---: | ---: | --- | --- | --- |',
  ];

  for (const row of rows) {
    lines.push(
      `| ${row.locale} | ${row.keys} | ${row.missing} | ${row.errors} | ${row.reviewStatus} | ${
        row.reviewedBy ?? '-'
      } | ${row.reviewedAt ?? '-'} |`,
    );
  }

  lines.push(
    '',
    'Missing or stale translations produce warnings and fall back to English at runtime. Placeholder',
    'mismatches, invalid ICU syntax, and plural messages without an `other` branch are structural',
    'errors and fail CI.',
    '',
  );

  return lines.join('\n');
}

function check(root, options = {}) {
  const catalogDir = path.join(root, 'web', 'src', 'locales');
  const errors = [];
  const warnings = [];
  const rows = [];

  if (!fs.existsSync(catalogDir)) {
    return {
      errors: [{ locale: 'en', key: '-', message: `missing catalog directory ${catalogDir}` }],
      warnings,
      statusMarkdown: buildStatusMarkdown(rows),
    };
  }

  const files = fs
    .readdirSync(catalogDir)
    .filter((file) => file.endsWith('.json'))
    .sort();
  const sourceFile = 'en.json';
  if (!files.includes(sourceFile)) {
    return {
      errors: [{ locale: 'en', key: '-', message: 'missing canonical en.json catalog' }],
      warnings,
      statusMarkdown: buildStatusMarkdown(rows),
    };
  }

  const sourceCatalog = readCatalog(path.join(catalogDir, sourceFile));
  const source = validateCatalog('en', sourceCatalog);
  errors.push(...source.errors);

  const sourceKeys = [...source.parsed.keys()];
  const sourceText = collectSourceText(path.join(root, 'web', 'src'));
  for (const key of findUnusedKeys(sourceKeys, sourceText)) {
    warnings.push({ locale: 'en', key, message: 'key is not referenced by Web UI source files' });
  }

  const sourceTime = gitCommitTime(root, path.join('web', 'src', 'locales', sourceFile));
  rows.push({
    locale: 'en',
    keys: sourceKeys.length,
    missing: 0,
    errors: source.errors.length,
    ...metaFor(sourceCatalog, 'en'),
  });

  for (const file of files) {
    if (file === sourceFile) {
      continue;
    }

    const locale = path.basename(file, '.json');
    const catalog = readCatalog(path.join(catalogDir, file));
    const target = validateCatalog(locale, catalog);
    errors.push(...target.errors);

    const comparison = compareCatalogs(locale, source, target);
    errors.push(...comparison.errors);
    warnings.push(...comparison.warnings);

    if (sourceTime !== null) {
      const targetTime = gitCommitTime(root, path.join('web', 'src', 'locales', file));
      if (targetTime !== null && sourceTime > targetTime) {
        warnings.push({
          locale,
          key: '-',
          message: 'translation is older than the English catalog; review for staleness',
        });
      }
    }

    rows.push({
      locale,
      keys: target.parsed.size,
      missing: comparison.warnings.filter((warning) => warning.message.startsWith('missing')).length,
      errors:
        target.errors.length + comparison.errors.length,
      ...metaFor(catalog, locale),
    });
  }

  if (options.writeStatus) {
    const statusFile = path.join(root, 'docs', 'i18n', 'STATUS.md');
    fs.mkdirSync(path.dirname(statusFile), { recursive: true });
    fs.writeFileSync(statusFile, buildStatusMarkdown(rows), 'utf8');
  }

  return { errors, warnings, statusMarkdown: buildStatusMarkdown(rows) };
}

function run(argv) {
  const root = path.resolve(__dirname, '..', '..');
  const writeStatus = argv.includes('--write-status');
  const strict = argv.includes('--strict');
  const result = check(root, { writeStatus });

  for (const error of result.errors) {
    console.error(`error [${error.locale}] ${error.key}: ${error.message}`);
  }
  for (const warning of result.warnings) {
    console.warn(`warning [${warning.locale}] ${warning.key}: ${warning.message}`);
  }

  const summary = `${result.errors.length} error(s), ${result.warnings.length} warning(s)`;
  if (result.errors.length > 0 || (strict && result.warnings.length > 0)) {
    console.error(`i18n check failed: ${summary}`);
    return 1;
  }
  console.log(`i18n check passed: ${summary}`);
  return 0;
}

module.exports = {
  IcuError,
  buildStatusMarkdown,
  check,
  compareCatalogs,
  findUnusedKeys,
  parseIcu,
  run,
  validateCatalog,
};

if (require.main === module) {
  process.exitCode = run(process.argv.slice(2));
}
