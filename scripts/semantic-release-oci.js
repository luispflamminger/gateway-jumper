// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

const { execFileSync } = require("node:child_process");
const path = require("node:path");

async function publish({ nextRelease, logger }) {
  logger.log(`Publishing verified image for ${nextRelease.version}`);
  execFileSync(
    path.join(__dirname, "release-image.sh"),
    ["publish", nextRelease.version, nextRelease.gitHead],
    { stdio: "inherit" },
  );

  return {
    name: `OCI image ${nextRelease.version}`,
    url: `${process.env.IMAGE_REPOSITORY}:${nextRelease.version}`,
  };
}

module.exports = { publish };
