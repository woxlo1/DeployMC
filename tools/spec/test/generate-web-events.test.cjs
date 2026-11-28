"use strict";

const assert = require("node:assert/strict");
const { execFileSync, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");
const { generate } = require("../generate-web-events.cjs");

const GENERATOR = path.resolve(__dirname, "..", "generate-web-events.cjs");
const ASYNCAPI = path.resolve(__dirname, "..", "..", "..", "specification", "api", "asyncapi.yaml");
const WEB_EVENTS = path.resolve(__dirname, "..", "..", "..", "web", "src", "api", "generated-events.ts");
const TEMP_ROOT = fs.mkdtempSync(path.join(os.tmpdir(), "deploymc-web-events-"));

test.after(() => {
  fs.rmSync(TEMP_ROOT, { recursive: true, force: true });
});

const MINIMAL_SPEC = `
asyncapi: 3.1.0
info:
  title: Fixture Events
  version: 2.2.2
channels:
  events:
    address: /api/v1/ws
operations:
  receiveEvents:
    action: receive
    messages:
      - $ref: '#/components/messages/Notice'
components:
  messages:
    Subscribe:
      payload:
        type: object
        properties:
          channels:
            type: array
            items:
              type: string
              enum: [console]
    Notice:
      payload:
        allOf:
          - type: object
            properties:
              type:
                type: string
                const: server.notice
  schemas:
    EnvelopeBase:
      type: object
      properties:
        protocolVersion:
          type: string
          const: '1'
`;

test("generator derives endpoint, channels, and server event types", () => {
  const specPath = path.join(TEMP_ROOT, "asyncapi.yaml");
  fs.writeFileSync(specPath, MINIMAL_SPEC, "utf8");
  const output = generate(specPath);

  assert.match(output, /export const WS_SPEC_VERSION = "2\.2\.2" as const;/);
  assert.match(output, /export const WS_ENDPOINT = "\/api\/v1\/ws" as const;/);
  assert.match(output, /export const WS_PROTOCOL_VERSION = "1" as const;/);
  assert.match(output, /export const SERVER_EVENT_TYPES: readonly ServerEventType\[\] = \["server\.notice"\];/);
});

test("generator rejects a document without receive events", () => {
  const specPath = path.join(TEMP_ROOT, "invalid.yaml");
  fs.writeFileSync(
    specPath,
    `asyncapi: 3.1.0\ninfo:\n  title: Empty\n  version: 1.0.0\nchannels:\n  events:\n    address: /api/v1/ws\ncomponents:\n  messages:\n    Subscribe:\n      payload:\n        type: object\n        properties:\n          channels:\n            type: array\n            items:\n              type: string\n              enum: [console]\n  schemas:\n    EnvelopeBase:\n      type: object\n      properties:\n        protocolVersion:\n          type: string\n          const: '1'\n`,
    "utf8",
  );
  assert.throws(() => generate(specPath), /receiveEvents has no server messages/);
});

test("check mode detects stale generated event constants", () => {
  const result = spawnSync(process.execPath, [GENERATOR, ASYNCAPI, WEB_EVENTS, "--check"], {
    encoding: "utf8",
  });
  assert.equal(result.status, 0, result.stderr);

  const stale = path.join(TEMP_ROOT, "stale.ts");
  fs.writeFileSync(stale, "// stale\n", "utf8");
  const staleResult = spawnSync(process.execPath, [GENERATOR, ASYNCAPI, stale, "--check"], {
    encoding: "utf8",
  });
  assert.equal(staleResult.status, 1);
  assert.match(staleResult.stderr, /generated event constants are stale/);
});

test("the checked-in event constants match the repository specification", () => {
  const output = execFileSync(process.execPath, [GENERATOR, ASYNCAPI, WEB_EVENTS, "--check"], {
    encoding: "utf8",
  });
  assert.match(output, /generated event constants are current/);
});
