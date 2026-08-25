# Google Cloud setup

Everything VITT needs from Google. Takes about 15 minutes.

**The one thing not to get wrong:** request **only** `drive.file`, `openid`,
`email`, `profile`. All four are *non-sensitive*, which means no verification
review, no CASA security assessment, no 100-user cap, and no 7-day refresh-token
expiry once published. Adding `spreadsheets` or `drive` — even "just to be safe"
— moves the app into sensitive or restricted tier and costs weeks plus a
recurring paid audit. `drive.file` already covers create, read, write and
`batchUpdate` on files the app itself created.

---

## 1. Create the project

1. Go to <https://console.cloud.google.com/projectcreate>
2. **Project name:** `VITT`
3. **Location:** No organisation
4. **Create**, then make sure `VITT` is the selected project in the top bar.

## 2. Enable the two APIs

Both are required. Sheets alone is not enough — the app creates and locates the
spreadsheet through Drive.

1. <https://console.cloud.google.com/apis/library/sheets.googleapis.com> → **Enable**
2. <https://console.cloud.google.com/apis/library/drive.googleapis.com> → **Enable**

## 3. Configure the OAuth consent screen

Now called **Google Auth Platform** → **Branding** / **Audience** / **Data Access**.

1. Go to <https://console.cloud.google.com/auth/overview> → **Get started**
2. **App name:** `VITT`
3. **User support email:** your address
4. **Audience:** **External**
5. **Contact information:** your address
6. Agree and **Create**

### Scopes — the important bit

**Data Access** → **Add or remove scopes**. Add exactly these four:

| Scope | Tier |
|---|---|
| `.../auth/drive.file` | Non-sensitive |
| `openid` | Non-sensitive |
| `.../auth/userinfo.email` | Non-sensitive |
| `.../auth/userinfo.profile` | Non-sensitive |

`drive.file` may not appear in the picker list — use **Manually add scopes** and
paste `https://www.googleapis.com/auth/drive.file`.

If the console shows any scope as **Sensitive** or **Restricted** after saving,
something extra got added. Remove it before continuing.

### Publishing status

**Audience** → while in **Testing**, add your own Google account under **Test
users**, which is enough for development.

Because every scope is non-sensitive you can press **Publish app** to move to
**In production** without a verification review. Do that before real users exist:
in Testing, refresh tokens expire after 7 days and you would be re-authenticating
constantly.

## 4. Create the OAuth clients

**Clients** → **Create client**. Native apps are *public clients* — there is no
client secret, which is why PKCE is used instead.

### iOS

- **Application type:** iOS
- **Name:** `VITT iOS`
- **Bundle ID:** `ie.shoonya.vitt`

Copy the **Client ID** and also note the **iOS URL scheme** (the client ID with
its dot-separated parts reversed). Both are needed for the redirect URI.

### Android

- **Application type:** Android
- **Name:** `VITT Android`
- **Package name:** `ie.shoonya.vitt`
- **SHA-1 certificate fingerprint** — the debug key on this machine is:

  ```
  B1:69:96:D4:F8:D6:98:83:CC:79:12:8A:B6:08:8C:10:C0:65:3B:F0
  ```

> **Add the second SHA-1 before your first Play release.** Play App Signing
> re-signs the upload with *Google's* certificate, so the production fingerprint
> differs from your upload key. Register both, or Sign-In works perfectly in
> development and fails only for real users. Find it in Play Console → Setup →
> App integrity.

## 5. Give the client IDs to the app

Client IDs are **not secrets** — Google documents installed-app clients as
public, and the real control is the binding to bundle id and signing
certificate. They are safe to commit. A *web* client secret would not be; do not
create a web client.

Put them in `local.properties` (already gitignored) for now:

```properties
oauth.ios.clientId=<the iOS client id>
oauth.android.clientId=<the Android client id>
```

## 6. Later, not now

- **Brand verification** — needed only to show your name and logo on the consent
  screen instead of a raw client id. Requires a domain you own plus a privacy
  policy on it, verified in Google Search Console. See the Phase 0 checklist.
- **Play App Signing SHA-1** — before the first Play release, per above.

---

## Hard rules

1. **Never change the OAuth client ID.** `drive.file` grants are stored per
   (file × client id). Changing it destroys every existing user's access to their
   own spreadsheet, unrecoverably — they would have to re-pick the file.
2. **Never add `spreadsheets` or `drive`.** They buy nothing that `drive.file`
   does not already provide for app-created files, and cost verification, CASA,
   and an annual reassessment.
3. **`drive.file` cannot list or search.** The app must persist the spreadsheet's
   file id itself; if it is lost, the user re-selects the file through the Google
   Picker. This is a deliberate trade for staying out of the restricted tier.
