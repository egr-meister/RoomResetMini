#!/usr/bin/env bash
# Verifies a signed release APK + AAB pair.
# Usage: scripts/verify-release.sh <apk> <aab> <out-dir> <label>
# Requires ANDROID_HOME and BUILD_TOOLS (e.g. 36.0.0). Never prints secrets.
set -euo pipefail

APK="$1"
AAB="$2"
OUT="$3"
LABEL="$4"
BT="$ANDROID_HOME/build-tools/$BUILD_TOOLS"
mkdir -p "$OUT"

fail() { echo "::error::[$LABEL] $*"; exit 1; }

test -f "$APK" || fail "APK not found: $APK"
test -f "$AAB" || fail "AAB not found: $AAB"

echo "== [$LABEL] APK signature (apksigner verify --print-certs)"
if ! "$BT/apksigner" verify --verbose --print-certs "$APK" > "$OUT/apk-signature.txt" 2>&1; then
  cat "$OUT/apk-signature.txt"
  fail "apksigner verification failed"
fi
cat "$OUT/apk-signature.txt"
grep -q "CN=Android Debug" "$OUT/apk-signature.txt" && fail "APK is signed with the Android Debug certificate"
grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" "$OUT/apk-signature.txt" || fail "APK is not v2-signed"
APK_SHA=$(grep -m1 "certificate SHA-256 digest" "$OUT/apk-signature.txt" | awk -F': ' '{print toupper($2)}' | tr -d ': ')
test -n "$APK_SHA" || fail "Could not read the APK signer digest"
# A self-signed upload certificate is valid for Google Play and is NOT rejected here.

echo "== [$LABEL] AAB integrity and signer (jarsigner)"
# No -strict: bundletool AABs trigger the harmless JDK warning
# "signed in JarFile but is not signed in JarInputStream" (entry ordering), which -strict turns into a failure.
if ! jarsigner -verify -verbose -certs "$AAB" > "$OUT/aab-verify.txt" 2>&1; then
  tail -40 "$OUT/aab-verify.txt"
  fail "jarsigner could not verify the AAB"
fi
grep -q "jar verified" "$OUT/aab-verify.txt" || { tail -40 "$OUT/aab-verify.txt"; fail "AAB signature not verified"; }
if grep -qE "jar is unsigned|unsigned entries|digest error|SecurityException" "$OUT/aab-verify.txt"; then
  tail -40 "$OUT/aab-verify.txt"
  fail "AAB has unsigned or tampered entries"
fi
grep -q "CN=Android Debug" "$OUT/aab-verify.txt" && fail "AAB is signed with the Android Debug certificate"
AAB_SHA=$(keytool -printcert -jarfile "$AAB" | grep -m1 "SHA256:" | sed 's/.*SHA256: //' | tr -d ': ' | tr 'a-f' 'A-F')
test -n "$AAB_SHA" || fail "Could not read the AAB signer digest"
echo "APK signer SHA-256: $APK_SHA"
echo "AAB signer SHA-256: $AAB_SHA"
[ "$APK_SHA" = "$AAB_SHA" ] || fail "APK and AAB are signed by different certificates"
if [ -n "${EXPECTED_SIGNER_SHA256:-}" ]; then
  EXPECTED=$(printf '%s' "$EXPECTED_SIGNER_SHA256" | tr -d ': ' | tr 'a-f' 'A-F')
  [ "$APK_SHA" = "$EXPECTED" ] || fail "Signer does not match the expected upload certificate"
fi
echo "$APK_SHA" > "$OUT/signer-sha256.txt"

echo "== [$LABEL] Packaged permissions (must be none)"
"$BT/aapt2" dump permissions "$APK" > "$OUT/packaged-permissions.txt"
cat "$OUT/packaged-permissions.txt"
PKG=$("$BT/aapt2" dump packagename "$APK")
ALLOWED="${PKG}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
USED=$(grep -oE "uses-permission: name='[^']+'" "$OUT/packaged-permissions.txt" | sed "s/uses-permission: name='//;s/'//" || true)
for p in $USED; do
  [ "$p" = "$ALLOWED" ] || fail "Unexpected permission in APK: $p"
done
for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE android.permission.CAMERA \
  android.permission.RECORD_AUDIO android.permission.ACCESS_FINE_LOCATION android.permission.POST_NOTIFICATIONS; do
  grep -q "$forbidden" "$OUT/packaged-permissions.txt" && fail "Forbidden permission present: $forbidden"
done
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK" > "$OUT/packaged-manifest.txt"
grep -qE 'android:allowBackup.*=false|allowBackup\(0x[0-9a-f]+\)=false' "$OUT/packaged-manifest.txt" || fail "allowBackup is not false"
grep -q 'android:debuggable' "$OUT/packaged-manifest.txt" && grep -qE 'debuggable.*=(true|0xffffffff)' "$OUT/packaged-manifest.txt" && fail "Release APK is debuggable"

echo "== [$LABEL] Native libraries / 16 KB page size"
SO_APK=$(unzip -Z1 "$APK" | grep -E '\.so$' || true)
SO_AAB=$(unzip -Z1 "$AAB" | grep -E '\.so$' || true)
{
  echo "## 16 KB page size findings ($LABEL)"
  if [ -z "$SO_APK$SO_AAB" ]; then
    echo "No native .so libraries in the release APK or AAB (including transitive dependencies)."
    echo "ELF 16 KB segment alignment therefore does not apply; APK zip alignment verified with zipalign -P 16."
  else
    echo "Native libraries found:"
    printf '%s\n' "$SO_APK" "$SO_AAB" | sed '/^$/d'
  fi
} > "$OUT/native-libs.md"
if ! "$BT/zipalign" -c -P 16 -v 4 "$APK" > "$OUT/zipalign.txt" 2>&1; then
  tail -40 "$OUT/zipalign.txt"
  fail "zipalign 16 KB check failed"
fi
if [ -n "$SO_APK" ]; then
  TMP_SO=$(mktemp -d)
  unzip -q -o "$APK" 'lib/*' -d "$TMP_SO"
  while IFS= read -r f; do
    ALIGN=$(objdump -p "$f" | awk '/LOAD/ {print $NF}' | sort -u | tr '\n' ' ')
    echo "- $f LOAD align: $ALIGN" >> "$OUT/native-libs.md"
    echo "$ALIGN" | grep -qE '2\*\*(1[4-9]|[2-9][0-9])' || fail "$f is not 16 KB aligned"
  done < <(find "$TMP_SO" -name '*.so')
  rm -rf "$TMP_SO"
fi
cat "$OUT/native-libs.md"

echo "== [$LABEL] Sizes"
{
  echo "## Release artifacts ($LABEL)"
  echo "- APK: $(stat -c %s "$APK") bytes ($(du -h "$APK" | cut -f1))"
  echo "- AAB: $(stat -c %s "$AAB") bytes ($(du -h "$AAB" | cut -f1))"
  echo "- Signer SHA-256: $APK_SHA"
} > "$OUT/report.md"
cat "$OUT/native-libs.md" >> "$OUT/report.md"
cat "$OUT/report.md"
echo "[$LABEL] all release checks passed"
