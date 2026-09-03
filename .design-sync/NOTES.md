# design-sync notes for VITT

- **Tokens only, no components.** VITT's UI is Compose Multiplatform drawing to
  a Skia canvas. There is no `package.json`, no lockfile, no Storybook and no
  `dist/`, so the converter cannot run and there is nothing to bundle. The
  skill's own core principle is "ship what the customer already built, never a
  reimplementation", which rules out hand-writing React copies of the Compose
  components. Decided with the maintainer on 2026-09-03.
- **`_ds_sync.json` is deliberately omitted.** The layout is produced off-script,
  and the anchor must only ever vouch for a build the converter receipted. No
  anchor means the next sync re-verifies everything, which is the correct and
  honest outcome here.
- **The token values are generated, not authored.** They come out of
  `composeApp/.../ui/theme/{Palette,VittColors,Themes,VittTheme}.kt`. If a theme
  or accent changes there, regenerate rather than hand-editing `ds-bundle/`.
- The design project that consumes these (`Design identity and mockups plan`)
  is a regular project, not a design system, so it cannot be a sync target. It
  currently imports the third-party "Nocturne" design system, which is why VITT
  mockups have not been in VITT's palette.
