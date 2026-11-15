#!/usr/bin/env bash
# Builds the APK of the release tag that is checked out and publishes it as a GitHub Release.
# The APK is built here, not in CI, and signed with this machine's Android debug key.
#
# Usage: scripts/release.sh <file with the release notes>
set -euo pipefail

notes="${1:?usage: scripts/release.sh <file with the release notes>}"
notes="$(realpath "$notes")"
cd "$(dirname "$0")/.."

tag="$(git describe --tags --exact-match HEAD)" || { echo "HEAD is not a release tag" >&2; exit 1; }
version="$(sed -n 's/^appVersion=//p' gradle.properties)"
[ "$tag" = "v$version" ] || { echo "tag $tag does not match appVersion $version" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "the working tree has changes" >&2; exit 1; }

# apksigner runs on Java.
PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}"
tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 | sort -V | tail -1)"

./gradlew --console=plain verify :app:assembleRelease

out=build/release
rm -rf "$out"
mkdir -p "$out"
apk="subfeed-browser-$tag.apk"
cp app/build/outputs/apk/release/app-release.apk "$out/$apk"

badging="$("$tools/aapt2" dump badging "$out/$apk")"
field() { sed -n "s/.*$1='\([^']*\)'.*/\1/p" <<<"$badging" | head -1; }
signer="$("$tools/apksigner" verify --print-certs "$out/$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"

(cd "$out" && sha256sum "$apk" > SHA256SUMS)
jq -n \
  --arg version "$version" \
  --argjson versionCode "$(field versionCode)" \
  --arg package "$(field 'package: name')" \
  --arg source_sha "$(git rev-parse HEAD)" \
  --arg apk_sha256 "$(cut -d' ' -f1 "$out/SHA256SUMS")" \
  --arg signer_sha256 "$signer" \
  --argjson minSdk "$(field minSdkVersion)" \
  --argjson targetSdk "$(field targetSdkVersion)" \
  --arg geckoview "$(sed -n 's/^geckoview = "\(.*\)"/\1/p' gradle/libs.versions.toml)" \
  '{version: $version, versionCode: $versionCode, package: $package, source_sha: $source_sha,
    apk_sha256: $apk_sha256, signer_sha256: $signer_sha256, minSdk: $minSdk, targetSdk: $targetSdk,
    geckoview: $geckoview, signing: "Android debug key of the machine that built it"}' \
  > "$out/release-info.json"

gh release create "$tag" "$out/$apk" "$out/SHA256SUMS" "$out/release-info.json" --title "$tag" --notes-file "$notes"
