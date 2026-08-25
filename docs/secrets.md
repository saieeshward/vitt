# Secrets and signing

## Safe to commit

Counterintuitive but documented by Google: for installed applications the OAuth
client ID and any issued "secret" are embedded in the app binary and are **not
treated as secret**. The real control is binding — an Android client is bound to
its package name plus signing-certificate SHA-1, and an iOS client to its bundle
ID. A stolen client ID cannot be used from another app.

So these may live in the public repo:

- OAuth **client IDs** for the Android and iOS clients
- `google-services.json`, if Firebase is ever added, provided every key is API-restricted

## Never commit

| Secret | Where it lives |
|---|---|
| Upload keystore (`.jks`) + passwords | GitHub Actions secret, base64-encoded |
| Play publisher service-account JSON | GitHub Actions secret — or eliminate it with Workload Identity Federation |
| App Store Connect API key (`.p8`) | GitHub Actions secret; Apple has no OIDC equivalent, so it stays long-lived |
| `match` certificate repo credentials | GitHub Actions secret; the certs repo must be **private** |
| Any web/server OAuth client secret | not needed by this project — do not create one |

`.gitignore` blocks the obvious file extensions, but that is a safety net, not a
control. Check `git diff --staged` before committing.

## The SHA-1 trap

Register **two** SHA-1 fingerprints for the Android OAuth client:

1. Your **upload key** — used in debug and local builds.
2. **Google Play App Signing's certificate** — the one Play re-signs with, found
   in Play Console → Setup → App integrity.

Register only the first and Google Sign-In works perfectly in development and
fails only for real users in production. This is a common and expensive mistake.

## GitHub Actions rules

- Put every publishing secret in a **`production` Environment** with required
  reviewers and a branch/tag restriction. This is the single highest-value control.
- Secrets are **not** passed to workflows triggered by forks, **not** inherited by
  reusable workflows, and **not** readable by Dependabot runs.
- Never put a release secret in a `pull_request`-triggered workflow.
- Never combine `pull_request_target` with a checkout of the PR head.
- `fastlane match` runs `--readonly` in CI.
