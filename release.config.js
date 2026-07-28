// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

module.exports = {
  branches: [
    { name: "main", channel: false },
    { name: "next", prerelease: "rc", channel: "next" },
  ],
  tagFormat: "${version}",
  plugins: [
    [
      "@semantic-release/commit-analyzer",
      {
        releaseRules: [
          { type: "build", release: "patch" },
          { type: "chore", release: "patch" },
          { type: "ci", release: "patch" },
          { type: "docs", release: "patch" },
          { type: "perf", release: "patch" },
          { type: "refactor", release: "patch" },
          { type: "revert", release: "patch" },
          { type: "style", release: "patch" },
          { type: "test", release: "patch" },
        ],
      },
    ],
    "semantic-release-export-data",
    [
      "@semantic-release/release-notes-generator",
      {
        preset: "conventionalcommits",
        presetConfig: {
          types: [
            { type: "feat", section: "Features", hidden: false },
            { type: "fix", section: "Bug Fixes", hidden: false },
            { type: "build", section: "Build System", hidden: false },
            { type: "chore", section: "Chores", hidden: false },
            { type: "ci", section: "Continuous Integration", hidden: false },
            { type: "docs", section: "Documentation", hidden: false },
            {
              type: "perf",
              section: "Performance Improvements",
              hidden: false,
            },
            { type: "refactor", section: "Code Refactoring", hidden: false },
            { type: "revert", section: "Reverts", hidden: false },
            { type: "style", section: "Styles", hidden: false },
            { type: "test", section: "Tests", hidden: false },
          ],
        },
      },
    ],
    [
      "@semantic-release/exec",
      {
        publishCmd:
          "./scripts/release-image.sh publish ${nextRelease.version} ${nextRelease.gitHead}",
      },
    ],
    // Fork validation only: remove this override before opening the upstream PR.
    [
      "@semantic-release/github",
      {
        successComment: false,
        failComment: false,
        releasedLabels: false,
      },
    ],
  ],
};
