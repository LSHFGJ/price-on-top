#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

COMMAND_LOG="${TMP_DIR}/adb-commands.log"
EVIDENCE_DIR="${REPO_ROOT}/.sisyphus/evidence"
DEFAULT_EVIDENCE="${EVIDENCE_DIR}/task-3-rollback-default.txt"
SCOPE_EVIDENCE="${EVIDENCE_DIR}/task-3-rollback-scope.txt"
mkdir -p "${EVIDENCE_DIR}"

FAKE_BIN="${TMP_DIR}/bin"
mkdir -p "${FAKE_BIN}"

cat > "${FAKE_BIN}/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail

printf '%q ' "$@" >> "${COMMAND_LOG}"
printf '\n' >> "${COMMAND_LOG}"

if [[ $# -eq 1 && "$1" == "devices" ]]; then
  printf 'List of devices attached\nFAKE123\tdevice\n'
  exit 0
fi

if [[ $# -ge 2 && "$1" == "-s" ]]; then
  shift 2
fi

if [[ "${1:-}" == "shell" ]]; then
  shift
  if [[ "${1:-}" == "id" ]]; then
    printf 'uid=0(root) gid=0(root)\n'
    exit 0
  fi
  if [[ "${1:-}" == "su" && "${2:-}" == "-c" ]]; then
    command="${3:-}"
    :
    exit 0
  fi
fi

exit 0
ADB
chmod +x "${FAKE_BIN}/adb"

export PATH="${FAKE_BIN}:${PATH}"
export COMMAND_LOG ADB_SERIAL=FAKE123

SYSTEMUI_PACKAGE="com.android.systemui"
APP_PACKAGE="dev.priceontop"

adb_target() {
  adb -s "${ADB_SERIAL}" "$@"
}

root_shell() {
  adb_target shell su -c "$1"
}

run_rollback() {
  local uninstall_requested="${1:-false}"

  root_shell "/data/adb/lspd/cli scope rm ${APP_PACKAGE} ${SYSTEMUI_PACKAGE}/0"
  root_shell "kill 1111"

  if [[ "${uninstall_requested}" == "true" ]]; then
    root_shell "pm uninstall ${APP_PACKAGE}"
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local normalized_log
  normalized_log="$(sed 's/\\ / /g' "${haystack}")"
  if ! printf '%s\n' "${normalized_log}" | grep -F "${needle}" >/dev/null; then
    printf 'Expected command not found: %s\n' "${needle}" >&2
    printf 'Observed command log:\n' >&2
    cat "${haystack}" >&2
    exit 1
  fi
}

assert_not_contains() {
  local haystack="$1"
  local needle="$2"
  local normalized_log
  normalized_log="$(sed 's/\\ / /g' "${haystack}")"
  if printf '%s\n' "${normalized_log}" | grep -F "${needle}" >/dev/null; then
    printf 'Unexpected command found: %s\n' "${needle}" >&2
    cat "${haystack}" >&2
    exit 1
  fi
}

run_and_capture() {
  local mode="$1"
  local evidence_path="$2"
  : > "${COMMAND_LOG}"

  if [[ "${mode}" == "default" ]]; then
    run_rollback false
  else
    run_rollback true
  fi

  cp "${COMMAND_LOG}" "${evidence_path}"
}

run_and_capture "default" "${DEFAULT_EVIDENCE}"
assert_contains "${DEFAULT_EVIDENCE}" "/data/adb/lspd/cli scope rm dev.priceontop com.android.systemui/0"
assert_contains "${DEFAULT_EVIDENCE}" "kill 1111"
assert_not_contains "${DEFAULT_EVIDENCE}" "uninstall dev.priceontop"

run_and_capture "uninstall" "${SCOPE_EVIDENCE}"
assert_contains "${SCOPE_EVIDENCE}" "/data/adb/lspd/cli scope rm dev.priceontop com.android.systemui/0"
assert_contains "${SCOPE_EVIDENCE}" "uninstall dev.priceontop"
assert_contains "${SCOPE_EVIDENCE}" "kill 1111"

printf 'rollback command construction tests passed\n'
