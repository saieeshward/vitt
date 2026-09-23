package ie.shoonya.vitt.config

/**
 * Public URLs the app points at. All of them are the maintainer's to own.
 *
 * Empty means "not published yet", and every screen that would show the link
 * hides it instead. Apple 5.1.1(i) and Play's User Data policy both want the
 * privacy policy reachable from inside the app, so the store gate in
 * docs/phase-0-checklist.md stays open until this is filled in.
 */
object Links {
    /**
     * The privacy policy, on a domain the maintainer controls (PLAN §7.4):
     * the project's GitHub Pages site, built from docs/privacy-policy.md by
     * tools/site-privacy.py and deployed by .github/workflows/pages.yml.
     */
    const val PRIVACY_POLICY = "https://saieeshward.github.io/vitt/privacy/"

    /** Where to report a problem. A GitHub issues page is fine. */
    const val SUPPORT = "https://github.com/saieeshward/vitt/issues"
}
