"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const { loadStateMachine, checkStateMachine } = require("../check-state-machine.cjs");

function clone(data) {
  return JSON.parse(JSON.stringify(data));
}

test("the committed state machine is consistent", () => {
  const data = loadStateMachine();
  assert.deepEqual(checkStateMachine(data), []);
});

test("a terminal state with an outgoing transition is rejected", () => {
  const data = clone(loadStateMachine());
  data.transitions.push({
    from: "REJECTED",
    to: "CREATED",
    trigger: "resurrect",
    actor: "client",
    guard: "none",
    emits: "deployment.state",
  });
  const errors = checkStateMachine(data);
  assert.ok(errors.some((error) => error.includes("REJECTED") && error.includes("terminal")));
});

test("a missing recovery mapping is rejected", () => {
  const data = clone(loadStateMachine());
  data.recovery = data.recovery.filter((entry) => entry.from !== "RESTARTING");
  const errors = checkStateMachine(data);
  assert.ok(errors.some((error) => error.includes("RESTARTING")));
});

test("a duplicate transition triple is rejected", () => {
  const data = clone(loadStateMachine());
  data.transitions.push({ ...data.transitions[0] });
  const errors = checkStateMachine(data);
  assert.ok(errors.some((error) => error.includes("duplicate")));
});

test("cancellation after APPLYING is rejected", () => {
  const data = clone(loadStateMachine());
  data.transitions.push({
    from: "APPLYING",
    to: "CANCELLED",
    trigger: "late-cancel",
    actor: "client",
    guard: "none",
    emits: "deployment.state",
  });
  const errors = checkStateMachine(data);
  assert.ok(errors.some((error) => error.includes("cancellation")));
});

test("a state that cannot reach a finish state is rejected", () => {
  const data = clone(loadStateMachine());
  for (const transition of data.transitions) {
    if (transition.from === "ROLLING_BACK" && transition.to === "ROLLED_BACK") {
      transition.to = "ROLLING_BACK";
    }
  }
  data.transitions = data.transitions.filter(
    (transition) => !(transition.from === "ROLLING_BACK" && transition.to === "ROLLBACK_FAILED"),
  );
  const errors = checkStateMachine(data);
  assert.ok(errors.some((error) => error.includes("cannot reach")));
});
