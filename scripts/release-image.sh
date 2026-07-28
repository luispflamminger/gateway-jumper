#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

readonly MODE="${1:-}"
readonly VERSION="${2:-}"
readonly SOURCE_SHA="${3:-}"
readonly IMAGE_REPOSITORY="${IMAGE_REPOSITORY:?IMAGE_REPOSITORY must be set}"
readonly COSIGN_PUBLIC_KEY="${COSIGN_PUBLIC_KEY:?COSIGN_PUBLIC_KEY must be set}"

die() {
  printf 'release-image: %s\n' "$*" >&2
  exit 1
}

[[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$ ]] || die "invalid release version: ${VERSION}"
[[ "${SOURCE_SHA}" =~ ^[0-9a-f]{40}$ ]] || die "invalid source SHA: ${SOURCE_SHA}"

git_tag_sha=$(git rev-list -n 1 "${VERSION}" 2>/dev/null || true)
if [[ -n "${git_tag_sha}" && "${git_tag_sha}" != "${SOURCE_SHA}" ]]; then
  die "Git tag ${VERSION} points to ${git_tag_sha}, expected ${SOURCE_SHA}"
fi
if [[ "${MODE}" == "reconcile" && -z "${git_tag_sha}" ]]; then
  die "Git tag ${VERSION} does not exist; reconciliation never creates Git tags"
fi

digest_for() {
  crane digest "${IMAGE_REPOSITORY}:$1" 2>/dev/null || true
}

verify_evidence() {
  local reference=$1
  cosign verify --insecure-ignore-tlog=true --key env://COSIGN_PUBLIC_KEY "${reference}" >/dev/null
  cosign verify-attestation --insecure-ignore-tlog=true --key env://COSIGN_PUBLIC_KEY \
    --type vuln "${reference}" >/dev/null
}

source_tag="sha-${SOURCE_SHA}"
source_digest=$(digest_for "${source_tag}")
promotion=false

# A stable major can reuse an accepted RC only when the RC is in its ancestry and
# both commits have exactly the same source tree.
if [[ "${VERSION}" != *-* ]]; then
  if git tag --list "${VERSION}-rc.*" | grep -q .; then
    promotion=true
  fi
  while IFS= read -r rc_tag; do
    [[ -n "${rc_tag}" ]] || continue
    rc_sha=$(git rev-list -n 1 "${rc_tag}")
    if git merge-base --is-ancestor "${rc_sha}" "${SOURCE_SHA}" && \
      [[ "$(git rev-parse "${rc_sha}^{tree}")" == "$(git rev-parse "${SOURCE_SHA}^{tree}")" ]]; then
      rc_digest=$(digest_for "${rc_tag}")
      if [[ -n "${rc_digest}" ]]; then
        source_tag="${rc_tag}"
        source_digest="${rc_digest}"
        break
      fi
    fi
  done < <(git tag --list "${VERSION}-rc.*" --sort=-version:refname)
fi

if [[ "${promotion}" == true && "${source_tag}" == sha-* ]]; then
  die "stable promotion requires an accepted RC with an identical source tree"
fi

[[ -n "${source_digest}" ]] || die "qualified source image ${IMAGE_REPOSITORY}:${source_tag} does not exist"
readonly SOURCE_REFERENCE="${IMAGE_REPOSITORY}:${source_tag}@${source_digest}"
verify_evidence "${SOURCE_REFERENCE}"

release_digest=$(digest_for "${VERSION}")
if [[ -n "${release_digest}" && "${release_digest}" != "${source_digest}" ]]; then
  die "immutable image tag ${VERSION} already points to ${release_digest}, expected ${source_digest}"
fi

if [[ -z "${release_digest}" ]]; then
  crane tag "${IMAGE_REPOSITORY}@${source_digest}" "${VERSION}"
fi

release_digest=$(digest_for "${VERSION}")
[[ "${release_digest}" == "${source_digest}" ]] || \
  die "candidate and release tags resolve to different digests"
verify_evidence "${IMAGE_REPOSITORY}:${VERSION}@${release_digest}"

if [[ "${VERSION}" == *-rc.* ]]; then
  latest_rc=$(git tag --list "*-rc.*" --sort=-version:refname | sed -n '1p')
  [[ -n "${latest_rc}" ]] || die "cannot determine latest RC tag"
  latest_rc_digest=$(digest_for "${latest_rc}")
  [[ -n "${latest_rc_digest}" ]] || die "latest RC image ${latest_rc} does not exist"
  verify_evidence "${IMAGE_REPOSITORY}:${latest_rc}@${latest_rc_digest}"
  crane tag "${IMAGE_REPOSITORY}@${latest_rc_digest}" next
  next_digest=$(digest_for next)
  [[ "${next_digest}" == "${latest_rc_digest}" ]] || die "next alias does not resolve to the latest RC digest"
  verify_evidence "${IMAGE_REPOSITORY}:next@${next_digest}"
fi

printf 'Published %s:%s from %s at %s\n' \
  "${IMAGE_REPOSITORY}" "${VERSION}" "${source_tag}" "${release_digest}"
