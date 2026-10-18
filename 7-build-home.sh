#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Builds the simple home screen app (Home.apk) on this Mac. Does NOT touch the TV.
set -e
D=$HERE; APP="$D/home-app"; T="$D/tools"; mkdir -p "$T"
cd "$T"

# 1) Java (needed by Android's build tools)
if /usr/libexec/java_home -v 17+ >/dev/null 2>&1; then
  export JAVA_HOME=$(/usr/libexec/java_home -v 17+)
else
  if [ ! -x "$T/jdk/Contents/Home/bin/java" ]; then
    echo "Downloading Java (one time, ~190 MB)..."
    curl -fL# -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/mac/$( [ "$(uname -m)" = arm64 ] && echo aarch64 || echo x64 )/jdk/hotspot/normal/eclipse"
    mkdir -p jdk-tmp && tar -xzf jdk.tar.gz -C jdk-tmp && mv jdk-tmp/*/ jdk && rm -rf jdk-tmp jdk.tar.gz
  fi
  export JAVA_HOME="$T/jdk/Contents/Home"
fi
export PATH="$JAVA_HOME/bin:$PATH"

# 2) Android build tools (one time, ~150 MB)
BT="$T/sdk/build-tools/34.0.0"
if [ ! -x "$BT/aapt2" ]; then
  echo "Downloading Android build tools (one time)..."
  curl -fL# -o cmdline.zip https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
  rm -rf sdk/cmdline-tools && mkdir -p sdk/cmdline-tools && unzip -q cmdline.zip -d sdk/cmdline-tools && mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest && rm cmdline.zip
  yes | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$T/sdk" --licenses >/dev/null 2>&1 || true
  sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$T/sdk" "build-tools;34.0.0" | tail -1
fi
if ! "$BT/aapt2" version >/dev/null 2>&1; then
  echo "Android's tools need Rosetta on this Mac. Run:  softwareupdate --install-rosetta --agree-to-license   then run this script again."; exit 1
fi

# Android API library (android.jar): use a local copy if present, otherwise install it with the SDK.
JAR="$APP/android.jar"
if [ ! -f "$JAR" ]; then
  JAR="$T/sdk/platforms/android-30/android.jar"
  if [ ! -f "$JAR" ]; then
    echo "Downloading Android platform library (one time)..."
    yes | "$T/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$T/sdk" --licenses >/dev/null 2>&1 || true
    "$T/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$T/sdk" "platforms;android-30" | tail -1
  fi
fi

# 3) Build
echo "Building Home.apk..."
B="$APP/build"; rm -rf "$B"; mkdir -p "$B/classes" "$B/dex"
RES=""
if [ -d "$APP/res" ]; then "$BT/aapt2" compile --dir "$APP/res" -o "$B/res.zip"; RES="-R $B/res.zip"; fi
ASSETS=""; [ -n "$(ls -A "$APP/assets" 2>/dev/null)" ] && ASSETS="-A $APP/assets"
"$BT/aapt2" link -o "$B/unsigned.apk" -I "$JAR" --manifest "$APP/AndroidManifest.xml" \
  --min-sdk-version 24 --target-sdk-version 30 $RES $ASSETS --auto-add-overlay
javac -nowarn -source 8 -target 8 -Xlint:-options -cp "$JAR" -d "$B/classes" $(find "$APP/src" -name '*.java') 2>&1 | grep -v JAVA_TOOL || true
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
(cd "$B/dex" && zip -q -j "$B/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f "$T/home.keystore" ] || keytool -genkeypair -keystore "$T/home.keystore" -storepass android -keypass android \
  -alias home -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Home" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$T/home.keystore" --ks-pass pass:android --key-pass pass:android --out "$D/Home.apk" "$B/aligned.apk"
"$BT/apksigner" verify "$D/Home.apk" && echo "Built: Home.apk"
