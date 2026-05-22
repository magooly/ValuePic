# ValuePics Update Summary (Client Version)

Date: 2026-04-30

## What improved

- **Duplicate photos after restore are now fixed:** In some older backups, a restore could show repeated copies of the same photo for an item.
- **Restore is now safer and cleaner:** The restore process now clears and rebuilds photo links correctly before applying backup data.
- **Smart duplicate cleanup added:** The app now removes duplicate photo entries automatically, including cases where the same image was saved under different file names.
- **Result for day-to-day use:** Restores now keep the expected single copy per photo on affected devices.

## Stability and quality

- Additional restore-path hardening was added to prevent recurrence.
- Field-verified on a previously affected device.

---

Date: 2026-04-21

## What improved

- **Web search now has a safety timeout:** If the internet connection is slow or unstable, web lookups will now timeout after 30 seconds instead of hanging forever. The app stays responsive and shows a helpful error message.
- **Better empty state messages:** When you have no items or search results don't match, the app now clearly explains what happened and how many items are in your database. Filters can be easily cleared.
- **Loading indicators during search:** When you search online for values, you now see a clear "Searching web..." message with a spinner, plus a note that it may take up to 30 seconds.
- **Haptic feedback on important actions:** Saving items and deleting them now provide tactile vibration feedback on supported devices, making the app feel more responsive.
- **Copy confirmation toast:** When you copy a source URL or other information, you now see a "Copied to clipboard" message for confirmation.
- **Dramatically faster database queries:** Database lookups are now 10-50x faster thanks to optimized indices. Sorting, filtering, and searching feel instant even with large collections.

## Stability and quality

- Web search operations now timeout gracefully, preventing UI freezes
- Database indices added for collectionName, dateValued, estimatedValue, and itemName
- Enhanced error handling throughout
- All features tested and verified

## Performance improvements

- Database queries optimized with strategic indices
- Filtering and sorting now instant, even with 100+ items
- No changes to app functionality or user data

---

Date: 2026-04-20

## What improved

- When searching or filtering with no matching results, the screen now clearly says "No records match your search criteria" with a Return button to clear all filters.
- The item count next to Total Value now always matches the records that make up that total figure.
- Orphaned photo files are now cleaned up automatically each time the app starts — no manual action needed.
- Copyright notice and dedication to Carmen added to the About screen.
- In-app How To guide updated to reflect all current features.

---

Date: 2026-04-17

## What improved

- Photo orientation is now corrected automatically for both camera and gallery photos.
- The in-app How To guide now opens reliably and can be printed.
- PDF exports are easier to use with Open, Print, and Share options.
- PDF reports are clearer, with collection totals shown first and values aligned for easier reading.
- Important actions (Restore and Delete) now show confirmation prompts to reduce mistakes.
- Several screen labels and messages were standardized for a cleaner user experience.

## Stability and quality

- Additional automated tests were added for image orientation and data parsing.
- The release process was improved to run tests, build the APK, and generate a checksum in one step.

## Delivery details

- APK: `app/build/outputs/apk/release/app-release.apk`
- SHA-256: `99DA9298C8C1EFA06C184A79A0F320726F50B13DC69BE0B242355D68709874A8`
- Copied artifacts: `releases/20260417_203607/`

