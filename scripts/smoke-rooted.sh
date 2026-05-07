#!/usr/bin/env bash
set -euo pipefail
set -E

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

APP_PACKAGE="dev.priceontop"
SYSTEMUI_PACKAGE="com.android.systemui"
APK_PATH="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
PREFS_NAME="price_on_top_private"
PRICE_DOUBLE_BITS="4638387438405602509"

PROVIDER="local"
SYMBOL="BTC"
EVIDENCE_ARG=".sisyphus/evidence/task-10-smoke"
EVIDENCE_DIR=""
SUMMARY_FILE=""
SUMMARY_WRITTEN="false"
FAIL_REASON=""
CURRENT_STEP="initializing"
DEVICE_SERIAL=""
ROOT_MODE=""
ROM_FAMILY="UNKNOWN"
APK_SHA256=""
LOGCAT_PID=""
SERVER_PID=""
SMOKE_STATUS="PASS"
SMOKE_MESSAGE="PriceOnTop hook marker observed; screenshot and logcat evidence captured"

usage() {
  cat <<'USAGE'
Usage: scripts/smoke-rooted.sh [--provider local] [--symbol SYMBOL] [--evidence DIR]

Rooted LSPosed smoke QA for Price on Top. The script writes all artifacts under
the supplied evidence directory, including summary.txt.

Prerequisites:
  - adb is installed and exactly one authorized device is connected, or ADB_SERIAL
    names the target device serial.
  - The target is a rooted LSPosed device with either an adb root shell or su -c id.
  - LSPosed must be detectable through its manager package or root-visible module
    directories, and the Price on Top module must already be enabled for
    com.android.systemui. If activation cannot be verified from logs, the script
    fails with prerequisite diagnostics instead of asking for clicks.
  - A debug APK is available at app/build/outputs/apk/debug/app-debug.apk or the
    Gradle wrapper can build it non-interactively with :app:assembleDebug.

Options:
  --provider MODE   Provider mode for the smoke config. Default: local.
                    local starts a host fake JSON quote server on 127.0.0.1:18080,
                    runs adb reverse tcp:18080 tcp:18080, and configures the app as
                    CUSTOM_JSON. No real external financial endpoint or key is used.
  --symbol SYMBOL   Single symbol to display in smoke evidence. Default: BTC.
  --evidence DIR    Evidence output directory. Relative paths resolve from the
                    repository root. Default: .sisyphus/evidence/task-10-smoke.
  --help, -h        Show this help.

The local fake provider is generated automatically. To inspect or replace it,
serve equivalent JSON on the host before running the smoke flow:
  {"price":123.45,"symbol":"BTC","currency":"$","timestamp":1600000000}
USAGE
}

on_error() {
  local status=$?
  if [[ -z "${FAIL_REASON}" ]]; then
    FAIL_REASON="unexpected failure during ${CURRENT_STEP} at line ${BASH_LINENO[0]}"
  fi
  exit "${status}"
}

cleanup() {
  if [[ -n "${LOGCAT_PID}" ]]; then
    kill "${LOGCAT_PID}" 2>/dev/null || true
    wait "${LOGCAT_PID}" 2>/dev/null || true
  fi
  if [[ -n "${SERVER_PID}" ]]; then
    kill "${SERVER_PID}" 2>/dev/null || true
    wait "${SERVER_PID}" 2>/dev/null || true
  fi
}

write_summary() {
  local status="$1"
  local message="$2"

  if [[ -z "${SUMMARY_FILE}" ]]; then
    return
  fi

  {
    printf 'status=%s\n' "${status}"
    printf 'message=%s\n' "${message}"
    printf 'timestamp_utc=%s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    printf 'repo_root=%s\n' "${REPO_ROOT}"
    printf 'evidence_dir=%s\n' "${EVIDENCE_DIR}"
    printf 'provider=%s\n' "${PROVIDER}"
    printf 'symbol=%s\n' "${SYMBOL}"
    printf 'adb_serial_env=%s\n' "${ADB_SERIAL:-}"
    printf 'selected_device=%s\n' "${DEVICE_SERIAL:-}"
    printf 'root_mode=%s\n' "${ROOT_MODE:-not-verified}"
    printf 'rom_family=%s\n' "${ROM_FAMILY:-UNKNOWN}"
    printf 'apk_path=%s\n' "${APK_PATH}"
    printf 'apk_sha256=%s\n' "${APK_SHA256:-not-recorded}"
    printf 'current_step=%s\n' "${CURRENT_STEP}"
    printf 'evidence_files:\n'
    if [[ -d "${EVIDENCE_DIR}" ]]; then
      local file
      for file in "${EVIDENCE_DIR}"/*; do
        [[ -e "${file}" ]] || continue
        printf '  %s\n' "$(basename -- "${file}")"
      done
    fi
  } > "${SUMMARY_FILE}"
}

finish() {
  local status=$?
  cleanup
  if [[ "${SUMMARY_WRITTEN}" != "true" && -n "${SUMMARY_FILE}" ]]; then
    if [[ ${status} -eq 0 ]]; then
      write_summary "PASS" "rooted LSPosed smoke checks completed"
    else
      write_summary "FAIL" "${FAIL_REASON:-unexpected failure during ${CURRENT_STEP}}"
    fi
  fi
}

trap on_error ERR
trap finish EXIT

fail() {
  local message="$1"
  FAIL_REASON="${message}"
  printf 'Smoke prerequisite failed: %s\n' "${message}" >&2
  write_summary "FAIL" "${message}"
  SUMMARY_WRITTEN="true"
  exit 1
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --provider)
        [[ $# -ge 2 && -n "$2" ]] || fail "--provider requires a value"
        PROVIDER="$2"
        shift 2
        ;;
      --provider=*)
        PROVIDER="${1#--provider=}"
        shift
        ;;
      --symbol)
        [[ $# -ge 2 && -n "$2" ]] || fail "--symbol requires a value"
        SYMBOL="$2"
        shift 2
        ;;
      --symbol=*)
        SYMBOL="${1#--symbol=}"
        shift
        ;;
      --evidence)
        [[ $# -ge 2 && -n "$2" ]] || fail "--evidence requires a directory"
        EVIDENCE_ARG="$2"
        shift 2
        ;;
      --evidence=*)
        EVIDENCE_ARG="${1#--evidence=}"
        shift
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        printf 'Unknown option: %s\n\n' "$1" >&2
        usage >&2
        exit 2
        ;;
    esac
  done

  PROVIDER="${PROVIDER,,}"
  SYMBOL="${SYMBOL^^}"
  [[ -n "${SYMBOL}" ]] || fail "--symbol must not be empty"
  case "${PROVIDER}" in
    local|fake)
      PROVIDER="local"
      ;;
    *)
      fail "unsupported provider mode '${PROVIDER}'; only local/fake is supported without real financial API keys"
      ;;
  esac
}

resolve_evidence_dir() {
  if [[ "${EVIDENCE_ARG}" = /* ]]; then
    EVIDENCE_DIR="${EVIDENCE_ARG}"
  else
    EVIDENCE_DIR="${REPO_ROOT}/${EVIDENCE_ARG}"
  fi
  mkdir -p "${EVIDENCE_DIR}"
  SUMMARY_FILE="${EVIDENCE_DIR}/summary.txt"
}

require_adb() {
  CURRENT_STEP="checking adb availability"
  command -v adb > "${EVIDENCE_DIR}/adb-path.txt" 2>&1 || fail "adb executable not found on PATH"
}

select_device() {
  CURRENT_STEP="selecting adb device"
  local devices_file="${EVIDENCE_DIR}/adb-devices.txt"
  adb devices | tr -d '\r' > "${devices_file}" 2>&1 || fail "adb devices failed; see adb-devices.txt"

  local records serial state device_count selected non_device_state
  records="$(awk 'NR > 1 && NF >= 2 { print $1 "\t" $2 }' "${devices_file}")"

  if [[ -n "${ADB_SERIAL:-}" ]]; then
    while IFS=$'\t' read -r serial state; do
      [[ -n "${serial}" ]] || continue
      if [[ "${serial}" == "${ADB_SERIAL}" ]]; then
        if [[ "${state}" != "device" ]]; then
          fail "adb device ${ADB_SERIAL} is ${state}, not an authorized device"
        fi
        DEVICE_SERIAL="${serial}"
        return
      fi
    done <<< "${records}"
    fail "no matching adb device for ADB_SERIAL=${ADB_SERIAL}"
  fi

  device_count=0
  selected=""
  non_device_state=""
  while IFS=$'\t' read -r serial state; do
    [[ -n "${serial}" ]] || continue
    if [[ "${state}" == "device" ]]; then
      device_count=$((device_count + 1))
      selected="${serial}"
    else
      non_device_state="${serial}:${state}"
    fi
  done <<< "${records}"

  if [[ ${device_count} -eq 0 ]]; then
    if [[ -n "${non_device_state}" ]]; then
      fail "no matching adb device; found non-ready device ${non_device_state}"
    fi
    fail "no matching adb device; connect one authorized rooted LSPosed device or set ADB_SERIAL"
  fi
  if [[ ${device_count} -gt 1 ]]; then
    fail "multiple adb devices connected; set ADB_SERIAL to choose the rooted LSPosed target"
  fi
  DEVICE_SERIAL="${selected}"
}

adb_target() {
  adb -s "${DEVICE_SERIAL}" "$@"
}

shell_out() {
  adb_target shell "$@" | tr -d '\r'
}

root_shell() {
  local command="$1"
  if [[ "${ROOT_MODE}" == "adb-shell" ]]; then
    adb_target shell "${command}"
  else
    adb_target shell su -c "${command}"
  fi
}

record_device_info() {
  CURRENT_STEP="recording device information"
  {
    printf 'serial=%s\n' "${DEVICE_SERIAL}"
    printf 'fingerprint=%s\n' "$(shell_out getprop ro.build.fingerprint || true)"
    printf 'manufacturer=%s\n' "$(shell_out getprop ro.product.manufacturer || true)"
    printf 'brand=%s\n' "$(shell_out getprop ro.product.brand || true)"
    printf 'model=%s\n' "$(shell_out getprop ro.product.model || true)"
    printf 'sdk=%s\n' "$(shell_out getprop ro.build.version.sdk || true)"
  } > "${EVIDENCE_DIR}/device.txt"
  detect_rom_family
}

detect_rom_family() {
  local values
  values="$(tr '\n' ' ' < "${EVIDENCE_DIR}/device.txt" | tr '[:upper:]' '[:lower:]')"
  if [[ "${values}" == *xiaomi* || "${values}" == *redmi* || "${values}" == *poco* || "${values}" == *miui* || "${values}" == *hyperos* ]]; then
    ROM_FAMILY="MIUI_HYPEROS"
  elif [[ "${values}" == *google* || "${values}" == *pixel* || "${values}" == *lineage* || "${values}" == *aosp* ]]; then
    ROM_FAMILY="AOSP_PIXEL_LINEAGE"
  else
    ROM_FAMILY="UNKNOWN"
  fi
  printf 'rom_family=%s\n' "${ROM_FAMILY}" > "${EVIDENCE_DIR}/rom.txt"
}

ensure_apk() {
  CURRENT_STEP="building or verifying debug APK"
  cd "${REPO_ROOT}"
  if [[ ! -f "${APK_PATH}" ]]; then
    [[ -x "${REPO_ROOT}/gradlew" ]] || fail "debug APK missing and Gradle wrapper is not executable"
    ./gradlew --no-daemon :app:assembleDebug > "${EVIDENCE_DIR}/gradle-assembleDebug.txt" 2>&1 || fail "Gradle assembleDebug failed; see gradle-assembleDebug.txt"
  else
    printf 'existing debug APK found\n' > "${EVIDENCE_DIR}/gradle-assembleDebug.txt"
  fi
  [[ -f "${APK_PATH}" ]] || fail "debug APK was not produced at ${APK_PATH}"
  if command -v sha256sum >/dev/null 2>&1; then
    APK_SHA256="$(sha256sum "${APK_PATH}" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    APK_SHA256="$(shasum -a 256 "${APK_PATH}" | awk '{print $1}')"
  else
    fail "cannot record APK SHA-256; neither sha256sum nor shasum is available"
  fi
  {
    printf 'apk_path=%s\n' "${APK_PATH}"
    printf 'apk_sha256=%s\n' "${APK_SHA256}"
  } > "${EVIDENCE_DIR}/apk.txt"
}

verify_root() {
  CURRENT_STEP="verifying root access"
  local output
  output="$(adb_target shell id 2>&1 | tr -d '\r' || true)"
  printf 'adb shell id: %s\n' "${output}" > "${EVIDENCE_DIR}/root.txt"
  if [[ "${output}" == *uid=0* ]]; then
    ROOT_MODE="adb-shell"
    printf 'root_mode=%s\n' "${ROOT_MODE}" >> "${EVIDENCE_DIR}/root.txt"
    return
  fi

  output="$(adb_target shell su -c id 2>&1 | tr -d '\r' || true)"
  printf 'adb shell su -c id: %s\n' "${output}" >> "${EVIDENCE_DIR}/root.txt"
  if [[ "${output}" == *uid=0* ]]; then
    ROOT_MODE="su"
    printf 'root_mode=%s\n' "${ROOT_MODE}" >> "${EVIDENCE_DIR}/root.txt"
    return
  fi

  fail "root unavailable; rooted LSPosed smoke requires adb shell id or su -c id to report uid=0"
}

verify_lsposed() {
  CURRENT_STEP="detecting LSPosed"
  local packages module_dirs
  packages="$(shell_out pm list packages 2>&1 || true)"
  module_dirs="$(root_shell 'for path in /data/adb/modules/zygisk_lsposed /data/adb/modules/riru_lsposed /data/adb/lspd /data/adb/modules/lsposed; do [ -e "$path" ] && echo "$path"; done' 2>&1 | tr -d '\r' || true)"
  {
    printf 'lsposed_packages:\n'
    printf '%s\n' "${packages}" | awk 'tolower($0) ~ /lsposed|lspd/ { print }'
    printf 'lsposed_root_paths:\n%s\n' "${module_dirs}"
  } > "${EVIDENCE_DIR}/lsposed.txt"

  if ! awk 'tolower($0) ~ /lsposed|lspd/ { found=1 } END { exit found ? 0 : 1 }' "${EVIDENCE_DIR}/lsposed.txt"; then
    fail "LSPosed not detectable; checked manager packages and root-visible module directories"
  fi
}

verify_systemui_package() {
  CURRENT_STEP="checking SystemUI package"
  shell_out pm path "${SYSTEMUI_PACKAGE}" > "${EVIDENCE_DIR}/systemui-package.txt" 2>&1 || fail "pm path ${SYSTEMUI_PACKAGE} failed; see systemui-package.txt"
  if ! awk '/^package:/ { found=1 } END { exit found ? 0 : 1 }' "${EVIDENCE_DIR}/systemui-package.txt"; then
    fail "${SYSTEMUI_PACKAGE} package path was not found"
  fi
}

install_apk() {
  CURRENT_STEP="installing debug APK"
  adb_target install -r "${APK_PATH}" > "${EVIDENCE_DIR}/install.txt" 2>&1 || fail "adb install -r failed; see install.txt"
  shell_out pm path "${APP_PACKAGE}" > "${EVIDENCE_DIR}/app-package.txt" 2>&1 || fail "installed app package ${APP_PACKAGE} was not found"
}

start_local_provider() {
  CURRENT_STEP="starting local fake provider"
  command -v python3 > "${EVIDENCE_DIR}/python3-path.txt" 2>&1 || fail "python3 is required to run the local fake provider"
  local server_script="${EVIDENCE_DIR}/local-provider.py"
  cat > "${server_script}" <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import sys
import time
from urllib.parse import parse_qs, urlparse

default_symbol = sys.argv[1] if len(sys.argv) > 1 else "BTC"

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        query = parse_qs(urlparse(self.path).query)
        symbol = (query.get("symbol") or [default_symbol])[0]
        body = json.dumps({
            "price": 123.45,
            "symbol": symbol,
            "currency": "$",
            "timestamp": int(time.time()),
        }).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        sys.stdout.write((fmt % args) + "\n")
        sys.stdout.flush()

class Server(HTTPServer):
    allow_reuse_address = True

Server(("127.0.0.1", 18080), Handler).serve_forever()
PY
  python3 "${server_script}" "${SYMBOL}" > "${EVIDENCE_DIR}/local-provider.log" 2>&1 &
  SERVER_PID=$!

  local attempt
  for attempt in {1..20}; do
    if python3 - "${SYMBOL}" <<'PY' >/dev/null 2>&1
import json
import sys
from urllib.request import urlopen

symbol = sys.argv[1]
with urlopen(f"http://127.0.0.1:18080/quote?symbol={symbol}", timeout=1) as response:
    payload = json.loads(response.read().decode("utf-8"))
if "price" not in payload:
    raise SystemExit(1)
PY
    then
      printf 'local provider ready on 127.0.0.1:18080\n' > "${EVIDENCE_DIR}/local-provider-ready.txt"
      return
    fi
    sleep 0.25
  done
  fail "local fake provider did not return price JSON on 127.0.0.1:18080; see local-provider.log"
}

configure_adb_reverse() {
  CURRENT_STEP="configuring adb reverse for local provider"
  adb_target reverse tcp:18080 tcp:18080 > "${EVIDENCE_DIR}/adb-reverse.txt" 2>&1 || fail "adb reverse tcp:18080 tcp:18080 failed; see adb-reverse.txt"
}

xml_escape() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</&lt;}"
  value="${value//>/&gt;}"
  value="${value//\"/&quot;}"
  value="${value//\'/&apos;}"
  printf '%s' "${value}"
}

write_debug_config() {
  CURRENT_STEP="configuring app debug storage"
  local timestamp_millis symbol_xml url_xml config_xml
  timestamp_millis="$(( $(date +%s) * 1000 ))"
  symbol_xml="$(xml_escape "${SYMBOL}")"
  url_xml="$(xml_escape 'http://127.0.0.1:18080/quote?symbol={symbol}')"
  config_xml="${EVIDENCE_DIR}/${PREFS_NAME}.xml"

  cat > "${config_xml}" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <boolean name="enabled" value="true" />
  <string name="provider">CUSTOM_JSON</string>
  <string name="symbol">${symbol_xml}</string>
  <string name="apiKey"></string>
  <int name="refreshIntervalSeconds" value="120" />
  <int name="timeoutMillis" value="3000" />
  <string name="customUrlTemplate">${url_xml}</string>
  <string name="customJsonPathPrice">$.price</string>
  <string name="customJsonPathSymbol">$.symbol</string>
  <string name="customJsonPathCurrency">$.currency</string>
  <string name="customJsonPathTimestamp">$.timestamp</string>
  <boolean name="cache.hasQuote" value="true" />
  <string name="cache.symbol">${symbol_xml}</string>
  <long name="cache.price" value="${PRICE_DOUBLE_BITS}" />
  <string name="cache.currency">$</string>
  <long name="cache.timestampMillis" value="${timestamp_millis}" />
  <long name="cache.fetchedAtMillis" value="${timestamp_millis}" />
</map>
XML

  if ! adb_target shell run-as "${APP_PACKAGE}" sh -c "mkdir -p shared_prefs && cat > shared_prefs/${PREFS_NAME}.xml && chmod 660 shared_prefs/${PREFS_NAME}.xml" < "${config_xml}" > "${EVIDENCE_DIR}/configure-app.txt" 2>&1; then
    fail "unable to configure ${APP_PACKAGE} via run-as debug SharedPreferences; see configure-app.txt"
  fi
  adb_target shell run-as "${APP_PACKAGE}" ls -l "shared_prefs/${PREFS_NAME}.xml" >> "${EVIDENCE_DIR}/configure-app.txt" 2>&1 || fail "configured SharedPreferences file was not readable through run-as"
}

clear_logcat() {
  CURRENT_STEP="clearing logcat"
  adb_target logcat -c > "${EVIDENCE_DIR}/logcat-clear.txt" 2>&1 || fail "adb logcat -c failed; see logcat-clear.txt"
}

start_logcat_capture() {
  CURRENT_STEP="starting logcat capture"
  adb_target logcat -v time > "${EVIDENCE_DIR}/logcat.txt" 2>&1 &
  LOGCAT_PID=$!
  sleep 1
  if ! kill -0 "${LOGCAT_PID}" 2>/dev/null; then
    fail "adb logcat capture did not stay running; see logcat.txt"
  fi
}

restart_systemui() {
  CURRENT_STEP="restarting SystemUI"
  local before_pid after_pid
  before_pid="$(shell_out pidof "${SYSTEMUI_PACKAGE}" 2>/dev/null || true)"
  {
    printf 'before_pid=%s\n' "${before_pid}"
    printf 'restart_command=kill SystemUI pid through %s\n' "${ROOT_MODE}"
  } > "${EVIDENCE_DIR}/systemui-restart.txt"
  if [[ -z "${before_pid}" ]]; then
    fail "${SYSTEMUI_PACKAGE} process is not running before restart"
  fi
  root_shell "kill ${before_pid}" >> "${EVIDENCE_DIR}/systemui-restart.txt" 2>&1 || fail "failed to kill ${SYSTEMUI_PACKAGE}; see systemui-restart.txt"

  local attempt
  for attempt in {1..30}; do
    after_pid="$(shell_out pidof "${SYSTEMUI_PACKAGE}" 2>/dev/null || true)"
    if [[ -n "${after_pid}" && "${after_pid}" != "${before_pid}" ]]; then
      printf 'after_pid=%s\n' "${after_pid}" >> "${EVIDENCE_DIR}/systemui-restart.txt"
      sleep 5
      return
    fi
    sleep 1
  done
  fail "${SYSTEMUI_PACKAGE} did not restart with a new pid; see systemui-restart.txt"
}

capture_screenshot() {
  CURRENT_STEP="capturing screenshot"
  adb_target exec-out screencap -p > "${EVIDENCE_DIR}/screenshot.png" 2> "${EVIDENCE_DIR}/screenshot.err" || fail "screenshot capture failed; see screenshot.err"
  [[ -s "${EVIDENCE_DIR}/screenshot.png" ]] || fail "screenshot.png is empty after screencap"
}

stop_logcat_capture() {
  CURRENT_STEP="stopping logcat capture"
  if [[ -n "${LOGCAT_PID}" ]]; then
    kill "${LOGCAT_PID}" 2>/dev/null || true
    wait "${LOGCAT_PID}" 2>/dev/null || true
    LOGCAT_PID=""
  fi
}

analyze_logcat() {
  CURRENT_STEP="analyzing logcat markers"
  if grep -F 'controller skipped unsupported clock target' "${EVIDENCE_DIR}/logcat.txt" > "${EVIDENCE_DIR}/priceontop-markers.txt" 2>/dev/null; then
    SMOKE_STATUS="PASS_UNSUPPORTED"
    SMOKE_MESSAGE="PriceOnTop fail-closed unsupported clock target for ROM family ${ROM_FAMILY}; screenshot and logcat evidence captured"
    return
  fi
  if ! grep -E 'PriceOnTop|systemui-clock-hook-installed|systemui-scope-accepted|systemui-entry-ready' "${EVIDENCE_DIR}/logcat.txt" > "${EVIDENCE_DIR}/priceontop-markers.txt" 2>/dev/null; then
    fail "no PriceOnTop log marker after SystemUI restart; verify LSPosed is active and the module is enabled for com.android.systemui"
  fi
  if ! grep -F 'systemui-clock-hook-installed' "${EVIDENCE_DIR}/priceontop-markers.txt" >/dev/null 2>&1; then
    fail "PriceOnTop module loaded but no clock hook installation marker was observed; see priceontop-markers.txt"
  fi
}

main() {
  parse_args "$@"
  resolve_evidence_dir
  CURRENT_STEP="starting smoke flow"
  write_summary "RUNNING" "rooted LSPosed smoke started"

  require_adb
  select_device
  record_device_info
  ensure_apk
  verify_root
  verify_lsposed
  verify_systemui_package
  install_apk
  start_local_provider
  configure_adb_reverse
  write_debug_config
  clear_logcat
  start_logcat_capture
  restart_systemui
  capture_screenshot
  sleep 3
  stop_logcat_capture
  analyze_logcat

  write_summary "${SMOKE_STATUS}" "${SMOKE_MESSAGE}"
  SUMMARY_WRITTEN="true"
  printf 'Smoke outcome %s. Evidence written to %s\n' "${SMOKE_STATUS}" "${EVIDENCE_DIR}"
}

main "$@"
