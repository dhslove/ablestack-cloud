#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -euo pipefail

SECRET_KEY_FILE="${SECRET_KEY_FILE:-/root/.ssh/ablestack.key}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_HELPER="${SCRIPT_DIR}/netbackup-host-secret-helper.py"

usage() {
  cat <<EOF
Usage:
  $(basename "$0") encrypt <secret_file>
  $(basename "$0") decrypt <secret_file>

Environment:
  SECRET_KEY_FILE               Key file used to decrypt the secret file
EOF
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

file_mode() {
  local target="$1"
  if stat -c '%a' "${target}" >/dev/null 2>&1; then
    stat -c '%a' "${target}"
    return 0
  fi
  stat -f '%OLp' "${target}"
}

validate_secret_key_file() {
  [[ -f "${SECRET_KEY_FILE}" ]] || fail "Secret key file not found: ${SECRET_KEY_FILE}"
  [[ -r "${SECRET_KEY_FILE}" ]] || fail "Secret key file is not readable: ${SECRET_KEY_FILE}"

  local mode
  mode="$(file_mode "${SECRET_KEY_FILE}")"
  [[ "${mode}" == "600" ]] || fail "Secret key file must have permission 600: ${SECRET_KEY_FILE} (current: ${mode})"
}

ACTION="${1:-}"

[[ -f "${PYTHON_HELPER}" ]] || fail "Python helper not found: ${PYTHON_HELPER}"

case "${ACTION}" in
  encrypt)
    shift
    validate_secret_key_file
    exec python3 "${PYTHON_HELPER}" encrypt "${1:-}"
    ;;
  decrypt)
    shift
    validate_secret_key_file
    exec python3 "${PYTHON_HELPER}" decrypt "${1:-}"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
