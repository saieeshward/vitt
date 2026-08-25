# Verifying builds

VITT ships from three places, with **two different signing identities**.

| Source | Signed by | Updates from |
|---|---|---|
| Google Play | Google Play App Signing | Play only |
| App Store | Apple | App Store only |
| GitHub Releases | project key | GitHub / Obtainium only |

**A Play install and a GitHub APK cannot update each other.** Android refuses to
replace an app with one signed by a different key. To switch, uninstall first —
which deletes local data, so let a sync finish, or export, before you do.

## Fingerprints

> To be filled in at first release. Do not trust an unpopulated table.

```
Play App Signing (SHA-256):    <pending>
GitHub release key (SHA-256):  <pending>
```

## Checking an APK

```
apksigner verify --print-certs VITT-<version>.apk
```

Compare the SHA-256 against the table above. If it does not match, do not install
it.

## Reproducible builds

The Android build aims to be reproducible so a third party can confirm the
published APK matches this source. Verify with `apksigcopier` to transplant the
release signature, then `diffoscope` to compare. Status and instructions land here
once the release pipeline exists.

iOS builds are not reproducible — Xcode and codesign make that unattainable.
