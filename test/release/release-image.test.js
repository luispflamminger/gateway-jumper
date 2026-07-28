// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

const assert = require("node:assert/strict");
const { execFileSync, spawnSync } = require("node:child_process");
const { mkdtempSync, mkdirSync, writeFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");
const test = require("node:test");

const script = path.resolve(__dirname, "../../scripts/release-image.sh");

function git(cwd, ...args) {
  return execFileSync("git", args, { cwd, encoding: "utf8" }).trim();
}

function fixture(version = "4.12.2") {
  const root = mkdtempSync(path.join(tmpdir(), "jumper-release-image-"));
  const bin = path.join(root, "bin");
  mkdirSync(bin);
  git(root, "init", "-q", "-b", "main");
  git(root, "config", "user.name", "Release Test");
  git(root, "config", "user.email", "release@example.com");
  writeFileSync(path.join(root, "content"), "stable\n");
  git(root, "add", "content");
  git(root, "commit", "-q", "-m", "fix: stable");
  const sha = git(root, "rev-parse", "HEAD");
  git(root, "tag", version);

  const state = path.join(root, "registry");
  writeFileSync(state, `sha-${sha}=sha256:candidate\n`);
  writeFileSync(
    path.join(bin, "crane"),
    `#!/usr/bin/env bash\nset -eu\nstate=${JSON.stringify(state)}\nif [[ $1 == digest ]]; then\n  tag=\${2##*:}\n  value=$(sed -n "s/^\${tag}=//p" "$state")\n  [[ -n $value ]] || exit 1\n  printf '%s\\n' "$value"\nelif [[ $1 == tag ]]; then\n  digest=\${2##*@}\n  tag=$3\n  printf '%s=%s\\n' "$tag" "$digest" >> "$state"\nfi\n`,
    { mode: 0o755 },
  );
  writeFileSync(path.join(bin, "cosign"), "#!/usr/bin/env bash\nexit 0\n", {
    mode: 0o755,
  });

  return { root, bin, sha, state, version };
}

function run(f, mode = "publish") {
  return spawnSync(script, [mode, f.version, f.sha], {
    cwd: f.root,
    encoding: "utf8",
    env: {
      ...process.env,
      PATH: `${f.bin}:${process.env.PATH}`,
      IMAGE_REPOSITORY: "registry.example/jumper",
      COSIGN_PUBLIC_KEY: "test-key",
    },
  });
}

test("publishes an absent release tag and accepts an idempotent retry", () => {
  const f = fixture("4.12.3");
  assert.equal(run(f).status, 0);
  assert.equal(run(f, "reconcile").status, 0);
});

test("refuses to move an existing conflicting OCI tag", () => {
  const f = fixture("4.12.3");
  writeFileSync(f.state, "4.12.3=sha256:other\n", { flag: "a" });
  const result = run(f);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /immutable image tag/);
});

test("reconciliation refuses to create a missing Git tag", () => {
  const f = fixture("4.12.3");
  git(f.root, "tag", "-d", f.version);
  const result = run(f, "reconcile");
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /never creates Git tags/);
});

test("moves the next alias only after publishing an immutable RC tag", () => {
  const f = fixture("5.0.0-rc.1");
  assert.equal(run(f).status, 0);
  const state = require("node:fs").readFileSync(f.state, "utf8");
  assert.match(state, /^5\.0\.0-rc\.1=sha256:candidate$/m);
  assert.match(state, /^next=sha256:candidate$/m);
});

test("reconciling an older RC keeps next on the highest RC", () => {
  const f = fixture("5.0.0-rc.1");
  git(f.root, "tag", "5.0.0-rc.2");
  writeFileSync(f.state, "5.0.0-rc.2=sha256:newer\n", { flag: "a" });
  assert.equal(run(f).status, 0);
  const state = require("node:fs").readFileSync(f.state, "utf8");
  assert.match(state, /^next=sha256:newer$/m);
});

test("refuses stable promotion when the RC source tree differs", () => {
  const f = fixture("5.0.0");
  const rcSha = git(f.root, "rev-parse", "HEAD");
  git(f.root, "tag", "5.0.0-rc.1", rcSha);
  writeFileSync(path.join(f.root, "content"), "changed\n");
  git(f.root, "add", "content");
  git(f.root, "commit", "-q", "-m", "fix: changed after RC");
  f.sha = git(f.root, "rev-parse", "HEAD");
  git(f.root, "tag", "-f", "5.0.0", f.sha);
  writeFileSync(f.state, `sha-${f.sha}=sha256:rebuilt\n5.0.0-rc.1=sha256:rc\n`);

  const result = run(f);
  assert.notEqual(result.status, 0);
  assert.match(
    result.stderr,
    /requires an accepted RC with an identical source tree/,
  );
});
