#!/usr/bin/env node
'use strict';

const { run } = require('./lib.cjs');

try {
  const result = run(process.argv.slice(2));
  process.stdout.write(result.report);
  process.exitCode = result.exitCode;
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 2;
}
