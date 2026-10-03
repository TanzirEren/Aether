# SETUP
```bash
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk wget unzip
bash .devcontainer/setup-sdk.sh        # installs SDK 34 + build-tools, persists ANDROID_HOME to ~/.bashrc
source ~/.bashrc
bash gradlew assembleDebug             # APK: app/build/outputs/apk/debug/app-debug.apk
```
Stack: Gradle 8.5 · AGP 8.2.2 · JDK 17 · minSdk 24 · target/compile 34.
