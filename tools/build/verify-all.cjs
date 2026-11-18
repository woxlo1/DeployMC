"use strict";

// One-command local verification for every language toolchain in the repository.
// It mirrors the commands in .github/workflows/ci.yml; CI remains the merge authority.

const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const root = path.resolve(__dirname, "..", "..");
const isWindows = process.platform === "win32";
const nonAscii = (value) => /[^\x00-\x7F]/.test(value);
const q = (value) => `"${value}"`;

function javaWorkingDirectory() {
  if (!isWindows || !nonAscii(root)) return root;
  if (process.env.DEPLOYMC_JAVA_ROOT) return path.resolve(process.env.DEPLOYMC_JAVA_ROOT);

  // Gradle test workers cannot use a non-ASCII classpath on this Windows host.
  // Reuse or create the documented ASCII junction instead of failing the whole run.
  const link = path.join(os.tmpdir(), "deploymc-ascii");
  if (fs.existsSync(link)) {
    const viaLink = fs.realpathSync(path.join(link, ".git"));
    const direct = fs.realpathSync(path.join(root, ".git"));
    if (viaLink.toLowerCase() === direct.toLowerCase()) return link;
    throw new Error(
      `${link} points to another checkout. Set DEPLOYMC_JAVA_ROOT to a different ASCII path.`,
    );
  }
  const made = spawnSync("cmd", ["/c", "mklink", "/J", link, root], { encoding: "utf8" });
  if (made.status !== 0) {
    throw new Error(`could not create ${link}: ${(made.stderr || "").trim()}`);
  }
  return link;
}

function cargoEnvironment() {
  const env = { ...process.env };
  if (isWindows && nonAscii(root) && !env.CARGO_TARGET_DIR) {
    // MinGW's linker cannot open object files below the non-ASCII repository path.
    env.CARGO_TARGET_DIR = path.join(os.tmpdir(), "deploymc-cli-target");
  }
  return env;
}

function buildSteps() {
  const java = javaWorkingDirectory();
  const cargoEnv = cargoEnvironment();
  const web = path.join(root, "web");
  const cli = path.join(root, "cli");
  const python = path.join(root, "sdk", "python");
  const spec = path.join(root, "tools", "spec");
  const venvPython = isWindows
    ? path.join(python, ".venv", "Scripts", "python.exe")
    : path.join(python, ".venv", "bin", "python");
  const basePython = process.env.DEPLOYMC_PYTHON || "python";
  const pythonCommands = [];

  if (!fs.existsSync(venvPython)) {
    pythonCommands.push({ command: `${q(basePython)} -m venv ${q(path.join(python, ".venv"))}`, cwd: python });
  }
  pythonCommands.push(
    { command: `${q(venvPython)} -m pip install -e ".[dev]"`, cwd: python },
    { command: `${q(venvPython)} -m ruff check .`, cwd: python },
    { command: `${q(venvPython)} -m ruff format --check .`, cwd: python },
    { command: `${q(venvPython)} -m mypy`, cwd: python },
    { command: `${q(venvPython)} -m pytest`, cwd: python },
  );

  return [
    {
      key: "java",
      name: "Java (Gradle, JUnit, Java 8 bytecode)",
      commands: [
        {
          // Spotless' JVM-local cache goes stale when Gradle reuses the configuration cache across
          // local JVMs (diffplug/spotless#987). CI uses a fresh runner, so disable it here only.
          command: `${isWindows ? ".\\gradlew.bat" : "./gradlew"} build verifyJava8 --console=plain --no-configuration-cache`,
          cwd: java,
        },
      ],
    },
    {
      key: "web",
      name: "Web (pnpm, ESLint, Prettier, Vitest, Vite)",
      commands: [
        { command: "pnpm install --frozen-lockfile", cwd: web },
        { command: "pnpm lint", cwd: web },
        { command: "pnpm format:check", cwd: web },
        { command: "pnpm test", cwd: web },
        { command: "pnpm build", cwd: web },
      ],
    },
    {
      key: "cli",
      name: "CLI (cargo fmt, clippy, test, build)",
      commands: [
        { command: "cargo fmt --all -- --check", cwd: cli, env: cargoEnv },
        { command: "cargo clippy --workspace --all-targets -- -D warnings", cwd: cli, env: cargoEnv },
        { command: "cargo test --workspace", cwd: cli, env: cargoEnv },
        { command: "cargo build --workspace", cwd: cli, env: cargoEnv },
      ],
    },
    {
      key: "python",
      name: "Python SDK (venv, ruff, mypy, pytest)",
      commands: pythonCommands,
    },
    {
      key: "spec",
      name: "Specifications (npm, lint, checker tests)",
      commands: [
        { command: "npm ci", cwd: spec },
        { command: "npm run lint", cwd: spec },
        { command: "npm test", cwd: spec },
      ],
    },
    {
      key: "i18n",
      name: "i18n (catalog check, checker tests, status drift)",
      commands: [
        { command: "node tools/i18n/check", cwd: root },
        { command: "node --test tools/i18n/test/check.test.cjs", cwd: root },
        { command: "node tools/i18n/check --write-status", cwd: root },
        { command: "git diff --exit-code -- docs/i18n/STATUS.md", cwd: root },
      ],
    },
  ];
}

function usage() {
  console.log("Usage: node tools/build/verify-all.cjs [--only=java,web,cli,python,spec,i18n]");
  console.log("Runs the local equivalents of the CI jobs from the repository root.");
  console.log("A failing command stops its toolchain but the other toolchains still run.");
}

function runCommand(item) {
  const relative = path.relative(root, item.cwd) || ".";
  console.log(`> [${relative}] ${item.command}`);
  const result = spawnSync(item.command, {
    cwd: item.cwd,
    env: item.env || process.env,
    shell: true,
    stdio: "inherit",
  });
  if (result.error) {
    console.error(`ERROR ${result.error.message}`);
    return false;
  }
  if (result.status !== 0) {
    console.error(`FAILED with exit code ${result.status}`);
    return false;
  }
  return true;
}

function main() {
  const args = process.argv.slice(2);
  if (args.includes("--help") || args.includes("-h")) {
    usage();
    return;
  }
  const unknown = args.filter((arg) => arg !== "--only=" && !arg.startsWith("--only="));
  if (unknown.length > 0) {
    console.error(`unknown argument: ${unknown[0]}`);
    usage();
    process.exitCode = 2;
    return;
  }

  const onlyArg = args.find((arg) => arg.startsWith("--only="));
  const steps = buildSteps();
  const known = new Set(steps.map((step) => step.key));
  let selected = steps;
  if (onlyArg !== undefined) {
    const keys = onlyArg.slice("--only=".length).split(",").map((key) => key.trim().toLowerCase());
    const invalid = keys.filter((key) => !known.has(key));
    if (keys.length === 0 || invalid.length > 0) {
      console.error(`invalid --only value: ${onlyArg}`);
      usage();
      process.exitCode = 2;
      return;
    }
    const wanted = new Set(keys);
    selected = steps.filter((step) => wanted.has(step.key));
  }

  console.log(`DeployMC local verification from ${root}`);
  const results = [];
  for (const step of selected) {
    console.log(`\n=== ${step.name} ===`);
    let ok = true;
    for (const item of step.commands) {
      if (!runCommand(item)) {
        ok = false;
        break;
      }
    }
    results.push({ key: step.key, ok });
  }

  console.log("\n=== Summary ===");
  for (const result of results) {
    console.log(`${result.ok ? "PASS" : "FAIL"} ${result.key}`);
  }
  if (results.some((result) => !result.ok)) {
    process.exitCode = 1;
  }
}

try {
  main();
} catch (error) {
  console.error(error.message);
  process.exitCode = 2;
}
