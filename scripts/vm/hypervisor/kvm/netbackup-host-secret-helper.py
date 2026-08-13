#!/usr/bin/env python3

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

import argparse
import base64
import hashlib
import hmac
import json
import os
import stat
import sys
from pathlib import Path


SECRET_KEY_FILE = Path(os.environ.get("SECRET_KEY_FILE", "/root/.ssh/ablestack.key"))
SKIP_SECRET_KEY_PERMISSION_VALIDATION = os.environ.get("SKIP_SECRET_KEY_PERMISSION_VALIDATION", "").lower() in ("1", "true", "yes")
PBKDF2_ITERATIONS = 200_000
FORMAT_VERSION = 1
SALT_SIZE = 16
NONCE_SIZE = 16


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def validate_secret_key_file(path: Path) -> None:
    if not path.is_file():
        fail(f"Secret key file not found: {path}")
    if SKIP_SECRET_KEY_PERMISSION_VALIDATION:
        return
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode != 0o600:
        fail(f"Secret key file must have permission 600: {path} (current: {oct(mode)[2:]})")


def read_key_material() -> bytes:
    validate_secret_key_file(SECRET_KEY_FILE)
    return SECRET_KEY_FILE.read_bytes().strip()


def derive_keys(key_material: bytes, salt: bytes) -> tuple[bytes, bytes]:
    derived = hashlib.pbkdf2_hmac("sha256", key_material, salt, PBKDF2_ITERATIONS, dklen=64)
    return derived[:32], derived[32:]


def keystream(enc_key: bytes, nonce: bytes, length: int) -> bytes:
    blocks = []
    counter = 0
    while sum(len(block) for block in blocks) < length:
        counter_bytes = counter.to_bytes(8, "big")
        blocks.append(hmac.new(enc_key, nonce + counter_bytes, hashlib.sha256).digest())
        counter += 1
    return b"".join(blocks)[:length]


def xor_bytes(left: bytes, right: bytes) -> bytes:
    return bytes(a ^ b for a, b in zip(left, right))


def encrypt_secret(output_file: Path, plaintext: bytes) -> None:
    key_material = read_key_material()
    salt = os.urandom(SALT_SIZE)
    nonce = os.urandom(NONCE_SIZE)
    enc_key, mac_key = derive_keys(key_material, salt)
    stream = keystream(enc_key, nonce, len(plaintext))
    ciphertext = xor_bytes(plaintext, stream)
    mac = hmac.new(mac_key, nonce + ciphertext, hashlib.sha256).digest()
    payload = {
        "version": FORMAT_VERSION,
        "iterations": PBKDF2_ITERATIONS,
        "salt": base64.b64encode(salt).decode(),
        "nonce": base64.b64encode(nonce).decode(),
        "ciphertext": base64.b64encode(ciphertext).decode(),
        "mac": base64.b64encode(mac).decode(),
    }
    output_file.write_text(json.dumps(payload), encoding="utf-8")
    output_file.chmod(0o600)


def decrypt_secret(secret_file: Path) -> bytes:
    key_material = read_key_material()
    try:
        payload = json.loads(secret_file.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"Failed to parse encrypted secret file: {exc}")

    if int(payload.get("version", 0)) != FORMAT_VERSION:
        fail(f"Unsupported encrypted secret format version: {payload.get('version')}")

    iterations = int(payload.get("iterations", PBKDF2_ITERATIONS))
    salt = base64.b64decode(payload["salt"])
    nonce = base64.b64decode(payload["nonce"])
    ciphertext = base64.b64decode(payload["ciphertext"])
    mac = base64.b64decode(payload["mac"])

    derived = hashlib.pbkdf2_hmac("sha256", key_material, salt, iterations, dklen=64)
    enc_key, mac_key = derived[:32], derived[32:]
    expected_mac = hmac.new(mac_key, nonce + ciphertext, hashlib.sha256).digest()
    if not hmac.compare_digest(mac, expected_mac):
        fail("Encrypted secret MAC validation failed")

    stream = keystream(enc_key, nonce, len(ciphertext))
    return xor_bytes(ciphertext, stream)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Encrypt/decrypt NetBackup secret files")
    subparsers = parser.add_subparsers(dest="action", required=True)

    encrypt_parser = subparsers.add_parser("encrypt")
    encrypt_parser.add_argument("secret_file")

    decrypt_parser = subparsers.add_parser("decrypt")
    decrypt_parser.add_argument("secret_file")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    secret_file = Path(args.secret_file)
    if args.action == "encrypt":
        plaintext = sys.stdin.buffer.read()
        if not plaintext:
            fail("No secret data provided on stdin for encryption")
        encrypt_secret(secret_file, plaintext)
        return

    if not secret_file.is_file():
        fail(f"Secret file not found: {secret_file}")
    sys.stdout.buffer.write(decrypt_secret(secret_file))


if __name__ == "__main__":
    main()
