#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

COMMAND_LOG="${TMP_DIR}/adb-commands.log"
STATE_DIR="${TMP_DIR}/state"
FAKE_BIN="${TMP_DIR}/bin"
EVIDENCE_DIR="${REPO_ROOT}/.sisyphus/evidence"

DEFAULT_EVIDENCE="${EVIDENCE_DIR}/task-6-disable-fake-adb.txt"
UNINSTALL_EVIDENCE="${EVIDENCE_DIR}/task-6-disable-fake-adb-uninstall.txt"
MISSING_EVIDENCE="${EVIDENCE_DIR}/task-6-disable-missing-cli.txt"

mkdir -p "${STATE_DIR}" "${FAKE_BIN}" "${EVIDENCE_DIR}"

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
    if [[ "${command}" == test\ -e\ /data/adb/lspd/cli ]]; then
      if [[ "${MISSING_VECTOR_CLI:-0}" == "1" ]]; then
        exit 1
      fi
      exit 0
    fi
    if [[ "${command}" == *'/data/adb/lspd/cli scope rm dev.priceontop com.android.systemui/0' ]]; then
      exit 0
    fi
    if [[ "${command}" == kill* ]]; then
      : > "${STATE_DIR}/systemui-killed"
      exit 0
    fi
    if [[ "${command}" == 'pm uninstall dev.priceontop' ]]; then
      exit 0
    fi
    exit 0
  fi

  if [[ "${1:-}" == "pidof" ]]; then
    if [[ -e "${STATE_DIR}/systemui-killed" ]]; then
      printf '2222\n'
    else
      printf '1111\n'
    fi
    exit 0
  fi
fi

exit 0
ADB
chmod +x "${FAKE_BIN}/adb"

export PATH="${FAKE_BIN}:${PATH}"
export COMMAND_LOG STATE_DIR ADB_SERIAL=FAKE123

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local normalized_log
  normalized_log="$(sed 's/\\ / /g' "${haystack}")"
  if ! printf '%s\n' "${normalized_log}" | grep -F -- "${needle}" >/dev/null; then
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
  if printf '%s\n' "${normalized_log}" | grep -F -- "${needle}" >/dev/null; then
    printf 'Unexpected command found: %s\n' "${needle}" >&2
    cat "${haystack}" >&2
    exit 1
  fi
}

assert_file_exists() {
  local path="$1"

  if [[ ! -f "${path}" ]]; then
    printf 'Expected file not found: %s\n' "${path}" >&2
    exit 1
  fi
}

run_and_capture() {
  local args="$1"
  local expected_exit="$2"
  local evidence_path="$3"
  local run_evidence_dir="$4"
  local missing_cli="${5:-0}"

  : > "${COMMAND_LOG}"
  rm -f "${STATE_DIR}/systemui-killed"

  mkdir -p "${run_evidence_dir}"

  printf 'ARGS=%s\n' "${args}" > "${evidence_path}"
  printf 'MISSING_VECTOR_CLI=%s\n' "${missing_cli}" >> "${evidence_path}"
  printf 'EVIDENCE_DIR=%s\n' "${run_evidence_dir}" >> "${evidence_path}"

  local script_args=(--evidence "${run_evidence_dir}")
  if [[ -n "${args}" ]]; then
    script_args+=("${args}")
  fi

  if MISSING_VECTOR_CLI="${missing_cli}" \
    bash "${REPO_ROOT}/scripts/disable-hook.sh" "${script_args[@]}" >> "${evidence_path}" 2>&1; then
    status=0
  else
    status=$?
  fi
  printf '\nexit_code=%s\n' "${status}" >> "${evidence_path}"
  printf '\ncommands:\n' >> "${evidence_path}"
  cat "${COMMAND_LOG}" >> "${evidence_path}"

  if [[ "${status}" != "${expected_exit}" ]]; then
    printf 'Expected exit code %s but got %s\n' "${expected_exit}" "${status}" >&2
    cat "${evidence_path}" >&2
    exit 1
  fi

  assert_file_exists "${run_evidence_dir}/summary.txt"
  assert_file_exists "${run_evidence_dir}/commands.txt"
  assert_file_exists "${run_evidence_dir}/status.txt"
}

run_and_capture "" "0" "${DEFAULT_EVIDENCE}" "${TMP_DIR}/evidence/default"
assert_contains "${DEFAULT_EVIDENCE}" "scope rm dev.priceontop com.android.systemui/0"
assert_contains "${DEFAULT_EVIDENCE}" "kill 1111"
assert_contains "${DEFAULT_EVIDENCE}" "-s FAKE123 shell"
assert_not_contains "${DEFAULT_EVIDENCE}" "uninstall dev.priceontop"
assert_contains "${TMP_DIR}/evidence/default/summary.txt" "status=success"
assert_contains "${TMP_DIR}/evidence/default/commands.txt" "scope rm dev.priceontop com.android.systemui/0"
assert_contains "${TMP_DIR}/evidence/default/commands.txt" "kill 1111"
assert_contains "${TMP_DIR}/evidence/default/status.txt" "status=success"

run_and_capture "--uninstall" "0" "${UNINSTALL_EVIDENCE}" "${TMP_DIR}/evidence/uninstall"
assert_contains "${UNINSTALL_EVIDENCE}" "scope rm dev.priceontop com.android.systemui/0"
assert_contains "${UNINSTALL_EVIDENCE}" "uninstall dev.priceontop"
assert_contains "${UNINSTALL_EVIDENCE}" "kill 1111"
assert_contains "${TMP_DIR}/evidence/uninstall/summary.txt" "uninstall=true"
assert_contains "${TMP_DIR}/evidence/uninstall/commands.txt" "pm uninstall dev.priceontop"
assert_contains "${TMP_DIR}/evidence/uninstall/status.txt" "status=success"

run_and_capture "" "1" "${MISSING_EVIDENCE}" "${TMP_DIR}/evidence/missing" 1
assert_contains "${MISSING_EVIDENCE}" "Vector/LSPosed CLI is not available"
assert_contains "${TMP_DIR}/evidence/missing/status.txt" "status=failed"
assert_contains "${TMP_DIR}/evidence/missing/status.txt" "exit_code=1"
assert_contains "${TMP_DIR}/evidence/missing/summary.txt" "status=failed"

printf 'rollback command construction tests passed\n'
