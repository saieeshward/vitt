# Running VITT locally

Requires JDK 17+ (21 recommended), the Android SDK, and — for iOS — macOS with
Xcode and [xcodegen](https://github.com/yonaskolb/XcodeGen).

## Shared logic tests

Everything that matters most — the sync engine, the amount parser, the CSV
importer — is plain Kotlin in `:shared` and tests on any platform:

```bash
./gradlew :shared:jvmTest
```

## Android

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## iOS simulator

The Xcode project is generated, not committed, so regenerate it after changing
`iosApp/project.yml`:

```bash
cd iosApp && xcodegen generate
xcodebuild -project VITT.xcodeproj -scheme VITT \
  -sdk iphonesimulator -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build

xcrun simctl boot "iPhone 17 Pro"
xcrun simctl install booted "$(find build/Build/Products -name VITT.app | head -1)"
xcrun simctl launch booted ie.shoonya.tracker
```

## iOS device

Needs an Apple ID signed in to Xcode; see the Phase 0 checklist. Then open
`iosApp/VITT.xcodeproj`, pick your phone, and press Run.

## Toolchain notes

Things that cost time to work out, recorded so they cost nobody else any:

- **Gradle 9.7.1 with AGP 9.3.2.** AGP 8.x cannot run on Gradle 9.6+ (it uses a
  Gradle internal API that was removed), and AGP 9 cannot apply
  `com.android.application` alongside the Kotlin Multiplatform plugin at all.
  Hence the module split: `:composeApp` is a KMP *library* holding the shared UI,
  and `:androidApp` / `iosApp` are thin shells that only host it.
- **Plugins are declared once in the root `build.gradle.kts` with `apply false`.**
  Without that, AGP's built-in Kotlin support and the multiplatform plugin load
  the Kotlin Gradle Plugin into two classloaders, and the build fails with a
  baffling "class X cannot be cast to class X" error.
- **`compileSdk` is 37, `targetSdk` is 36.** Compose 1.12 requires compiling
  against 37; Play requires *targeting* 36 for new apps from 31 Aug 2026. These
  are different settings and both are correct.
- **`CADisableMinimumFrameDurationOnPhone` must be `true` in `Info.plist`.**
  Compose Multiplatform aborts at launch without it, because iOS would otherwise
  silently cap the app at 60fps on ProMotion displays.
- **Do not `.ignoresSafeArea(.all)`** around the Compose view — content then draws
  under the Dynamic Island. Only the keyboard inset should be ceded to Compose.
