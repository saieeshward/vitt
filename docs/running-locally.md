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
xcrun simctl launch booted ie.shoonya.vitt
```

Launch variables (`VITT_SEED=1` for the sample month, `VITT_STRESS=1` for
eighteen months across six currencies) reach the app only with simctl's
child-environment prefix; a bare `VITT_SEED=1 xcrun simctl launch …` is
silently ignored and the app opens empty:

```bash
SIMCTL_CHILD_VITT_STRESS=1 xcrun simctl launch booted ie.shoonya.vitt
```

Both seeders are no-ops once the database has any transaction, so a different
data set needs `xcrun simctl uninstall booted ie.shoonya.vitt` first.

To check what VoiceOver would read on the current screen without a device, dump
the Simulator's accessibility tree (role, frame in device points, label, value):

```bash
python3 tools/axtree.py --grep 'amount|decimal'   # --all for every node, no flag for interactive only
```

The terminal needs Accessibility permission (System Settings > Privacy &
Security); the script says so when it is missing. It waits up to eight seconds
for the app to publish its tree, because the screen reads as empty for a moment
after every relaunch. `tools/sim.py tap X Y` taps in screenshot pixels, which is
three times the points `axtree.py` prints on the iPhone 17 Pro.

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

## Simulator vs device — which to use

The simulator is the faster loop and is right for almost everything: no
provisioning, no certificate expiry, and it can be driven end to end with
`simctl` (install, launch, screenshot, reset).

**Two things it cannot do.**

- **Judge how the app feels under a thumb.** Touch latency and scroll physics
  need real hardware. This is why the amount keypad was verified on a device.
- **Hold a Keychain credential.** A simulator build is signed ad hoc, which
  carries no keychain entitlement — `SecItemAdd` fails with `-34018` and
  `securityd` logs *"Requestor lacks required entitlement"*. Embedding a
  `keychain-access-groups` entitlement does not help: ad-hoc signing drops it.
  So **sign-in does not persist in the simulator**, and the live verification
  checks must be run on a device.

That second point is worth knowing before it costs an hour. It also produced a
useful finding: the failure used to be *silent*, because `SecItemAdd`'s status
was ignored and the app reported a successful sign-in while holding no token.
It now throws (`TokenStoreException`).
