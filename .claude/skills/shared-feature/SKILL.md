---
name: shared-feature
description: Add or change logic in the :shared KMP module — a parser, a money/currency rule, a sync primitive, a model type. Use whenever work lands in shared/src/commonMain, or when adding a platform-specific implementation via expect/actual.
---

# Adding shared logic

Order matters. Do these in sequence.

## 1. Place it

`shared/src/commonMain/kotlin/ie/shoonya/tracker/<area>/`

Existing areas: `capture/`, `money/`, `sync/`. Add a new one only when the code
fits none of them — prefer growing an existing area over inventing a fourth.

Stay in `commonMain`. Reach for `expect`/`actual` only when a platform API truly
differs (filesystem, keystore, clock source). A pure-Kotlin implementation that
compiles for both targets is always the better answer.

## 2. Write the test first

`shared/src/commonTest/kotlin/ie/shoonya/tracker/<area>/<Name>Test.kt`

Use `kotlin.test`. Match the existing files' style — read a neighbouring test
before writing a new one.

Cover the cases that actually break this app:

- **Money:** integer minor units only. Test rounding at the boundary, and that no
  operation mixes two currencies silently.
- **Parsing:** locale-varying separators (`1.234,56` vs `1,234.56`), currency
  symbols on either side, blank and malformed input.
- **Sync/HLC:** ordering under equal timestamps, clock going backwards, and merge
  being commutative — the same two events applied in either order agree.

## 3. Run the fast loop

```bash
rtk ./gradlew :shared:jvmTest
```

Only run `:shared:allTests` when the change touches `expect`/`actual` or anything
platform-shaped. It links the iOS targets and is much slower.

## 4. Check it against the constraints

Before reporting done, confirm the change does not violate `CLAUDE.md`'s hard
constraints — most relevantly:

- No floating point anywhere near an amount.
- No blending of currencies into a single total.
- Nothing that assumes the sheet can be read back as authoritative.
