"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const { lintAsyncApiFile, lintAsyncApiText } = require("../check-asyncapi.cjs");

const SPEC_PATH = path.resolve(__dirname, "..", "..", "..", "specification", "api", "asyncapi.yaml");

test("valid repository AsyncAPI document has no errors", async () => {
  const result = await lintAsyncApiFile(SPEC_PATH);
  assert.ok(result.document, "expected a parsed AsyncAPI document");
  assert.deepEqual(result.errors, []);
});

test("invalid AsyncAPI document is rejected", async () => {
  const result = await lintAsyncApiText("asyncapi: '3.0.0'\ninfo:\n  title: Missing version\n");
  assert.ok(result.errors.length > 0, "expected validation errors");
});

test("empty AsyncAPI document is rejected", async () => {
  const result = await lintAsyncApiText(fs.readFileSync(SPEC_PATH, "utf8").slice(0, 0));
  assert.ok(result.errors.length > 0, "expected an empty-document error");
});
