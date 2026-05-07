#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
EVIDENCE_DIR="${REPO_ROOT}/.sisyphus/evidence"
EVIDENCE_FILE="${EVIDENCE_DIR}/task-9-ci-local.txt"

mkdir -p "${EVIDENCE_DIR}"

{
  printf '\n==== PriceOnTop local CI run: %s ====\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  printf 'Repository root: %s\n' "${REPO_ROOT}"
  printf 'Gradle tasks: clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyXposedMetadata :app:verifyNoSecretsAndNoUiThreadNetwork\n'
  printf 'Script tests: scripts/test-smoke-rooted.sh\n'
} >> "${EVIDENCE_FILE}"

cd "${REPO_ROOT}"

exec > >(tee -a "${EVIDENCE_FILE}") 2>&1

trap 'status=$?; if [[ ${status} -eq 0 ]]; then printf "CI finished with exit code 0. Evidence: %s\n" "${EVIDENCE_FILE}"; else printf "CI failed with exit code %s. Evidence: %s\n" "${status}" "${EVIDENCE_FILE}"; fi' EXIT

if [[ ! -x "${REPO_ROOT}/gradlew" ]]; then
  printf 'Gradle wrapper is missing or not executable: %s\n' "${REPO_ROOT}/gradlew" >&2
  exit 1
fi

export CI=true

./gradlew --no-daemon \
  clean \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:verifyXposedMetadata \
  :app:verifyNoSecretsAndNoUiThreadNetwork

bash "${REPO_ROOT}/scripts/test-smoke-rooted.sh"
