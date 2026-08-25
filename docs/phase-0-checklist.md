# Phase 0 — long-lead items

Nothing here needs code, and several items have multi-week or multi-month lead
times. Start them now; they are the real schedule.

Legend: **[you]** requires your identity, payment, or a legal decision — I cannot
do it. **[code]** automatable.

## Blocking decisions

- [ ] **[you] Confirm or replace the app name.** Everything below depends on it —
      trademark, domain, bundle IDs, store listings. Current placeholder is
      **Tender**, used throughout the repo. Changing it later is cheap in code and
      expensive in trademark and store presence.
      - Check: EUIPO and USPTO/IPOI registers, Play and App Store searches, domain
        availability, npm/GitHub name collisions.
- [ ] **[you] Individual or company developer account?**
      - *Individual:* Google Play publicly displays your **legal name**.
      - *Company:* Play publicly displays a **legal address**, and an organisation
        account requires a **D-U-N-S number (up to 30 days to issue)**.
      - Also relevant: Apple Guideline 5.1.1(ix) prefers a legal entity for apps in
        "banking and financial services." A pure tracker should be fine either way,
        but an entity removes the argument.
      - **Decide before registering anything.** Converting later is painful.

## Accounts and payments

- [ ] **[you]** Apple Developer Program — **$99/yr**. Enrolment and entity
      verification can take weeks. Start first.
- [ ] **[you]** Google Play Console — **$25 one-time**.
- [ ] **[you]** Complete Play developer verification (identity, address, phone).
- [ ] **[you]** D-U-N-S request, *if* incorporating. Up to 30 days.
- [ ] **[you]** Google Cloud project; enable **both** the Sheets API and the Drive API.
- [ ] **[you]** Create OAuth clients — Android (package + SHA-1) and iOS (bundle ID).
      Register **both** SHA-1s: your upload key and Play App Signing's certificate.
      See [secrets.md](secrets.md).

## Domain and hosting

- [ ] **[you]** Register a domain. Needed for OAuth **brand verification**, which
      requires a homepage and a privacy policy on a domain you own.
- [ ] **[you]** Verify domain ownership in **Google Search Console** (DNS
      propagation 1–48h). Must be done by an Owner/Editor of the Cloud project.
- [ ] **[code]** Publish the privacy policy to GitHub Pages on that custom domain.
      Draft ready at [privacy-policy.md](privacy-policy.md) — fill the `<…>`
      placeholders.
- [ ] **[you]** Configure the OAuth consent screen: app name, logo, support email,
      homepage, privacy policy link. Submit for **brand verification** (2–3 business
      days). No demo video and no security assessment are needed, because every
      scope is non-sensitive.

## Legal

- [ ] **[you]** File the trademark on the name and icon. **6–12 month lead** — this
      is the longest item in the project and the only real defence against store
      clones. File before launch.
- [ ] **[you]** Review the privacy policy draft. It is a binding public statement,
      and both stores check it against your declared data practices.
- [x] **[code]** Licence decision: **Apache-2.0**, with `TRADEMARK.md` covering the
      name. Settled before the first external PR, as it must be.

## Repository

- [x] **[code]** `git init`, Apache-2.0 `LICENSE`, `NOTICE`, `TRADEMARK.md`
- [x] **[code]** `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`
- [x] **[code]** `.gitignore` covering keystores, provisioning profiles, API keys
- [x] **[code]** Issue templates + `config.yml`
- [x] **[code]** `docs/secrets.md`, `docs/verifying-builds.md`
- [ ] **[you]** Create the GitHub repo and push. Then:
      - Enable **private vulnerability reporting** (Settings → Security). It is
        separate from `SECURITY.md` and off by default.
      - Create the labels the issue templates reference: `bug`, `enhancement`,
        `android`, `ios`, `sync`, `sheets`, `import`. Templates fail if a label
        does not exist.
      - Create a **`production` Environment** with required reviewers.
      - Enable Discussions if you want a support channel that is not the issue tracker.

## Do not do yet

- Do **not** submit anything for OAuth *scope* verification. With `drive.file` +
  `openid email profile` all scopes are non-sensitive and no verification is
  required. Requesting `spreadsheets` would cost a 2–8 week review; `drive` would
  cost months plus a recurring paid CASA assessment.
- Do **not** create a web/server OAuth client. Not needed, and its secret is a
  genuine secret you would then have to protect.

## Watch items

- **Play target API 36** is required for new apps and all updates from
  **31 Aug 2026** (extension to 1 Nov 2026 via Console). Build against it from the
  first commit.
- **Play closed testing gate:** personal accounts created after 13 Nov 2023 need
  **12 testers continuously opted in for 14 days** before applying for production.
  Begin recruiting the moment there is an installable build — this is the critical
  path to launch, and it is calendar time you cannot compress.
- **Google's sideloading developer verification** (2026–27) may require registering
  package names and signing keys for APKs distributed outside Play.

## Added during Phase 1 — iOS device builds

- [ ] **[you] Sign in to Xcode with your Apple ID.** Xcode → Settings → Accounts
      → **+** → Apple ID. A *free* Apple ID is enough to run on your own device;
      certificates last 7 days and the app stops launching after that, which is
      fine for testing. The paid Apple Developer Program is only needed for
      TestFlight and the store.
- [ ] **[you] Set the signing team**, either in Xcode (select the Tender target →
      Signing & Capabilities → Team) or by adding `DEVELOPMENT_TEAM: <TEAMID>` to
      `iosApp/project.yml` and re-running `xcodegen generate`. Do not commit a
      team id if the repo is public.
- [ ] **[you] Trust the developer on the phone** the first time: Settings →
      General → VPN & Device Management → trust the certificate.
