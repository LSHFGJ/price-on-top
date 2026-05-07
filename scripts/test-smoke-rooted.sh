#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

COMMAND_LOG="${TMP_DIR}/adb-commands.log"
STATE_DIR="${TMP_DIR}/state"
FAKE_BIN="${TMP_DIR}/bin"
EVIDENCE_DIR="${TMP_DIR}/evidence"
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

case "${1:-}" in
  install|reverse|push)
    exit 0
    ;;
  exec-out)
    if [[ "${2:-}" == "screencap" ]]; then
      printf '\211PNG\r\n\032\n'
      exit 0
    fi
    ;;
  logcat)
    if [[ "${2:-}" == "-c" ]]; then
      exit 0
    fi
    printf '01-01 00:00:00.000 I/PriceOnTop: systemui-clock-hook-installed class=fake.Clock\n'
    sleep 30
    exit 0
    ;;
  shell)
    shift
    if [[ "${1:-}" == "id" ]]; then
      printf 'uid=2000(shell) gid=2000(shell)\n'
      exit 0
    fi
    if [[ "${1:-}" == "su" && "${2:-}" == "-c" ]]; then
      command="${3:-}"
      if [[ "${command}" == "id" ]]; then
        printf 'uid=0(root) gid=0(root)\n'
        exit 0
      fi
      if [[ "${command}" == *'for path in '* ]]; then
        printf '/system/bin/sh: syntax error: unexpected do\n' >&2
        exit 2
      fi
      if [[ "${command}" == "[ -e /data/adb/lspd ]" ]]; then
        exit 0
      fi
      if [[ "${command}" == \[\ -e\ * ]]; then
        exit 1
      fi
      if [[ "${command}" == kill* ]]; then
        : > "${STATE_DIR}/systemui-killed"
        exit 0
      fi
      exit 0
    fi
    if [[ "${1:-}" == "pm" && "${2:-}" == "list" && "${3:-}" == "packages" ]]; then
      printf 'package:org.lsposed.manager\npackage:dev.priceontop\n'
      exit 0
    fi
    if [[ "${1:-}" == "pm" && "${2:-}" == "path" ]]; then
      printf 'package:/fake/%s/base.apk\n' "${3:-unknown}"
      exit 0
    fi
    if [[ "${1:-}" == "getprop" ]]; then
      case "${2:-}" in
        ro.product.manufacturer|ro.product.brand) printf 'Google\n' ;;
        ro.product.model) printf 'Pixel Fake\n' ;;
        ro.build.version.sdk) printf '36\n' ;;
        *) printf 'fake\n' ;;
      esac
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
    if [[ "${1:-}" == "run-as" ]]; then
      shift
      package="${1:-}"
      shift || true
      if [[ "${1:-}" == "sh" && "${2:-}" == "-c" && "${3:-}" == *'mkdir -p shared_prefs && cat >'* ]]; then
        printf 'mkdir: Needs 1 argument\n' >&2
        exit 2
      fi
      if [[ "${package}" == "dev.priceontop" ]]; then
        exit 0
      fi
    fi
    ;;
esac

printf 'unexpected fake adb invocation: %q ' "$@" >&2
printf '\n' >&2
exit 1
ADB
chmod +x "${FAKE_BIN}/adb"

export PATH="${FAKE_BIN}:${PATH}"
export COMMAND_LOG STATE_DIR ADB_SERIAL=FAKE123

"${REPO_ROOT}/scripts/smoke-rooted.sh" --provider local --symbol BTC --evidence "${EVIDENCE_DIR}"

if grep -F 'for\ path\ in' "${COMMAND_LOG}" >/dev/null; then
  printf 'verify_lsposed still sends a fragile remote for loop through su -c\n' >&2
  exit 1
fi

if grep -F 'mkdir\ -p\ shared_prefs\ \&\&\ cat\ \>\ shared_prefs' "${COMMAND_LOG}" >/dev/null; then
  printf 'write_debug_config still sends mkdir/cat/chmod as one nested sh -c command\n' >&2
  exit 1
fi

printf 'smoke-rooted command construction tests passed\n'
