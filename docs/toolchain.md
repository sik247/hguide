# Toolchain

JDK 17, Android SDK platform 34 + build-tools 34.0.0, Gradle wrapper 8.9.
macOS quick setup used for development:

```
brew install openjdk@17 && brew install --cask android-commandlinetools
export JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Android/sdk
sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Emulator smoke test: `system-images;android-34;google_apis;arm64-v8a`, install `app-debug.apk`, open the 데모 tab.
Pleos Emulator + Virtual Console: install the release APK (real gateways).
