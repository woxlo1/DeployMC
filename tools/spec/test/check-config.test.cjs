"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const path = require("node:path");
const { lintConfigFile, lintConfigObject } = require("../check-config.cjs");

const EXAMPLE = path.resolve(__dirname, "..", "..", "..", "specification", "config", "example.config.yaml");

function validConfig() {
  return {
    "config-version": 1,
    bind: { address: "127.0.0.1", port: 8080, "allow-non-loopback": false },
    auth: { mode: "token" },
    sandbox: { "protected-paths": [".deploymc/**", "DeployMC/**"] },
    roles: { admin: { permissions: ["*"] }, developer: { permissions: ["files.read"] } },
    users: { "alice@example.com": { roles: ["admin"] } },
    "default-role": "none",
  };
}

test("the committed example configuration is valid", () => {
  const result = lintConfigFile(EXAMPLE);
  assert.deepEqual(result.errors, []);
});

test("an undefined default role is rejected", () => {
  const config = validConfig();
  config["default-role"] = "everyone";
  const result = lintConfigObject(config);
  assert.ok(result.errors.some((error) => error.includes("default-role")));
});

test("an undefined role reference is rejected", () => {
  const config = validConfig();
  config.users["bob@example.com"] = { roles: ["ghost"] };
  const result = lintConfigObject(config);
  assert.ok(result.errors.some((error) => error.includes('role "ghost"')));
});

test("a role limit above the global limit is rejected", () => {
  const config = validConfig();
  config.sandbox["max-upload-size-mb"] = 10;
  config.roles.developer.limits = { "max-upload-mb": 20 };
  const result = lintConfigObject(config);
  assert.ok(result.errors.some((error) => error.includes("exceeds the global limit")));
});

test("a path escaping the plugins directory is rejected", () => {
  const config = validConfig();
  config.sandbox.root = "../world";
  const result = lintConfigObject(config);
  assert.ok(result.errors.some((error) => error.includes("sandbox.root")));
});

test("a Cloudflare configuration without a team domain is rejected", () => {
  const config = validConfig();
  config.auth = { mode: "cloudflare", cloudflare: { audience: "abc" } };
  const result = lintConfigObject(config);
  assert.ok(result.errors.some((error) => error.includes("team-domain")));
});

test("local one-time mode rejects a non-loopback bind without an explicit override", () => {
  const config = validConfig();
  config.bind.address = "0.0.0.0";
  config.auth.mode = "local-onetime";
  const result = lintConfigObject(config);
  assert.ok(result.errors.some((error) => error.includes("local-onetime requires a loopback bind")));
});

test("local one-time mode warns when the loopback override is enabled", () => {
  const config = validConfig();
  config.bind["allow-non-loopback"] = true;
  config.auth.mode = "local-onetime";
  const result = lintConfigObject(config);
  assert.deepEqual(result.errors, []);
  assert.ok(result.warnings.some((warning) => warning.includes("local one-time mode")));
});

test("unknown top-level keys are warnings, not errors", () => {
  const config = validConfig();
  config.unexpected = true;
  const result = lintConfigObject(config);
  assert.deepEqual(result.errors, []);
  assert.ok(result.warnings.some((warning) => warning.includes("unexpected")));
});

test("weakening the protected path defaults is a warning", () => {
  const config = validConfig();
  config.sandbox["protected-paths"] = ["DeployMC/**"];
  const result = lintConfigObject(config);
  assert.deepEqual(result.errors, []);
  assert.ok(result.warnings.some((warning) => warning.includes(".deploymc/**")));
});
