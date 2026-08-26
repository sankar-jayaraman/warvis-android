# WARVIS Setup

**Last Updated:** 2026-06-07

## Installed toolchain

The command-line Android toolchain is installed for the current user:

- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`
- Android SDK: `C:\Users\SNKR\AppData\Local\Android\Sdk`
- SDK command-line tools: `C:\Users\SNKR\AppData\Local\Android\Sdk\cmdline-tools\latest`
- Platform tools / `adb`: `C:\Users\SNKR\AppData\Local\Android\Sdk\platform-tools`
- Local Gradle install: `C:\Users\SNKR\tools\gradle-8.10.2`
- Gradle wrapper: `.\gradlew.bat`

User-level `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `Path` were updated. Open a new terminal to pick them up automatically.

The full Android Studio IDE installer was attempted through `winget`, but it requested administrator elevation and did not complete in the CLI session. The command-line SDK is enough to build the app.
- Android Gradle Plugin support

## Project path

```text
C:\Users\SNKR\WARVIS
```

## Build command

Preferred build command:

```powershell
Set-Location C:\Users\SNKR\WARVIS
.\gradlew.bat :app:assembleDebug
```

Alternative build command using the local Gradle install:

```powershell
Set-Location C:\Users\SNKR\WARVIS
gradle :app:assembleDebug
```

Debug APK output:

```text
C:\Users\SNKR\WARVIS\app\build\outputs\apk\debug\app-debug.apk
```

## Run target

Use Android Studio or `adb install` against an emulator/device once the debug APK is built.
