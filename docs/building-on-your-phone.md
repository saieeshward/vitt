# Building on your own iPhone

`xcodebuild` cannot do this from the command line, and the reason is worth
knowing rather than working around: signing needs an Apple ID that lives in
Xcode's own account store, and the first provisioning profile has to be minted
interactively. After that, CLI builds work.

Attempted on 2026-09-22 against the connected iPhone 13 Pro Max and it failed on
exactly two things.

## 1. Xcode has no Apple ID signed in

```
error: No Accounts: Add a new account in Accounts settings. (in target 'VITT')
error: No Accounts: Add a new account in Accounts settings. (in target 'VittWidget')
```

**Xcode → Settings → Accounts → + → Apple ID.** `iosApp/Local.xcconfig` already
carries `DEVELOPMENT_TEAM = <TEAM_ID>`, so once the matching Apple ID is signed
in, both targets pick the team up from there.

A free Apple ID is enough to run on your own device, with the caveat that its
certificates last seven days and the app stops launching after that.

## 2. The provisioning profile predates the App Group

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

> **App Groups need a paid Apple Developer Program membership.** A free Apple ID
> cannot create one. If the account is free, see the fallback below.

## 3. Press Run once

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

If the account is free, or you just want the app on the phone today, the widget
is the only thing that needs the App Group. Remove these two lines and
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
