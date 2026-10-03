#!/bin/bash
set -e
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdtools.zip
unzip -q -o cmdtools.zip -d /tmp/cmdtools
rm -rf "$ANDROID_HOME/cmdline-tools/latest"
mv /tmp/cmdtools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
if ! grep -q "ANDROID_HOME" "$HOME/.bashrc"; then
  {
    echo "export ANDROID_HOME=$ANDROID_HOME"
    echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools'
  } >> "$HOME/.bashrc"
fi
yes | sdkmanager --licenses > /dev/null || true
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > "$OLDPWD/local.properties" 2>/dev/null || true
echo "Android SDK ready. Build with: bash gradlew assembleDebug"
