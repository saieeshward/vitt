# Building on your own iPhone

**It is on the phone.** VITT 1.0.0 (build 1) was installed and launched on the
iPhone 13 Pro Max on 2026-09-22, without signing into Xcode at all.

The trick is that `No Accounts: Add a new account in Accounts settings` is an
*automatic signing* error: Xcode wants to talk to the developer portal to mint a
profile. It is not needed, because this machine already has both halves —

```
Apple Development: <your-apple-id> (XXXXXXXXXX)   valid
iOS Team Provisioning Profile: ie.shoonya.vitt           valid to 2026-09-24
```

so the app can be built unsigned and signed afterwards by hand.

## The recipe that worked

```bash
cd iosApp
# 1. Build unsigned for the device architecture.
xcodebuild -project VITT.xcodeproj -scheme VITT -configuration Debug \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath build-device CODE_SIGNING_ALLOWED=NO build

APP=build-device/Build/Products/Debug-iphoneos/VITT.app
PROF=~/Library/Developer/Xcode/UserData/Provisioning\ Profiles/<PROFILE_UUID>.mobileprovision

# 2. Embed the profile and pull its entitlements out.
cp "$PROF" "$APP/embedded.mobileprovision"
security cms -D -i "$PROF" > /tmp/prof.plist
/usr/libexec/PlistBuddy -x -c 'Print :Entitlements' /tmp/prof.plist > /tmp/ent.plist

# 3. Sign the bundle.
codesign --force --sign "Apple Development: <your-apple-id> (XXXXXXXXXX)" \
  --entitlements /tmp/ent.plist --timestamp=none "$APP"

# 4. Install and run.
xcrun devicectl device install app --device <UDID> "$(pwd)/$APP"
xcrun devicectl device process launch --device <UDID> --terminate-existing ie.shoonya.vitt
```

**Manual signing with `PROVISIONING_PROFILE_SPECIFIER` does not work here** —
the profile is Xcode-managed, and `xcodebuild` refuses to use a managed profile
in manual mode. Signing the built bundle afterwards sidesteps that entirely.

## Two caveats on this build

**No widget.** The profile predates the App Group, and the widget is the only
thing that needs it, so it was dropped for this build and `project.yml` restored
afterwards — the repo is unchanged. Everything else is there: capture, the
reminder, the companion, sync, reports.

**The profile expires 2026-09-24.** Two days. After that it needs regenerating,
which is the Xcode route below and is worth doing properly anyway.

## Doing it properly, for the widget and for TestFlight

### Sign into Xcode

```
error: No Accounts: Add a new account in Accounts settings. (in target 'VITT')
error: No Accounts: Add a new account in Accounts settings. (in target 'VittWidget')
```

**Xcode → Settings → Accounts → + → Apple ID.** `iosApp/Local.xcconfig` already
carries `DEVELOPMENT_TEAM = <TEAM_ID>`, so once the matching Apple ID is signed
in, both targets pick the team up from there.

A free Apple ID is enough to run on your own device, with the caveat that its
certificates last seven days and the app stops launching after that.

### Add the App Group

```
error: Provisioning profile "iOS Team Provisioning Profile: ie.shoonya.vitt"
       doesn't support the group.ie.shoonya.vitt App Group.
```

The widget is a separate process with its own container, so it can only read the
app's figures through a shared App Group. Both targets declare it:

- `iosApp/iosApp/VITT.entitlements`
- `iosApp/VittWidget/VittWidget.entitlements`

The existing profile was minted before that entitlement existed, so it has to be
regenerated with the capability included.

**In Xcode, for `VITT` *and* `VittWidget`:** Signing & Capabilities → check
*Automatically manage signing*, pick the team, then **+ Capability → App
Groups**, and tick `group.ie.shoonya.vitt`. Xcode regenerates both profiles.

> **This account can already do it.** Checking the profiles on this machine:
>
> ```
> iOS Team Provisioning Profile: ie.shoonya.vitt   team=<TEAM_ID>   groups=no
> iOS Team Provisioning Profile: ie.shoonya.yant   team=<TEAM_ID>   groups=YES
> ```
>
> Yantra already holds an App Group on the same team, and only a paid membership
> can create one. So VITT's profile lacks the capability purely because it was
> minted before the entitlement existed — ticking the box regenerates it exactly
> as it did for Yantra. The fallback below is a convenience, not a necessity.

### Press Run once

Select the phone as the destination and Run. Trust the developer on the device
the first time: **Settings → General → VPN & Device Management**.

After that first interactive build, this works from the terminal:

```bash
cd iosApp
xcodebuild -project VITT.xcodeproj -scheme VITT -configuration Debug \
  -destination 'id=<DEVICE_UDID>' \
  -derivedDataPath build-device -allowProvisioningUpdates build
```

## Fallback: the app without the widget

Not needed on this account — see above — but if you just want the app on the
phone before touching capabilities, the widget is the only thing that needs the
App Group. Remove these two lines and
regenerate, and everything else — capture, the reminder, the companion, sync —
installs and runs normally:

```yaml
# iosApp/project.yml
    dependencies:
      - target: VittWidget      # ← delete these two lines
        embed: true
...
        CODE_SIGN_ENTITLEMENTS: iosApp/VITT.entitlements   # ← and this one
```

```bash
cd iosApp && xcodegen generate
```

Put them back before a store build. Nothing else in the app depends on the App
Group, and the widget degrades to "Nothing recorded yet" rather than crashing if
it is ever installed without one.

## What the widget needs on the store

`group.ie.shoonya.vitt` has to exist in the developer portal as an App Group,
with both `ie.shoonya.vitt` and `ie.shoonya.vitt.widget` registered as App IDs
that use it. Xcode creates all three when you tick the capability with automatic
signing on a paid account.
