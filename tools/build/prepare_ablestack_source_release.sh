#!/usr/bin/env bash

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

usage() {
    cat <<'USAGE'
Usage: prepare_ablestack_source_release.sh --release-id string --branch string --outputdir path [OPTIONS]...

Prepare an ABLESTACK-flavored source release using the build_asf.sh ceremony:
- update source tree version to the requested release identifier
- create an RC branch
- optionally create a release tag
- generate ablestack-cloud source archives and checksums

Mandatory arguments:
  --release-id string              Release identifier such as ABLESTACK-4.6.1
  --branch string                  Base branch to release from
  --outputdir path                 Directory where release artifacts will be written

Optional arguments:
  --sourcedir path                 Repository root (default: current repo root)
  --timestamp string               Fixed timestamp in YYYYMMDDHHMM format
  --archive-prefix string          Source archive prefix (default: ablestack-cloud)
  --tag                            Create an annotated or signed git tag
  --sign-artifacts                 Create an ASCII-armored signature for the tar.bz2 artifact
  --sign-tag                       Create a signed git tag instead of an annotated tag
  --gpg-key-id string              GPG key id/fingerprint used for signing
  -h, --help                       Show this help text
USAGE
}

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd -P)
SOURCE_DIR=$(cd "$SCRIPT_DIR/../.." && pwd -P)
RELEASE_ID=""
BASE_BRANCH=""
OUTPUT_DIR=""
TIMESTAMP_VALUE=""
ARCHIVE_PREFIX="ablestack-cloud"
CREATE_TAG="false"
SIGN_ARTIFACTS="false"
SIGN_TAG="false"
GPG_KEY_ID=""
GPG_ARGS=()

while [ -n "${1:-}" ]; do
    case "$1" in
        --release-id)
            RELEASE_ID=$2
            shift 2
            ;;
        --branch)
            BASE_BRANCH=$2
            shift 2
            ;;
        --outputdir)
            OUTPUT_DIR=$2
            shift 2
            ;;
        --sourcedir)
            SOURCE_DIR=$2
            shift 2
            ;;
        --timestamp)
            TIMESTAMP_VALUE=$2
            shift 2
            ;;
        --archive-prefix)
            ARCHIVE_PREFIX=$2
            shift 2
            ;;
        --tag)
            CREATE_TAG="true"
            shift 1
            ;;
        --sign-artifacts)
            SIGN_ARTIFACTS="true"
            shift 1
            ;;
        --sign-tag)
            SIGN_TAG="true"
            shift 1
            ;;
        --gpg-key-id)
            GPG_KEY_ID=$2
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [ -z "$RELEASE_ID" ] || [ -z "$BASE_BRANCH" ] || [ -z "$OUTPUT_DIR" ]; then
    usage >&2
    exit 1
fi

if [ -n "$TIMESTAMP_VALUE" ] && ! echo "$TIMESTAMP_VALUE" | grep -Eq '^[0-9]{12}$'; then
    echo "ERROR: --timestamp must be in YYYYMMDDHHMM format" >&2
    exit 1
fi

if [ -z "$TIMESTAMP_VALUE" ]; then
    TIMESTAMP_VALUE=$(date -u +%Y%m%d%H%M)
fi

if [ -n "${GPG_PASSPHRASE:-}" ]; then
    GPG_ARGS=(--batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE")
fi

RC_TIMESTAMP="${TIMESTAMP_VALUE:0:8}T${TIMESTAMP_VALUE:8:4}"
RC_BRANCH="${RELEASE_ID}-RC${RC_TIMESTAMP}"
ARCHIVE_BASE="${ARCHIVE_PREFIX}-${RELEASE_ID}-src"
TAR_PATH="${OUTPUT_DIR}/${ARCHIVE_BASE}.tar"
TARBZ2_PATH="${TAR_PATH}.bz2"
ZIP_PATH="${OUTPUT_DIR}/${ARCHIVE_BASE}.zip"
ASC_PATH="${TARBZ2_PATH}.asc"
SHA512_PATH="${TARBZ2_PATH}.sha512"
METADATA_PATH="${OUTPUT_DIR}/release-metadata.env"

cd "$SOURCE_DIR"

if [ -n "$(git status --short)" ]; then
    echo "ERROR: repository must be clean before preparing a source release" >&2
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: mvn is required to prepare the source release" >&2
    exit 1
fi

if ! git checkout "$BASE_BRANCH" >/dev/null 2>&1; then
    git checkout -B "$BASE_BRANCH" "origin/$BASE_BRANCH"
fi

CURRENT_VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version 2>/dev/null | tail -1)
if [ -z "$CURRENT_VERSION" ]; then
    CURRENT_VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\[' | tail -1)
fi

echo "Using release id       : $RELEASE_ID"
echo "Using source directory : $SOURCE_DIR"
echo "Using branch           : $BASE_BRANCH"
echo "Using current version  : $CURRENT_VERSION"
echo "Using RC branch        : $RC_BRANCH"
echo "Using output directory : $OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_DIR"/"${ARCHIVE_PREFIX}"-* "$OUTPUT_DIR"/release-metadata.env

echo "Setting source release version"
mvn versions:set -DnewVersion="$RELEASE_ID" -P developer,systemvm -Dnoredist -Dsimulator
mvn versions:set -DnewVersion="$RELEASE_ID" -pl tools/checkstyle

if [ -f deps/XenServerJava/pom.xml.versionsBackup ]; then
    mv deps/XenServerJava/pom.xml.versionsBackup deps/XenServerJava/pom.xml
fi

perl -pi -e "s/<cs.xapi.version>6.2.0-1-SNAPSHOT<\/cs.xapi.version>/<cs.xapi.version>6.2.0-1<\/cs.xapi.version>/" pom.xml
perl -pi -e 's/-SNAPSHOT//' deps/XenServerJava/pom.xml tools/apidoc/pom.xml build/replace.properties tools/marvin/setup.py tools/marvin/marvin/deployAndRun.py tools/docker/Dockerfile tools/docker/Dockerfile.marvin tools/docker/Dockerfile.centos6

if echo "$CURRENT_VERSION" | grep -q -- '-SNAPSHOT'; then
    perl -pi -e 's/-SNAPSHOT//' debian/rules
fi

tmpfilenm=$$.tmp
{
    echo "cloudstack ($RELEASE_ID) unstable; urgency=low"
    echo
    echo "  * Update the version to $RELEASE_ID"
    echo
    echo " -- the ABLESTACK Cloud project <dev@ablestack.io>  $(date -u '+%a, %d %b %Y %T %z')"
    echo
    cat debian/changelog
} >"$tmpfilenm"
mv "$tmpfilenm" debian/changelog

git clean -f

if git show-ref --verify --quiet "refs/heads/$RC_BRANCH"; then
    echo "ERROR: RC branch $RC_BRANCH already exists" >&2
    exit 1
fi

git checkout -b "$RC_BRANCH"
git commit -a -s -m "Updating pom.xml version numbers for release $RELEASE_ID"
COMMIT_SHA=$(git rev-parse HEAD)

echo "Creating source archives"
git archive --format=tar --prefix="${ARCHIVE_BASE}/" "$RC_BRANCH" > "$TAR_PATH"
bzip2 -f "$TAR_PATH"
git archive --format=zip --prefix="${ARCHIVE_BASE}/" "$RC_BRANCH" > "$ZIP_PATH"
sha512sum "$TARBZ2_PATH" > "$SHA512_PATH"

if [ "$SIGN_ARTIFACTS" = "true" ]; then
    echo "Signing source archive"
    if [ -n "$GPG_KEY_ID" ]; then
        gpg "${GPG_ARGS[@]}" -v --default-key "$GPG_KEY_ID" --armor --output "$ASC_PATH" --detach-sig "$TARBZ2_PATH"
    else
        gpg "${GPG_ARGS[@]}" -v --armor --output "$ASC_PATH" --detach-sig "$TARBZ2_PATH"
    fi
    gpg "${GPG_ARGS[@]}" -v --verify "$ASC_PATH" "$TARBZ2_PATH"
fi

if [ "$CREATE_TAG" = "true" ]; then
    if git show-ref --verify --quiet "refs/tags/$RELEASE_ID"; then
        echo "ERROR: release tag $RELEASE_ID already exists" >&2
        exit 1
    fi

    echo "Creating release tag"
    if [ "$SIGN_TAG" = "true" ]; then
        if [ -n "$GPG_KEY_ID" ]; then
            git tag -u "$GPG_KEY_ID" -s "$RELEASE_ID" -m "Tagging release $RELEASE_ID on branch $BASE_BRANCH."
        else
            git tag -s "$RELEASE_ID" -m "Tagging release $RELEASE_ID on branch $BASE_BRANCH."
        fi
    else
        git tag -a "$RELEASE_ID" -m "Tagging release $RELEASE_ID on branch $BASE_BRANCH."
    fi
fi

cat >"$METADATA_PATH" <<EOF
RELEASE_ID=$RELEASE_ID
BASE_BRANCH=$BASE_BRANCH
RC_BRANCH=$RC_BRANCH
TIMESTAMP_VALUE=$TIMESTAMP_VALUE
SOURCE_COMMIT_SHA=$COMMIT_SHA
ARCHIVE_BASE=$ARCHIVE_BASE
SOURCE_TARBZ2=$(basename "$TARBZ2_PATH")
SOURCE_ZIP=$(basename "$ZIP_PATH")
SOURCE_ASC=$(basename "$ASC_PATH")
SOURCE_SHA512=$(basename "$SHA512_PATH")
EOF

if [ -n "${GITHUB_OUTPUT:-}" ]; then
    {
        echo "release_id=$RELEASE_ID"
        echo "rc_branch=$RC_BRANCH"
        echo "timestamp_value=$TIMESTAMP_VALUE"
        echo "source_commit_sha=$COMMIT_SHA"
        echo "source_tarball=$TARBZ2_PATH"
        echo "source_zip=$ZIP_PATH"
        echo "source_signature=$ASC_PATH"
        echo "source_sha512=$SHA512_PATH"
    } >> "$GITHUB_OUTPUT"
fi

echo "Prepared source release commit $COMMIT_SHA"
