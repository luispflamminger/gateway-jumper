// SPDX-FileCopyrightText: 2025 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

module.exports = {
    branches: ['main'],
    repositoryUrl: 'https://github.com/luispflamminger/gateway-jumper', // TODO: set correct repo
    tagFormat: '${version}',
    plugins: [
        '@semantic-release/commit-analyzer',
        'semantic-release-export-data',
        '@semantic-release/release-notes-generator',
        '@semantic-release/github',
    ],
};
