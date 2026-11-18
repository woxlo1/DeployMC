#!/usr/bin/env node
"use strict";

/**
 * Validate the normative transition data in
 * `specification/deployment/state-machine.md`.
 *
 * Status: the deployment engine does not exist. This script checks the
 * specification's own consistency so the future model-based test starts from a
 * sound table.
 */

const fs = require("node:fs");
const path = require("node:path");
const YAML = require("yaml");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const SPEC_PATH = path.join(REPO_ROOT, "specification", "deployment", "state-machine.md");
const BEGIN = "<!-- state-machine-data:begin -->";
const END = "<!-- state-machine-data:end -->";

const REQUIRED_RECOVERY = [
  "APPROVED",
  "SNAPSHOTTING",
  "APPLYING",
  "RESTART_PENDING",
  "RESTARTING",
  "VERIFYING",
  "ROLLING_BACK",
  "ROLLBACK_FAILED",
];

const TARGET_WRITE_STATES = new Set(["APPLYING", "ROLLING_BACK"]);
const FINISH_STATES = new Set(["SUCCEEDED", "REJECTED", "ROLLED_BACK", "CANCELLED", "SAFE_STATE"]);
const CANCELLABLE = new Set(["CREATED", "UPLOADING", "VALIDATING", "STAGED", "AWAITING_APPROVAL"]);

function extractDataBlock(markdown) {
  const start = markdown.indexOf(BEGIN);
  const end = markdown.indexOf(END);
  if (start === -1 || end === -1 || end <= start) {
    throw new Error(`missing state-machine data markers (${BEGIN} ... ${END})`);
  }
  const section = markdown.slice(start + BEGIN.length, end);
  const fence = section.match(/```ya?ml\n([\s\S]*?)```/);
  if (!fence) {
    throw new Error("the marked section does not contain a fenced yaml block");
  }
  return YAML.parse(fence[1]);
}

function reachableFrom(initial, edges) {
  const seen = new Set([initial]);
  const queue = [initial];
  while (queue.length > 0) {
    const current = queue.shift();
    for (const next of edges.get(current) || []) {
      if (!seen.has(next)) {
        seen.add(next);
        queue.push(next);
      }
    }
  }
  return seen;
}

function canReach(targets, reverseEdges) {
  const seen = new Set(targets);
  const queue = [...targets];
  while (queue.length > 0) {
    const current = queue.shift();
    for (const previous of reverseEdges.get(current) || []) {
      if (!seen.has(previous)) {
        seen.add(previous);
        queue.push(previous);
      }
    }
  }
  return seen;
}

function checkStateMachine(data) {
  const errors = [];
  const push = (message) => errors.push(message);

  if (!data || typeof data !== "object") {
    return ["data block is not a mapping"];
  }
  if (data.version !== 1) {
    push(`version: expected 1, found ${JSON.stringify(data.version)}`);
  }

  const states = Array.isArray(data.states) ? data.states : [];
  if (states.length === 0) {
    return [...errors, "states: must be a non-empty list"];
  }
  const byName = new Map();
  for (const state of states) {
    if (!state || typeof state.name !== "string") {
      push("states: every entry needs a name");
      continue;
    }
    if (!/^[A-Z][A-Z0-9_]*$/.test(state.name)) {
      push(`states.${state.name}: name must be UPPER_SNAKE_CASE`);
    }
    if (byName.has(state.name)) {
      push(`states.${state.name}: duplicate state name`);
    }
    byName.set(state.name, state);
    if (typeof state.terminal !== "boolean") {
      push(`states.${state.name}: terminal must be a boolean`);
    }
    if (!["yes", "no", "unknown"].includes(state["target-writes"])) {
      push(`states.${state.name}: target-writes must be yes, no, or unknown`);
    }
  }

  const initial = data.initial;
  if (!byName.has(initial)) {
    push(`initial: "${initial}" is not a declared state`);
  } else if (byName.get(initial).class !== "initial") {
    push(`initial: "${initial}" must have class initial`);
  }

  for (const name of TARGET_WRITE_STATES) {
    if (byName.has(name) && byName.get(name)["target-writes"] !== "yes") {
      push(`states.${name}: must declare target-writes: yes`);
    }
  }
  for (const state of states) {
    if (state["target-writes"] === "yes" && !TARGET_WRITE_STATES.has(state.name)) {
      push(`states.${state.name}: only APPLYING and ROLLING_BACK may write target files`);
    }
  }

  const transitions = Array.isArray(data.transitions) ? data.transitions : [];
  const seenTriples = new Set();
  const outgoing = new Map();
  const incoming = new Map();
  const termination = [];

  for (const transition of transitions) {
    if (!transition || typeof transition.from !== "string" || typeof transition.to !== "string") {
      push("transitions: every entry needs from and to");
      continue;
    }
    const label = `${transition.from} -> ${transition.to} (${transition.trigger || "no-trigger"})`;
    if (!byName.has(transition.from)) {
      push(`${label}: unknown from state`);
    }
    if (!byName.has(transition.to)) {
      push(`${label}: unknown to state`);
    }
    if (typeof transition.trigger !== "string" || transition.trigger.length === 0) {
      push(`${label}: trigger is required`);
    }
    if (!["client", "system", "approver", "operator"].includes(transition.actor)) {
      push(`${label}: actor must be client, system, approver, or operator`);
    }
    if (typeof transition.guard !== "string" || transition.guard.length === 0) {
      push(`${label}: guard is required (use "none" to state that explicitly)`);
    }
    const triple = `${transition.from}|${transition.to}|${transition.trigger}`;
    if (seenTriples.has(triple)) {
      push(`${label}: duplicate (from, to, trigger)`);
    }
    seenTriples.add(triple);
    if (!outgoing.has(transition.from)) outgoing.set(transition.from, []);
    outgoing.get(transition.from).push(transition.to);
    if (!incoming.has(transition.to)) incoming.set(transition.to, []);
    incoming.get(transition.to).push(transition.from);
  }

  const recovery = Array.isArray(data.recovery) ? data.recovery : [];
  const recoveryStates = new Set();
  for (const entry of recovery) {
    if (!entry || !byName.has(entry.from)) {
      push(`recovery: unknown from state ${JSON.stringify(entry && entry.from)}`);
      continue;
    }
    if (typeof entry.action !== "string" || entry.action.length === 0) {
      push(`recovery.${entry.from}: action is required`);
    }
    recoveryStates.add(entry.from);
  }

  for (const state of states) {
    const hasOutgoing = (outgoing.get(state.name) || []).length > 0;
    const hasRecovery = recoveryStates.has(state.name);
    if (state.terminal) {
      if (hasOutgoing) {
        push(`states.${state.name}: terminal state must not have outgoing transitions`);
      }
      if (hasRecovery) {
        push(`states.${state.name}: terminal state must not have a recovery action`);
      }
    } else if (!hasOutgoing && !hasRecovery) {
      push(`states.${state.name}: non-terminal state has no outgoing transition or recovery action`);
    }
  }

  const initialIncoming = incoming.get(initial) || [];
  if (initialIncoming.length > 0) {
    push(`initial: "${initial}" must not have incoming transitions (found ${initialIncoming.join(", ")})`);
  }

  if (byName.has("RESTARTING")) {
    const successors = [...new Set(outgoing.get("RESTARTING") || [])];
    if (successors.length !== 1 || successors[0] !== "VERIFYING") {
      push(`states.RESTARTING: the only runtime successor must be VERIFYING, found ${successors.join(", ") || "none"}`);
    }
  }

  if (byName.has("ROLLBACK_FAILED")) {
    const successors = [...new Set(outgoing.get("ROLLBACK_FAILED") || [])];
    if (successors.length !== 1 || successors[0] !== "SAFE_STATE") {
      push(
        `states.ROLLBACK_FAILED: the only successor must be SAFE_STATE, found ${successors.join(", ") || "none"}`,
      );
    }
    if (!recoveryStates.has("ROLLBACK_FAILED")) {
      push("recovery: ROLLBACK_FAILED must map to SAFE_STATE on startup");
    }
  }

  if (byName.has("FAILED")) {
    const failed = transitions.filter((transition) => transition.from === "FAILED");
    if (failed.length !== 1 || failed[0].to !== "ROLLING_BACK") {
      push("states.FAILED: the only runtime successor must be ROLLING_BACK");
    } else if (failed[0].guard !== "target-writes-occurred") {
      push('states.FAILED: the ROLLING_BACK transition must guard on "target-writes-occurred"');
    }
  }

  for (const transition of transitions) {
    if (transition.to === "CANCELLED" && !CANCELLABLE.has(transition.from)) {
      push(`${transition.from} -> CANCELLED: cancellation is not allowed from this state`);
    }
  }

  const toApply = [...new Set(incoming.get("APPLYING") || [])];
  if (toApply.length !== 1 || toApply[0] !== "SNAPSHOTTING") {
    push(`states.APPLYING: must be entered only from SNAPSHOTTING, found ${toApply.join(", ") || "none"}`);
  }
  const toRollingBack = [...new Set(incoming.get("ROLLING_BACK") || [])];
  if (toRollingBack.length !== 1 || toRollingBack[0] !== "FAILED") {
    push(`states.ROLLING_BACK: must be entered only from FAILED, found ${toRollingBack.join(", ") || "none"}`);
  }

  for (const name of REQUIRED_RECOVERY) {
    if (byName.has(name) && !recoveryStates.has(name)) {
      push(`recovery: missing handling for ${name}`);
    }
  }

  if (byName.has(initial)) {
    const reachable = reachableFrom(initial, outgoing);
    for (const state of states) {
      // A state that is only entered by startup recovery is still required to be
      // reachable in the runtime graph: recovery does not invent new states.
      if (!reachable.has(state.name)) {
        push(`states.${state.name}: not reachable from ${initial}`);
      }
    }

    const reverse = new Map();
    for (const [from, targets] of outgoing) {
      for (const to of targets) {
        if (!reverse.has(to)) reverse.set(to, []);
        reverse.get(to).push(from);
      }
    }
    const finishing = canReach(FINISH_STATES, reverse);
    for (const state of states) {
      if (!finishing.has(state.name)) {
        push(`states.${state.name}: cannot reach any of ${[...FINISH_STATES].join(", ")}`);
      }
    }
  }

  for (const state of states) {
    if (state.class === "terminal" && state.terminal !== true) {
      push(`states.${state.name}: class terminal requires terminal: true`);
    }
  }

  return errors;
}

function loadStateMachine(filePath = SPEC_PATH) {
  return extractDataBlock(fs.readFileSync(filePath, "utf8"));
}

function main() {
  let data;
  try {
    data = loadStateMachine();
  } catch (error) {
    process.stderr.write(`state machine: ${error.message}\n`);
    return 1;
  }
  const errors = checkStateMachine(data);
  if (errors.length > 0) {
    for (const error of errors) {
      process.stderr.write(`error  ${error}\n`);
    }
    process.stderr.write("state machine validation failed\n");
    return 1;
  }
  const stateCount = data.states.length;
  const transitionCount = data.transitions.length;
  process.stdout.write(
    `ok  specification/deployment/state-machine.md  (${stateCount} states, ${transitionCount} transitions)\n`,
  );
  process.stdout.write("state machine validation passed\n");
  return 0;
}

if (require.main === module) {
  process.exitCode = main();
}

module.exports = { checkStateMachine, extractDataBlock, loadStateMachine, SPEC_PATH };
