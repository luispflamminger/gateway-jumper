// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const { mkdtempSync, writeFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");
const test = require("node:test");
const releaseConfig = require("../../release.config");

test("fork validation disables pull request updates", () => {
  const githubPlugin = releaseConfig.plugins.at(-1);

  assert.deepEqual(githubPlugin, [
    "@semantic-release/github",
    {
      successComment: false,
      failComment: false,
      releasedLabels: false,
    },
  ]);
});

function git(cwd, ...args) {
  return execFileSync("git", args, { cwd, encoding: "utf8" }).trim();
}

function commit(cwd, message, content = message, file = "content") {
  writeFileSync(path.join(cwd, file), `${content}\n`);
  git(cwd, "add", file);
  git(cwd, "commit", "-q", "-m", message);
  return git(cwd, "rev-parse", "HEAD");
}

function repository() {
  const root = mkdtempSync(path.join(tmpdir(), "jumper-release-graph-"));
  const remote = path.join(root, "origin.git");
  const work = path.join(root, "work");
  git(root, "init", "--bare", "-q", remote);
  git(root, "clone", "-q", remote, work);
  git(work, "config", "user.name", "Release Test");
  git(work, "config", "user.email", "release@example.com");
  git(work, "checkout", "-q", "-b", "main");
  commit(work, "chore: baseline");
  git(work, "tag", "4.12.2");
  git(work, "push", "-q", "origin", "main", "--tags");
  git(root, `--git-dir=${remote}`, "symbolic-ref", "HEAD", "refs/heads/main");
  return { remote, work };
}

async function calculate(cwd) {
  const { default: semanticRelease } = await import("semantic-release");
  const env = { ...process.env };
  for (const name of Object.keys(env)) {
    if (name === "CI" || name.startsWith("GITHUB_")) {
      delete env[name];
    }
  }
  const analyzerOptions = releaseConfig.plugins[0][1];
  const plugins = [
    [require.resolve("@semantic-release/commit-analyzer"), analyzerOptions],
  ];
  return semanticRelease(
    {
      branches: releaseConfig.branches,
      tagFormat: releaseConfig.tagFormat,
      plugins,
      repositoryUrl: git(cwd, "remote", "get-url", "origin"),
      dryRun: true,
      ci: false,
    },
    { cwd, env },
  );
}

test("calculates stable and RC versions from a complete synchronized graph", async () => {
  const { work } = repository();
  const stableSha = commit(work, "fix: stable correction");
  git(work, "push", "-q", "origin", "main");
  assert.equal((await calculate(work)).nextRelease.version, "4.12.3");

  git(work, "tag", "4.12.3", stableSha);
  git(work, "checkout", "-q", "-b", "next");
  const rcSha = commit(
    work,
    "feat(mesh): restore mesh authentication\n\nBREAKING CHANGE: mesh authentication requires reverse connectivity",
  );
  git(work, "push", "-q", "origin", "next", "--tags");
  const firstRc = await calculate(work);
  assert.equal(firstRc.nextRelease.version, "5.0.0-rc.1");
  assert.equal(firstRc.nextRelease.channel, "next");

  git(work, "tag", "5.0.0-rc.1", rcSha);
  git(
    work,
    "notes",
    "--ref",
    "semantic-release",
    "add",
    "-m",
    '{"channels":["next"]}',
    "5.0.0-rc.1",
  );
  git(work, "checkout", "-q", "main");
  commit(work, "fix: shared correction", "shared", "shared-content");
  git(work, "tag", "4.12.4");
  git(work, "checkout", "-q", "next");
  git(work, "merge", "-q", "--no-ff", "main", "-m", "Merge main into next");
  git(
    work,
    "push",
    "-q",
    "origin",
    "main",
    "next",
    "--tags",
    "refs/notes/semantic-release",
  );
  assert.equal((await calculate(work)).nextRelease.version, "5.0.0-rc.2");
});

test("promotion calculates stable major and preserves the accepted RC tree", async () => {
  const { work } = repository();
  git(work, "checkout", "-q", "-b", "next");
  const rcSha = commit(
    work,
    "feat(mesh): restore mesh authentication\n\nBREAKING CHANGE: mesh authentication requires reverse connectivity",
  );
  git(work, "tag", "5.0.0-rc.1", rcSha);
  git(
    work,
    "notes",
    "--ref",
    "semantic-release",
    "add",
    "-m",
    '{"channels":["next"]}',
    "5.0.0-rc.1",
  );
  git(work, "checkout", "-q", "main");
  git(work, "merge", "-q", "--no-ff", "next", "-m", "Merge next into main");
  git(
    work,
    "push",
    "-q",
    "origin",
    "main",
    "next",
    "--tags",
    "refs/notes/semantic-release",
  );

  assert.equal(
    git(work, "rev-parse", "HEAD^{tree}"),
    git(work, "rev-parse", "5.0.0-rc.1^{tree}"),
  );
  assert.equal((await calculate(work)).nextRelease.version, "5.0.0");
});
