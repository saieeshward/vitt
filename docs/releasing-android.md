# Releasing to Google Play

The pipeline is `.github/workflows/release-android.yml`. It runs on a tag and
degrades on purpose: with no secrets it still builds a bundle and attaches it to
the run, so the whole path can be rehearsed before a Play account exists.

```
git tag v1.0.0
git push origin v1.0.0
```

## What it does, in order

1. Builds `:androidApp:bundleRelease`, signing it only if a keystore secret
   exists.
2. Deletes the keystore from the runner, whether or not the build succeeded.
3. Attaches the `.aab` to the workflow run.
4. Uploads to the Play internal track, only if *both* the keystore and a Play
   service account are configured.

Steps 3 and 4 are separate on purpose. **The first release has to be uploaded by
hand** anyway: Play will not accept an API upload for a package it has never
seen, so the first bundle goes through the console and every later one can be
automated.

## The upload key

This is the one credential in the project that cannot be replaced. Play binds a
listing to it, so losing it means never updating that listing again. Create it
once, back it up somewhere that is not this repo and not this machine alone, and
never regenerate it.

```bash
keytool -genkeypair -v \
  -keystore upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

`upload.jks` and `keystore.properties` are both gitignored. The build reads
`keystore.properties` when it is there and stays unsigned when it is not, which
is why CI can prove the release build compiles without holding a private key.

To run a signed build locally, copy `keystore.properties.example` and fill it in.

## Secrets to set

**Settings → Secrets and variables → Actions → New repository secret.**

| Secret | What it is |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i upload.jks \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | the store password from `keytool` |
| `ANDROID_KEY_ALIAS` | `upload`, unless you chose another |
| `ANDROID_KEY_PASSWORD` | the key password, often the same as the store one |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole JSON file, pasted, for later |

Base64 because a secret is a string and a keystore is binary. The workflow
decodes it, uses it, and shreds it in a step marked `if: always()`.

## The Play service account

Only needed once you want step 4. Play Console → Setup → API access → create a
Google Cloud service account, grant it **Release manager** on this app alone,
download the JSON key, paste the whole file into `PLAY_SERVICE_ACCOUNT_JSON`.

Grant it the one app, not the account. A key with account-wide release rights
is a key that can publish anything you own.

## Before any of this works

From `docs/phase-0-checklist.md`, and none of it can be automated:

- A Play Console account, $25 once, with developer verification completed.
- The package name `ie.shoonya.vitt` registered in the console.
- For a new personal account, **a closed test with 12 testers running for 14
  consecutive days** before production is even offered. Start this early; it is
  the longest lead time in the whole release and it cannot be shortened.
- The privacy policy live at a real URL, and the Data Safety form filled to
  match the app: nothing collected, nothing shared.

## Versioning

`versionCode` must rise on every upload and Play refuses a repeat, so a rejected
build's replacement still needs a new one. Both numbers live in
`androidApp/build.gradle.kts`, and the iOS pair in `iosApp/project.yml` should
move with them so a version means one thing across both stores.
