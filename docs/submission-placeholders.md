# Submission placeholders

Everything the submission needs that does not exist yet, in one place. Each row is
a `{{TOKEN}}` used verbatim across `docs/app-store-listing.md`,
`docs/privacy-policy.md` and `docs/review-notes.md`. Fill the token here, then
search and replace it everywhere.

Nothing in this table can be automated — each needs an account, a purchase, a
domain, or a decision only the maintainer can make.

## Accounts and identity

| Token | What it is | Where to get it | Blocks |
|---|---|---|---|
| `{{APPLE_TEAM_ID}}` | 10-character Apple team id | Apple Developer Program, $99/yr | Any signed build, TestFlight, upload |
| `{{APPLE_DEVELOPER_NAME}}` | The name shown on the store page as the seller | Set during enrolment; an individual's legal name unless incorporated | Listing |
| `{{SUPPORT_EMAIL}}` | Contact address for App Review and for users | Any mailbox you actually read | ASC contact, 1.5 |
| `{{PRIVACY_EMAIL}}` | Contact address in the privacy policy | Can be the same address | Privacy policy |

## URLs

| Token | What it is | Where to get it | Blocks |
|---|---|---|---|
| `{{DOMAIN}}` | A domain you own | A registrar; also needed for Google OAuth brand verification | Everything below |
| `{{POLICY_URL}}` | Live HTTPS URL of the privacy policy | Publish `docs/privacy-policy.md` to GitHub Pages on `{{DOMAIN}}` | **Hard blocker.** ASC refuses the submission without it |
| `{{SUPPORT_URL}}` | Support page | A page on `{{DOMAIN}}`, or the repo's GitHub issues page | **Hard blocker.** ASC requires it |
| `{{MARKETING_URL}}` | Optional product page | A page on `{{DOMAIN}}` | Optional |

Once `{{POLICY_URL}}` and `{{SUPPORT_URL}}` are real, put them in
`shared/src/commonMain/kotlin/ie/shoonya/vitt/config/Links.kt`. Both constants are
empty strings today and the Settings screen hides each link while its constant is
blank, so the About row appears only once the URLs exist. That is deliberate:
shipping a dead privacy-policy link is worse than shipping none, and Apple 5.1.1(i)
wants the policy reachable from inside the app.

## Dates and versions

| Token | Value |
|---|---|
| `{{POLICY_LAST_UPDATED}}` | The date you publish the policy, as `1 October 2026` |
| `{{SUBMISSION_DATE}}` | The date you upload |

Version is already set: `1.0.0` build `1`, in `iosApp/project.yml`,
`iosApp/iosApp/Info.plist` and `androidApp/build.gradle.kts`. Bump the build
number on every upload, including a rejected one's replacement.

## Assets

| Token | What it is | Notes |
|---|---|---|
| ~~`{{SCREENSHOTS_6_9}}`~~ | **Done.** Five in `screenshots/6.9/`, 1320×2868 | 6.9" is the only required size; Apple scales it down for other devices. 1290×2796 is 6.7", not 6.9" |
| `{{DEMO_VIDEO_URL}}` | Optional walkthrough for App Review | Unlisted YouTube link is fine. Record: add a spend → Ledgers → Reports → connect Google → disconnect |

Screenshots must show the app in use, never a splash or an empty state. Seed the
sample data first — `VITT_SEED=1` as a launch environment variable in the Xcode
scheme, debug build only — so the screens have real content. That switch does not
exist in a release binary.

## Decisions

| Token | Decision |
|---|---|
| `{{PRIMARY_CATEGORY}}` | Suggested: **Finance**. Alternative: Productivity, which is a less crowded 4.3 field but a worse match |
| `{{SECONDARY_CATEGORY}}` | Suggested: **Productivity** |
| `{{PRICE}}` | Suggested: **Free**, no in-app purchases. Anything else pulls in StoreKit and Apple 3.1.1 |
