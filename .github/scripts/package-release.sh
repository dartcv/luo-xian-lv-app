set -euo pipefail

key="$RUNNER_TEMP/release.jks"
trap 'rm -f "$key"' EXIT
umask 077
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$key"
export ORG_GRADLE_PROJECT_releaseStoreFile="$key"
build_args=()
if [ -n "${UPDATE_BASE_URL:-}" ]; then
  build_args+=("-PupdateBaseUrl=$UPDATE_BASE_URL")
fi
bash ./gradlew testDebugUnitTest assembleRelease --no-daemon "${build_args[@]}"

apk=app/build/outputs/apk/release/app-release.apk
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)
aapt=$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)
signature=$("$apksigner" verify --verbose --print-certs "$apk")
certificate=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$signature")
test "$certificate" = "$ANDROID_SIGNING_CERT_SHA256"
test -s app/build/outputs/mapping/release/mapping.txt
badging=$("$aapt" dump badging "$apk")
code=$(sed -nE "s/^package: .*versionCode='([0-9]+)'.*/\1/p" <<< "$badging")
version=$(sed -nE "s/^package: .*versionName='([^']+)'.*/\1/p" <<< "$badging")
[[ "$code" =~ ^[1-9][0-9]*$ ]]
test "v$version" = "$RELEASE_TAG"

mkdir -p dist
asset="luoxianlv-${RELEASE_TAG}-release.apk"
cp "$apk" "dist/$asset"
(cd dist && sha256sum "$asset" > "$asset.sha256")
jq -n --arg name "$version" --argjson code "$code" --arg asset "$asset" \
  '{versionName:$name, versionCode:$code, asset:$asset}' > dist/package.json
printf 'Packaged %s (versionCode %s)\n' "$RELEASE_TAG" "$code" >> "$GITHUB_STEP_SUMMARY"
