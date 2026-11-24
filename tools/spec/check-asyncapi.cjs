#!/usr/bin/env node
"use strict";

/**
 * Validate an AsyncAPI document with the official JavaScript parser.
 *
 * The parser checks the AsyncAPI specification and reports diagnostics. This
 * replaces the CLI wrapper so the specification toolchain does not need the
 * CLI plugin manager and its bundled npm dependency tree.
 */

const fs = require("node:fs");
const { DiagnosticSeverity, Parser } = require("@asyncapi/parser");

function formatDiagnostic(diagnostic) {
  const code = diagnostic.code ? ` [${diagnostic.code}]` : "";
  const location = diagnostic.path ? ` at ${diagnostic.path}` : "";
  return `${diagnostic.message || "invalid AsyncAPI document"}${code}${location}`;
}

async function lintAsyncApiText(text, { fileName = "asyncapi" } = {}) {
  const parser = new Parser();
  const { document, diagnostics } = await parser.parse(text);
  const bySeverity = (severity) => diagnostics.filter((diagnostic) => diagnostic.severity === severity);
  const errors = bySeverity(DiagnosticSeverity.Error).map(formatDiagnostic);
  const warnings = bySeverity(DiagnosticSeverity.Warning).map(formatDiagnostic);
  if (!document && errors.length === 0) {
    errors.push("empty or invalid AsyncAPI document");
  }
  return { fileName, document, errors, warnings };
}

async function lintAsyncApiFile(filePath) {
  return lintAsyncApiText(fs.readFileSync(filePath, "utf8"), { fileName: filePath });
}

async function main() {
  const filePath = process.argv[2];
  if (!filePath) {
    console.error("usage: check-asyncapi.cjs <asyncapi.yaml>");
    process.exitCode = 1;
    return;
  }
  const result = await lintAsyncApiFile(filePath);
  for (const warning of result.warnings) {
    console.warn(`warning\t${result.fileName}\t${warning}`);
  }
  if (result.errors.length > 0) {
    for (const error of result.errors) {
      console.error(`error\t${result.fileName}\t${error}`);
    }
    console.error(`asyncapi validation failed with ${result.errors.length} error(s)`);
    process.exitCode = 1;
    return;
  }
  console.log(`ok\t${result.fileName}\t(${result.warnings.length} warning(s))`);
  console.log("asyncapi validation passed");
}

if (require.main === module) {
  main().catch((error) => {
    console.error(`error\t${error && error.message ? error.message : error}`);
    process.exitCode = 1;
  });
}

module.exports = { lintAsyncApiFile, lintAsyncApiText };
