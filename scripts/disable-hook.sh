#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

APP_PACKAGE="dev.priceontop"
SYSTEMUI_PACKAGE="com.android.systemui"
VECTOR_CLI_PATH="/data/adb/lspd/cli"
SCOPE_REMOVAL_COMMAND="${VECTOR_CLI_PATH} scope rm ${APP_PACKAGE} ${SYSTEMUI_PACKAGE}/0"

UNINSTALL=false
EVIDENCE_DIR=""
SUMMARY_FILE=""
COMMANDS_FILE=""
STATUS_FILE=""

usage() {
  cat <<'USAGE'
Usage: scripts/disable-hook.sh [--uninstall] [--help] [--evidence DIR]

Rolls back the Price on Top SystemUI hook wiring by removing the LSPosed/Vector
scope for com.android.systemui/0 and restarting SystemUI. By default this only
removes the scope and restarts SystemUI; use --uninstall to also remove the
dev.priceontop package.

Environment:
  ADB_SERIAL   Optional adb serial number. Set this to target a specific device.
  --evidence   Optional evidence directory. Default: .sisyphus/evidence/disable-hook-<timestamp>.

Behavior:
  - Remove scope using exactly:
    /data/adb/lspd/cli scope rm dev.priceontop com.android.systemui/0
  - Restart SystemUI by killing the current com.android.systemui process.
  - If Vector CLI is missing, the script prints manual recovery steps instead of
    silently reporting success.

Notes:
  If SystemUI refuses to come up after scope removal and restart, force reboot
  the device (`adb reboot`) and if needed, boot to recovery and re-flash to
  recover. Manual force-reboot command:
    adb reboot

Examples:
  ADB_SERIAL=FAKE123 scripts/disable-hook.sh
  scripts/disable-hook.sh --uninstall

USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --uninstall)
        UNINSTALL=true
        shift
        ;;
      --evidence)
        if [[ $# -lt 2 ]]; then
          printf 'Missing value for --evidence\n' >&2
          usage >&2
          exit 2
        fi
        EVIDENCE_DIR="$2"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        printf 'Unknown option: %s\n' "$1" >&2
        usage >&2
        exit 2
        ;;
    esac
  done
}

setup_evidence() {
  if [[ -z "${EVIDENCE_DIR}" ]]; then
    local timestamp
    timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
    EVIDENCE_DIR="${REPO_ROOT}/.sisyphus/evidence/disable-hook-${timestamp}"
  fi

  mkdir -p "${EVIDENCE_DIR}"
  SUMMARY_FILE="${EVIDENCE_DIR}/summary.txt"
  COMMANDS_FILE="${EVIDENCE_DIR}/commands.txt"
  STATUS_FILE="${EVIDENCE_DIR}/status.txt"

  : > "${SUMMARY_FILE}"
  : > "${COMMANDS_FILE}"
  : > "${STATUS_FILE}"

  printf 'script=disable-hook.sh\n' >> "${SUMMARY_FILE}"
  printf 'timestamp=%s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" >> "${SUMMARY_FILE}"
  printf 'app_package=%s\n' "${APP_PACKAGE}" >> "${SUMMARY_FILE}"
  printf 'systemui_package=%s\n' "${SYSTEMUI_PACKAGE}" >> "${SUMMARY_FILE}"
  printf 'vector_cli=%s\n' "${VECTOR_CLI_PATH}" >> "${SUMMARY_FILE}"
  printf 'uninstall=%s\n' "${UNINSTALL}" >> "${SUMMARY_FILE}"
  printf 'adb_serial=%s\n' "${ADB_SERIAL:-<auto>}" >> "${SUMMARY_FILE}"
}

write_status() {
  local status="$1"
  local code="${2:-}"

  printf 'status=%s\n' "${status}" > "${STATUS_FILE}"
  if [[ -n "${code}" ]]; then
    printf 'exit_code=%s\n' "${code}" >> "${STATUS_FILE}"
  fi
}

record_step() {
  local step_name="$1"
  local timestamp
  timestamp="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

  printf '[%s] %s\n' "${timestamp}" "${step_name}"
  if [[ -n "${SUMMARY_FILE}" ]]; then
    printf '[%s] %s\n' "${timestamp}" "${step_name}" >> "${SUMMARY_FILE}"
  fi
}

log_command() {
  if [[ -n "${COMMANDS_FILE}" ]]; then
    printf '%s\n' "$*" >> "${COMMANDS_FILE}"
  fi
}

on_exit() {
  local code="$1"
  if [[ -n "${STATUS_FILE}" ]]; then
    if [[ "${code}" -eq 0 ]]; then
      write_status "success" "${code}"
      printf 'status=%s\n' "success" >> "${SUMMARY_FILE}"
    else
      write_status "failed" "${code}"
      printf 'status=%s\n' "failed" >> "${SUMMARY_FILE}"
    fi
  fi
}

trap 'on_exit "$?"' EXIT

adb_target() {
  local -a adb_cmd

  if [[ -n "${ADB_SERIAL:-}" ]]; then
    adb_cmd=(adb -s "${ADB_SERIAL}" "$@")
  else
    adb_cmd=(adb "$@")
  fi

  log_command "${adb_cmd[@]}"
  "${adb_cmd[@]}"
}

root_shell() {
  local command="$1"
  adb_target shell su -c "${command}"
}

ensure_vector_cli() {
  if ! root_shell "test -e ${VECTOR_CLI_PATH}" >/dev/null 2>&1; then
    write_status "failed" "1"
    cat <<'EOF'
Vector/LSPosed CLI is not available at /data/adb/lspd/cli.

Manual recovery steps:
1) Connect a root-capable adb shell and run:
   adb shell su -c '/data/adb/lspd/cli scope rm dev.priceontop com.android.systemui/0'
2) Restart SystemUI:
   adb shell su -c 'pid=$(pidof com.android.systemui); if [ -n "$pid" ]; then kill $pid; fi'
3) If UI is still stuck or black:
   adb reboot
   (if reboot does not restore, boot to recovery and reboot to repair system)

EOF
    exit 1
  fi
}

restart_systemui() {
  local before_pid after_pid
  before_pid="$(adb_target shell pidof "${SYSTEMUI_PACKAGE}" 2>/dev/null || true)"
  before_pid="$(printf '%s' "${before_pid}" | tr -d '\r' | awk '{print $1}')"
  record_step "Restarting SystemUI (before_pid=${before_pid})"

  if [[ -z "${before_pid}" ]]; then
    printf 'Cannot determine SystemUI pid; cannot restart automatically.\n' >&2
    return 1
  fi

  root_shell "kill ${before_pid}" >/dev/null
  local attempt
  for attempt in {1..30}; do
    after_pid="$(adb_target shell pidof "${SYSTEMUI_PACKAGE}" 2>/dev/null || true)"
    after_pid="$(printf '%s' "${after_pid}" | tr -d '\r' | awk '{print $1}')"
    if [[ -n "${after_pid}" && "${after_pid}" != "${before_pid}" ]]; then
      return 0
    fi
    sleep 1
  done
  printf 'SystemUI did not restart with a new pid.\n' >&2
  return 1
}

rollback() {
  record_step "Removing LSPosed/Vector scope: ${SCOPE_REMOVAL_COMMAND}"
  root_shell "${SCOPE_REMOVAL_COMMAND}"
  record_step "Scope removed"

  restart_systemui

  if [[ "${UNINSTALL}" == true ]]; then
    record_step "Uninstalling ${APP_PACKAGE}"
    root_shell "pm uninstall ${APP_PACKAGE}"
  fi
}

main() {
  parse_args "$@"
  setup_evidence
  ensure_vector_cli
  rollback
}

main "$@"
