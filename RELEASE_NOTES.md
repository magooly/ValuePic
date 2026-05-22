# ValuePics Release Notes

Date: 2026-04-30

## Duplicate Photo Restore Fix (Builds 27-31)

- **Issue observed:** Some restores produced 3 copies of each photo for affected items.
- **Root cause:** Older/poisoned backups contained duplicate `item_photos` rows, and some duplicates referenced the same image bytes under different file paths.
- **Fix 1 (build 27):** Restore paths now explicitly clear both `item_photos` and `valued_items` before reinsert, instead of relying only on cascade behavior.
- **Fix 2 (builds 28-30):** Added photo-row deduplication at startup and then moved dedup to run as part of restore completion.
- **Fix 3 (build 31):** Strengthened dedup to compare file content hashes (SHA-256), so same-image duplicates are removed even when stored under different paths.
- **Result:** Restore now keeps the expected single copy per photo on target devices, including previously affected restores.

## Verification

- Release build: pass (`VALUEFINDER_BUILD_COUNTER=31`).
- Field validation: user-confirmed restore behavior now works as desired on previously affected device.

---

Date: 2026-04-20

## Changes since 2026-04-17

- Empty state now shows "No records match your search criteria" (with Return button) when filters are active; "Start from scratch" only shown when database is truly empty.
- Total Value item count now reflects the same subset as the displayed total (Excludes Major Items / All Records / Filtered subset).
- Renamed total mode label from "Excluding major items" to "Excludes Major Items".
- Orphaned photo cleanup now runs automatically on startup — no manual menu action required.
- Added copyright notice: Copyright (c) 2026 Wally Horsman. All rights reserved.
- Added dedication in About screen.
- Added LICENSE, NOTICE, and GIT_WORKFLOW.md to the project root.
- Added Git version control setup (.gitignore, .gitattributes).
- Copyright headers added to key Kotlin source files.
- In-app How To PDF (how_to.pdf) regenerated with all current features documented.

---

Date: 2026-04-17

## Highlights

- Fixed gallery and camera photo orientation handling using EXIF normalization.
- Added robust orientation regression coverage with instrumentation tests (16 cases).
- Added How To PDF access from About with Open/Print actions.
- Reworked How To PDF generation to avoid blank pages and support multi-page content.
- Improved PDF export workflow with Open/Print/Share actions.
- Updated PDF summary layout:
  - Collection totals overview first
  - Sorted totals and detail sections by value
  - Grand total line
  - Right-aligned value columns
  - Metadata header (generated time, counts)
- Added restore and delete confirmation dialogs to reduce accidental destructive actions.
- Added fallback UI for unavailable/corrupt images in list, valuation, and details screens.
- Improved localization readiness by moving most UI text to `strings.xml`.
- Added one-click release script improvements:
  - Unit test + instrumentation test + release build + SHA-256
  - Optional switches for skipping test stages
  - Optional artifact copy to `releases/<timestamp>/`
  - Optional dirty-git enforcement when repository metadata is present

## Testing and Verification

- JVM unit tests: pass
- Instrumentation tests: pass (16/16)
- Release build: pass

Latest verified APK checksum:

- `app/build/outputs/apk/release/app-release.apk`
- SHA-256: `99DA9298C8C1EFA06C184A79A0F320726F50B13DC69BE0B242355D68709874A8`

Latest copied release artifacts:

- `releases/20260417_203607/`

## Notable New/Updated Files

- `release.ps1`
- `app/src/main/java/com/example/valuefinder/ui/HowToHelper.kt`
- `app/src/main/java/com/example/valuefinder/ui/ValuePicsApp.kt`
- `app/src/main/java/com/example/valuefinder/ui/ItemListScreen.kt`
- `app/src/main/java/com/example/valuefinder/ui/ValuationScreen.kt`
- `app/src/main/java/com/example/valuefinder/ui/DetailsScreen.kt`
- `app/src/main/java/com/example/valuefinder/ui/ComparableParser.kt`
- `app/src/main/java/com/example/valuefinder/PhotoUtils.kt`
- `app/src/main/java/com/example/valuefinder/ValuePicsRepository.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/androidTest/java/com/example/valuefinder/PhotoUtilsExifInstrumentationTest.kt`
- `app/src/test/java/com/example/valuefinder/ui/ComparableParserTest.kt`
- `app/src/test/java/com/example/valuefinder/CollectionTotalsTest.kt`
- `README.md`
- `QUICK_START.md`
- `DELIVERY.md`

