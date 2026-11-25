#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TOOL="$PWD/android-toolchain/build-tools/android-15"
ANDROID_JAR="$PWD/android-toolchain/platform/android-35/android.jar"
ECJ="$PWD/android-toolchain/ecj.jar"
mkdir -p build deliverables
WORK="$(mktemp -d "$PWD/build/compile-XXXXXX")"
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/src/main/AndroidManifest.xml)"
APK="deliverables/ResetNotice-v${VERSION}.apk"
if [[ -n "${RESETNOTICE_KEYSTORE:-}" ]]; then
    [[ -f "$RESETNOTICE_KEYSTORE" ]] || { echo 'Release keystore not found.' >&2; exit 1; }
    : "${RESETNOTICE_KS_PASS:?Set RESETNOTICE_KS_PASS for release signing}"
    KEYSTORE="$RESETNOTICE_KEYSTORE"
    KEY_ALIAS="${RESETNOTICE_KEY_ALIAS:-resetnotice}"
    export RESETNOTICE_KS_PASS
    export RESETNOTICE_KEY_PASS="${RESETNOTICE_KEY_PASS:-$RESETNOTICE_KS_PASS}"
else
    # Local development signature only; never use it to distribute official updates.
    APK="deliverables/ResetNotice-v${VERSION}-local.apk"
    KEYSTORE="build/local-debug.jks"
    KEY_ALIAS="resetnotice-local"
    export RESETNOTICE_KS_PASS=android
    export RESETNOTICE_KEY_PASS=android
    if [[ ! -f "$KEYSTORE" ]]; then
        keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias "$KEY_ALIAS" -dname 'CN=ResetNotice Local Development' -keyalg RSA -keysize 2048 -validity 10000 -storetype JKS
    fi
fi
mkdir -p "$WORK"/{generated,classes,dex,tests}
"$TOOL/aapt2" compile --dir app/src/main/res -o "$WORK/res.zip"
"$TOOL/aapt2" link -I "$ANDROID_JAR" --manifest app/src/main/AndroidManifest.xml -o "$WORK/resources.apk" --java "$WORK/generated" "$WORK/res.zip"
mapfile -t sources < <(find app/src/main/java "$WORK/generated" -name '*.java' ! -name '*Test.java' -print)
java -jar "$ECJ" -1.8 -proc:none -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$WORK/classes" "${sources[@]}"
mapfile -t tests < <(find tests -name '*.java' -print)
java -jar "$ECJ" -1.8 -proc:none -encoding UTF-8 -d "$WORK/tests" app/src/main/java/com/vlan/resetnotice/{ResetMath,ResetMathTest,ReminderStore,AlarmIdentity,TimerSnapshot,BrandMatcher,AccountOrder,AlarmPolicy,PushVendor,QuietHours,WidgetBindingStore,WidgetMath,HistoryStore,EdgeMotion}.java "${tests[@]}"
java -cp "$WORK/tests" com.vlan.resetnotice.ResetMathTest
java -cp "$WORK/tests" com.vlan.resetnotice.ProfileStoreTest
mapfile -t classes < <(find "$WORK/classes" -name '*.class' -print)
"$TOOL/d8" --release --min-api 23 --lib "$ANDROID_JAR" --output "$WORK/dex" "${classes[@]}"
cp "$WORK/resources.apk" "$WORK/unsigned.apk"
zip -q -j "$WORK/unsigned.apk" "$WORK/dex/classes.dex"
"$TOOL/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
"$TOOL/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:RESETNOTICE_KS_PASS --key-pass env:RESETNOTICE_KEY_PASS --out "$APK" "$WORK/aligned.apk"
"$TOOL/apksigner" verify --verbose --print-certs "$APK"
"$TOOL/zipalign" -c -p 4 "$APK"
"$TOOL/aapt2" dump badging "$APK"
ls -lh "$APK"
