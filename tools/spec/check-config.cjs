#!/usr/bin/env node
"use strict";

/**
 * Validate a DeployMC configuration file against
 * `specification/config/config.schema.json` and check semantics the JSON Schema
 * cannot express (cross-references, path containment, limit nesting).
 *
 * Status: the schema is a specification. No plugin loader consumes it yet.
 */

const fs = require("node:fs");
const path = require("node:path");
const YAML = require("yaml");
const Ajv2020 = require("ajv/dist/2020");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const SCHEMA_PATH = path.join(REPO_ROOT, "specification", "config", "config.schema.json");
const EXAMPLE_PATH = path.join(REPO_ROOT, "specification", "config", "example.config.yaml");

const DEFAULTS = {
  "sandbox.max-upload-size-mb": 64,
  "limits.rate-limit-per-minute": 300,
  "auth.token.default-ttl-days": 90,
  "auth.token.max-ttl-days": 365,
};

const SCHEMA = JSON.parse(fs.readFileSync(SCHEMA_PATH, "utf8"));
const PERMISSIONS = new Set(SCHEMA.$defs.permission.enum || []);

function readYaml(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  const doc = YAML.parseDocument(text, { prettyErrors: true });
  if (doc.errors.length > 0) {
    throw new Error(doc.errors.map((error) => error.message).join("\n"));
  }
  return doc.toJSON();
}

function pointerToPath(pointer) {
  if (!pointer) {
    return "(root)";
  }
  return pointer
    .split("/")
    .slice(1)
    .map((part) => part.replace(/~1/g, "/").replace(/~0/g, "~"))
    .join(".");
}

function get(object, dotted) {
  return dotted.split(".").reduce((value, key) => {
    if (value !== null && typeof value === "object" && Object.prototype.hasOwnProperty.call(value, key)) {
      return value[key];
    }
    return undefined;
  }, object);
}

function isRelativeContained(value) {
  if (typeof value !== "string" || value.length === 0) {
    return false;
  }
  if (path.isAbsolute(value) || /^[A-Za-z]:[\\/]/.test(value)) {
    return false;
  }
  return !value.split(/[\\/]/).includes("..");
}

function compileValidator() {
  const schema = JSON.parse(fs.readFileSync(SCHEMA_PATH, "utf8"));
  // The schema carries annotation keywords (x-deploymc-hot-reload, defaults,
  // descriptions) and no code generation, so non-strict mode is intentional.
  const ajv = new Ajv2020({ allErrors: true, strict: false, validateFormats: false });
  return ajv.compile(schema);
}

function lintConfigObject(config, { fileName = "config" } = {}) {
  const errors = [];
  const warnings = [];
  const validate = compileValidator();

  if (config === null || typeof config !== "object" || Array.isArray(config)) {
    return { fileName, errors: ["(root): configuration must be a mapping"], warnings, count: 1 };
  }

  if (!validate(config)) {
    for (const issue of validate.errors || []) {
      errors.push(`${pointerToPath(issue.instancePath)}: ${issue.message}`);
    }
  }

  const roles = config.roles && typeof config.roles === "object" ? config.roles : {};
  const roleNames = new Set(Object.keys(roles));

  if (roleNames.has("none")) {
    errors.push('roles.none: "none" is reserved for the implicit deny-only role');
  }

  for (const [name, role] of Object.entries(roles)) {
    for (const permission of (role && role.permissions) || []) {
      if (permission !== "*" && !PERMISSIONS.has(permission)) {
        errors.push(`roles.${name}.permissions: unknown permission "${permission}"`);
      }
    }
  }

  const defaultRole = config["default-role"] ?? "none";
  if (defaultRole !== "none" && !roleNames.has(defaultRole)) {
    errors.push(`default-role: "${defaultRole}" is not defined in roles (use "none" to deny unknown identities)`);
  }

  for (const [identity, entry] of Object.entries(config.users || {})) {
    for (const role of (entry && entry.roles) || []) {
      if (!roleNames.has(role)) {
        errors.push(`users.${identity}.roles: role "${role}" is not defined in roles`);
      }
    }
  }

  const approval = get(config, "deploy.approval");
  if (approval && approval.required === true) {
    const approverRoles = approval["approver-roles"] || [];
    if (approverRoles.length === 0) {
      errors.push("deploy.approval.approver-roles: must be non-empty when approval is required");
    }
    for (const role of approverRoles) {
      if (!roleNames.has(role)) {
        errors.push(`deploy.approval.approver-roles: role "${role}" is not defined in roles`);
      }
    }
  }

  for (const [name, role] of Object.entries(roles)) {
    const roleLimits = (role && role.limits) || {};
    const pairs = [
      ["max-upload-mb", DEFAULTS["sandbox.max-upload-size-mb"], get(config, "sandbox.max-upload-size-mb")],
      ["rate-limit-per-minute", DEFAULTS["limits.rate-limit-per-minute"], get(config, "limits.rate-limit-per-minute")],
    ];
    for (const [key, fallback, configured] of pairs) {
      const roleValue = roleLimits[key];
      const globalValue = configured ?? fallback;
      if (typeof roleValue === "number" && typeof globalValue === "number" && roleValue > globalValue) {
        errors.push(
          `roles.${name}.limits.${key}: ${roleValue} exceeds the global limit ${globalValue}; a role limit can only lower it`,
        );
      }
    }
  }

  const token = get(config, "auth.token") || {};
  const defaultTtl = token["default-ttl-days"] ?? DEFAULTS["auth.token.default-ttl-days"];
  const maxTtl = token["max-ttl-days"] ?? DEFAULTS["auth.token.max-ttl-days"];
  if (defaultTtl > maxTtl) {
    errors.push("auth.token.default-ttl-days: must not exceed auth.token.max-ttl-days");
  }

  const containment = [
    ["sandbox.root", get(config, "sandbox.root")],
    ["snapshots.directory", get(config, "snapshots.directory")],
    ["deploy.staging-directory", get(config, "deploy.staging-directory")],
    ["audit.directory", get(config, "audit.directory")],
  ];
  for (const [key, value] of containment) {
    if (value !== undefined && !isRelativeContained(value)) {
      errors.push(`${key}: must be a relative path inside the server's plugins directory without ".." segments`);
    }
  }

  const protectedPaths = (get(config, "sandbox.protected-paths") || []).map(String);
  for (const required of [".deploymc/**", "DeployMC/**"]) {
    if (!protectedPaths.includes(required)) {
      warnings.push(`sandbox.protected-paths: "${required}" is not protected; protective defaults are weakened`);
    }
  }

  const warningsForPermissiveSettings = [
    ["sandbox.follow-symlinks", get(config, "sandbox.follow-symlinks"), "symlink following weakens sandbox containment"],
    ["webhooks.allow-private-targets", get(config, "webhooks.allow-private-targets"), "private webhook targets increase SSRF risk"],
    ["telemetry.enabled", get(config, "telemetry.enabled"), "telemetry must stay opt-in and documented"],
  ];
  for (const [key, value, reason] of warningsForPermissiveSettings) {
    if (value === true) {
      warnings.push(`${key}: enabled (${reason})`);
    }
  }

  const bindAddress = get(config, "bind.address");
  const authMode = get(config, "auth.mode");
  const allowNonLoopback = get(config, "bind.allow-non-loopback") === true;
  const loopback = ["127.0.0.1", "::1", "localhost"].includes(bindAddress);
  if (typeof bindAddress === "string" && !loopback) {
    warnings.push(`bind.address: "${bindAddress}" is not loopback; exposure relies on Cloudflare Tunnel or another TLS terminator`);
  }
  if (authMode === "local-onetime" && typeof bindAddress === "string" && !loopback && !allowNonLoopback) {
    errors.push("auth.mode: local-onetime requires a loopback bind or bind.allow-non-loopback: true");
  }
  if (authMode === "local-onetime" && allowNonLoopback) {
    warnings.push("bind.allow-non-loopback: local one-time mode is explicitly allowed outside loopback; local testing only");
  }

  const schemaTopLevel = new Set(Object.keys(SCHEMA.properties || {}));
  for (const key of Object.keys(config)) {
    if (!schemaTopLevel.has(key)) {
      warnings.push(`${key}: unknown top-level key (ignored by the schema, warned by the loader)`);
    }
  }

  return { fileName, errors: [...new Set(errors)], warnings: [...new Set(warnings)], count: 1 };
}

function lintConfigFile(filePath) {
  const resolved = path.resolve(filePath);
  let config;
  try {
    config = readYaml(resolved);
  } catch (error) {
    return {
      fileName: path.relative(REPO_ROOT, resolved) || resolved,
      errors: [`(root): cannot parse YAML: ${error.message}`],
      warnings: [],
      count: 1,
    };
  }
  const result = lintConfigObject(config, { fileName: path.relative(REPO_ROOT, resolved) || resolved });
  return result;
}

function formatResult(result) {
  const lines = [];
  for (const warning of result.warnings) {
    lines.push(`warning  ${result.fileName}  ${warning}`);
  }
  for (const error of result.errors) {
    lines.push(`error    ${result.fileName}  ${error}`);
  }
  if (result.errors.length === 0) {
    lines.push(`ok       ${result.fileName}  (${result.warnings.length} warning(s))`);
  }
  return lines.join("\n");
}

function main(argv) {
  const files = argv.length > 0 ? argv : [EXAMPLE_PATH];
  let failed = false;
  for (const file of files) {
    const result = lintConfigFile(file);
    process.stdout.write(`${formatResult(result)}\n`);
    if (result.errors.length > 0) {
      failed = true;
    }
  }
  if (failed) {
    process.stderr.write("config validation failed\n");
    return 1;
  }
  process.stdout.write("config validation passed\n");
  return 0;
}

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}

module.exports = { lintConfigFile, lintConfigObject, readYaml, get, isRelativeContained, PERMISSIONS };
