"use strict";

const assert = require("node:assert/strict");
const { execFileSync, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");
const { generate } = require("../generate-web-client.cjs");

const GENERATOR = path.resolve(__dirname, "..", "generate-web-client.cjs");
const OPENAPI = path.resolve(__dirname, "..", "..", "..", "specification", "api", "openapi.yaml");
const WEB_CLIENT = path.resolve(__dirname, "..", "..", "..", "web", "src", "api", "generated.ts");
const TEMP_ROOT = fs.mkdtempSync(path.join(os.tmpdir(), "deploymc-web-client-"));

test.after(() => {
  fs.rmSync(TEMP_ROOT, { recursive: true, force: true });
});

const MINIMAL_SPEC = `
openapi: 3.1.0
info:
  title: Fixture API
  version: 9.9.9
paths:
  /healthz:
    get:
      operationId: getHealth
      security: []
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Health'
  /api/v1/items/{id}:
    put:
      operationId: updateItem
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '204':
          description: updated
        '409':
          description: conflict
components:
  schemas:
    PermissionIdentifier:
      type: string
      enum: [server.read]
    Health:
      type: object
      additionalProperties: false
      required: [status]
      properties:
        status:
          type: string
          const: ok
`;

test("generator derives operations, parameters, and schema literals from the spec", () => {
  const specPath = path.join(TEMP_ROOT, "openapi.yaml");
  fs.writeFileSync(specPath, MINIMAL_SPEC, "utf8");
  const output = generate(specPath);

  assert.match(output, /export const API_SPEC_VERSION = "9\.9\.9" as const;/);
  assert.match(output, /export interface Health \{/);
  assert.match(output, /readonly status: "ok";/);
  assert.match(output, /operationId: "getHealth"/);
  assert.match(output, /authenticated: false/);
  assert.match(output, /operationId: "updateItem"/);
  assert.match(output, /name: "id", in: "path", required: true/);
  assert.match(output, /hasRequestBody: false/);
  assert.match(output, /successStatuses: \[204\]/);
  assert.match(output, /errorStatuses: \[409\]/);
  assert.match(output, /export const API_PERMISSION_IDENTIFIERS: readonly string\[\] = \["server\.read"\];/);
  assert.match(output, /export interface ApiSuccessSchemaMap \{/);
  assert.match(output, /readonly getHealth: "Health";/);
  assert.match(output, /readonly updateItem: null;/);
});

test("generator rejects duplicate operation ids", () => {
  const specPath = path.join(TEMP_ROOT, "duplicate.yaml");
  fs.writeFileSync(
    specPath,
    `
openapi: 3.1.0
info:
  title: Duplicate
  version: 1.0.0
paths:
  /a:
    get:
      operationId: same
      responses: {}
  /b:
    get:
      operationId: same
      responses: {}
components:
  schemas:
    PermissionIdentifier:
      type: string
      enum: [server.read]
`,
    "utf8",
  );
  assert.throws(() => generate(specPath), /missing or duplicate operationId/);
});

test("check mode detects stale generated output", () => {
  const result = spawnSync(
    process.execPath,
    [GENERATOR, OPENAPI, WEB_CLIENT, "--check"],
    { encoding: "utf8" },
  );
  assert.equal(result.status, 0, result.stderr);

  const stale = path.join(TEMP_ROOT, "stale.ts");
  fs.writeFileSync(stale, "// stale\n", "utf8");
  const staleResult = spawnSync(process.execPath, [GENERATOR, OPENAPI, stale, "--check"], {
    encoding: "utf8",
  });
  assert.equal(staleResult.status, 1);
  assert.match(staleResult.stderr, /generated client is stale/);
});

test("the checked-in client matches the repository specification", () => {
  const output = execFileSync(process.execPath, [GENERATOR, OPENAPI, WEB_CLIENT, "--check"], {
    encoding: "utf8",
  });
  assert.match(output, /generated client is current/);
});
