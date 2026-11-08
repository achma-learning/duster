#!/usr/bin/env bash
# Build Dacia Clock without Gradle: aapt -> javac (Java 8) -> d8 -> zipalign -> apksigner.
# Usage: ./build.sh            (from Git Bash on Windows, or bash on Linux with paths adjusted)
# Output: ../apk/DaciaClock-v17.apk
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"   # pwd -W: E:/... form for Windows tools
ROOT="$(cd "$HERE/.." && (pwd -W 2>/dev/null || pwd))"
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot}"
SDK="${ANDROID_SDK:-C:/Users/$USERNAME/AppData/Local/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-23/android.jar"
KEYDIR="$ROOT/keys"; KS="$KEYDIR/dacia-v17.jks"; KSPASS="$KEYDIR/dacia-v17.pass"
OUT="$HERE/build"; APK="$ROOT/apk/DaciaClock-v17.apk"
export MSYS_NO_PATHCONV=1

J() { "$JAVA_HOME/bin/$1" "${@:2}"; }

# Signing key (created once, never committed: keys/ is git-ignored via *.jks / *.pass)
mkdir -p "$KEYDIR"
if [ ! -f "$KS" ]; then
  head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24 > "$KSPASS"
  J keytool -genkeypair -keystore "$KS" -storepass "$(cat "$KSPASS")" -keypass "$(cat "$KSPASS")" \
    -alias dacia -keyalg RSA -keysize 2048 -validity 12000 \
    -dname "CN=DaciaClock, OU=Personal, O=Personal, L=Marrakech, C=MA"
  echo "new signing key: $KS (password in $KSPASS) - BACK BOTH UP"
fi

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt (resources + manifest)"
"$BT/aapt.exe" package -f -m -M "$HERE/AndroidManifest.xml" -S "$HERE/res" -A "$HERE/assets" \
  -I "$JAR" -J "$OUT/gen" -F "$OUT/unsigned.apk"

echo "== javac (Java 8)"
J javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" -d "$OUT/classes" \
  $(find "$HERE/src" "$OUT/gen" -name '*.java')

echo "== d8"
J java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 21 --lib "$JAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "== package + align + sign"
( cd "$OUT/dex" && "$BT/aapt.exe" add "$OUT/unsigned.apk" classes.dex >/dev/null )
"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
J java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-key-alias dacia --ks-pass "file:$KSPASS" \
  --min-sdk-version 21 --out "$APK" "$OUT/aligned.apk"
J java -jar "$BT/lib/apksigner.jar" verify --min-sdk-version 21 --print-certs "$APK" | head -3
ls -l "$APK"
