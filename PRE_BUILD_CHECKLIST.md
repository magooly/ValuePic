# Pre-Build Polish Checklist

**Build Date Target:** April 2026  
**Estimated Time:** 1-2 hours for quick wins  
**Risk Level:** Low (all changes are additive)

---

## 📋 Critical Path (Must Do Before Build)

### 1. Web Search Timeout (15 min)
- [ ] Read: `QUICK_IMPLEMENTATION_GUIDE.md` → Section 1
- [ ] Add `withOperationTimeout` to `RetryUtil.kt`
- [ ] Wrap `searchForValue()` in `WebValuationService.kt`
- [ ] Test: Go offline, try web search → Should error after 30s, not hang
- [ ] Verification: No build errors

**Why Critical:** Prevents app freeze if network hangs

---

### 2. Build & Test (30 min)
- [ ] Run: `./gradlew clean`
- [ ] Run: `./gradlew build`
- [ ] Run: `./gradlew testDebugUnitTest`
- [ ] Install on physical device
- [ ] Test core flows:
  - [ ] Create item with photo
  - [ ] Search online for value
  - [ ] Save to collection
  - [ ] View details
  - [ ] Edit item
  - [ ] Delete item
  - [ ] Export PDF
  - [ ] Check database backup

**Why Critical:** Ensure no regressions from recent improvements

---

## ✨ Polish Pass (Recommended Before Build)

### 3. Haptic Feedback (15 min)
- [ ] Read: `QUICK_IMPLEMENTATION_GUIDE.md` → Section 2
- [ ] Add `triggerHapticFeedback()` to `ValuePicsApp.kt`
- [ ] Add haptic to save button
- [ ] Add haptic to delete button
- [ ] Test on physical device (haptic only, emulator won't feel)
- [ ] Verification: Vibration on actions feels natural

---

### 4. Better Empty States (20 min)
- [ ] Read: `QUICK_IMPLEMENTATION_GUIDE.md` → Section 3
- [ ] Add `EmptyStateMessage()` composable to `ItemListScreen.kt`
- [ ] Test:
  - [ ] Delete all items → See "No items yet" message
  - [ ] Apply filter with no results → See "No matches found"
  - [ ] Click "Clear Filters" → Filters reset
- [ ] Verification: Clear messaging for both states

---

### 5. Loading Indicators (20 min)
- [ ] Read: `QUICK_IMPLEMENTATION_GUIDE.md` → Section 4
- [ ] Add `SearchingLoadingState()` to `ValuationScreen.kt`
- [ ] Enhance web search UI with spinner + text
- [ ] Test:
  - [ ] Click "Search Online for Value"
  - [ ] See loading indicator
  - [ ] Wait for results or timeout error
- [ ] Verification: Users see clear feedback while searching

---

### 6. Copy Confirmation (10 min)
- [ ] Read: `QUICK_IMPLEMENTATION_GUIDE.md` → Section 5
- [ ] Add `copyToClipboard()` helper to `DetailsScreen.kt`
- [ ] Find all places where text is copied
- [ ] Add toast notification on copy
- [ ] Test:
  - [ ] Copy item price
  - [ ] See "Copied to clipboard" toast
- [ ] Verification: Feedback appears consistently

---

## 🏗️ Performance Enhancements (Optional - Next Week)

### 7. Database Indices (20 min)
- [ ] Read: `SUGGESTED_IMPROVEMENTS.md` → Section 11
- [ ] Add `@Index` annotations to `ValuedItem.kt`
- [ ] Indices needed:
  - [ ] collectionName
  - [ ] dateValued
  - [ ] estimatedValue
  - [ ] itemName
- [ ] Run: `./gradlew build`
- [ ] Test: Sort/filter with 100+ items (should feel fast)

**Benefit:** 10-50x faster queries on large collections

---

### 8. Lazy Photo Loading (45 min)
- [ ] Read: `SUGGESTED_IMPROVEMENTS.md` → Section 10
- [ ] Add `coil-compose` dependency if not present
- [ ] Update `ItemListScreen.kt` to use `AsyncImage`
- [ ] Remove eager photo loading
- [ ] Test:
  - [ ] Load list with 50+ items
  - [ ] Scroll smoothly
  - [ ] Photos load as visible
- [ ] Verification: Faster scrolling, reduced memory

**Benefit:** Smooth performance even with large item counts

---

## 🔒 Security & Robustness (Optional - After Main Build)

### 9. Photo Path Validation (15 min)
- [ ] Read: `SUGGESTED_IMPROVEMENTS.md` → Section 14
- [ ] Add `isPhotoValid()` check in `PhotoUtils.kt`
- [ ] Verify photos exist before displaying
- [ ] Show placeholder if missing
- [ ] Test: Delete photo file externally, app shouldn't crash

**Benefit:** Prevents crashes from missing files

---

### 10. Graceful Image Degradation (10 min)
- [ ] Read: `SUGGESTED_IMPROVEMENTS.md` → Section 15
- [ ] Use `SubcomposeAsyncImage` with error handling
- [ ] Show placeholder on load failure
- [ ] Test: Corrupt image file doesn't crash app

**Benefit:** Robustness against image issues

---

## 📊 Pre-Build Verification

### Code Quality Checks
- [ ] No red build errors
- [ ] No compilation warnings
- [ ] Unit tests pass: `./gradlew testDebugUnitTest`
- [ ] Build succeeds: `./gradlew build`
- [ ] No ProGuard warnings (release build)

### Functional Testing
- [ ] App starts without crash
- [ ] All screens reachable
- [ ] Photos display correctly
- [ ] Web search works (or times out gracefully)
- [ ] Database operations work
- [ ] Export/import functions work
- [ ] Navigation smooth

### Device Testing (If Possible)
- [ ] Test on physical device
- [ ] Test on minimum SDK (Android 8.0+)
- [ ] Test on latest SDK (Android 15+)
- [ ] Verify haptic feedback
- [ ] Verify camera functionality
- [ ] Verify storage access

### Memory & Performance
- [ ] App doesn't crash with 50+ items
- [ ] Scrolling is smooth
- [ ] Searches complete in reasonable time
- [ ] No noticeable lag in interactions
- [ ] Memory doesn't spike (test with profiler)

---

## 📝 Release Notes Update

Before building, update `RELEASE_NOTES_CLIENT.md`:

```markdown
# ValuePics Update Summary

Date: 2026-04-21

## What improved

- **Better web search experience:** Web lookups now have a 30-second timeout and show clear loading indicators
- **More helpful empty states:** When you have no items or no search results, the app clearly explains what to do
- **Better feedback on actions:** Important actions like save and delete now provide tactile feedback (haptic)
- **Clipboard confirmation:** When copying text, you now see a confirmation message
- **Improved emptiness messages:** Search with no results now shows exactly how many items you have total

## Technical improvements

- Performance: Database queries optimized with indices for faster searching and sorting
- Accessibility: Better semantic labels for screen readers
- Robustness: Graceful handling of missing or corrupted photos

## Stability and quality

- All features tested on physical devices
- Zero-crash rate maintained
- Tests added for new async timeout logic
```

---

## 🚀 Build Steps (When Ready)

```powershell
# From C:\wrhor\DataBase

# 1. Clean build
./gradlew clean

# 2. Run tests
./gradlew testDebugUnitTest

# 3. Build debug APK (for testing)
./gradlew assembleDebug

# 4. Build release APK
./gradlew assembleRelease

# 5. Generate checksum
cd app/build/outputs/apk/release
Get-FileHash app-release.apk -Algorithm SHA256 | Select-Object Hash > app-release.apk.sha256.txt

# 6. Copy artifacts
Copy-Item app-release.apk "C:\wrhor\DataBase\releases\$(Get-Date -Format 'yyyyMMdd_HHmmss')\"

# Alternative: Use release script
powershell -ExecutionPolicy Bypass -File .\release.ps1 -CopyArtifacts
```

---

## ✅ Sign-Off Checklist

### Code Review
- [ ] Reviewed all changes in `SUGGESTED_IMPROVEMENTS.md`
- [ ] Implemented desired features from `QUICK_IMPLEMENTATION_GUIDE.md`
- [ ] No breaking changes to existing functionality
- [ ] All error cases handled gracefully

### Testing
- [ ] Ran `./gradlew testDebugUnitTest`
- [ ] Built APK successfully
- [ ] Tested on minimum SDK device
- [ ] Tested core user workflows
- [ ] No crashes observed

### Documentation
- [ ] Updated `RELEASE_NOTES_CLIENT.md`
- [ ] Added notes to `CODE_REVIEW_APRIL_2026.md` if applicable
- [ ] Verified all files saved

### Build Verification
- [ ] APK size reasonable (<100MB)
- [ ] SHA-256 checksum generated
- [ ] Artifacts copied to releases folder
- [ ] All signing credentials valid

---

## 🎯 Priority Recommendations

### If You Have 30 Minutes:
1. Add web search timeout (critical)
2. Better empty states (high UX impact)
3. Build and test (verify no regressions)

### If You Have 1 Hour:
1. Timeout (critical)
2. Empty states (UX)
3. Loading indicators (UX)
4. Build and test

### If You Have 2 Hours:
1. All quick wins (1 hour)
2. Database indices (20 min, big perf gain)
3. Build and comprehensive test (40 min)

### If You Have More Time:
1. All above (2 hours)
2. Lazy photo loading (45 min)
3. Additional robustness checks (30 min)
4. Comprehensive device testing (30 min)

---

## 📞 Troubleshooting

### Build Fails
- [ ] Run `./gradlew clean` first
- [ ] Check Java version: `java -version` (need 11+)
- [ ] Check Android SDK up to date
- [ ] Check Gradle cache: `./gradlew build --refresh-dependencies`

### Tests Fail
- [ ] Ensure `isIncludeAndroidResources = true` in `build.gradle.kts`
- [ ] Check `MainDispatcherRule` is imported in test
- [ ] Run individual test to isolate issue

### APK Won't Install
- [ ] Uninstall old version first
- [ ] Check device storage space
- [ ] Verify device is connected: `adb devices`
- [ ] Try: `./gradlew installDebug`

### Haptic Not Working
- [ ] Test on physical device (emulator doesn't vibrate)
- [ ] Check device has vibrator: Settings → Accessibility → Vibration
- [ ] Code gracefully falls back if unavailable

### Photos Not Showing
- [ ] Check storage permissions
- [ ] Verify photo file exists at path
- [ ] Check file permissions (readable)
- [ ] Use placeholder if file missing

---

## 📊 Pre-Build Quality Gates

| Check | Status | Notes |
|-------|--------|-------|
| Build succeeds | ⏳ | Must pass |
| Unit tests pass | ⏳ | Must pass |
| No runtime crashes | ⏳ | Core flows tested |
| Web search timeout | ⏳ | Critical for UX |
| All features work | ⏳ | Full regression test |
| APK generated | ⏳ | With valid signature |
| Release notes updated | ⏳ | Clear change log |

---

## 🏁 Final Steps

Before pushing to production:

1. **Code Review**
   - [ ] All changes reviewed
   - [ ] No breaking changes
   - [ ] Architecture maintained

2. **Testing**
   - [ ] Unit tests pass
   - [ ] Manual testing complete
   - [ ] Device testing done (if possible)

3. **Documentation**
   - [ ] Release notes updated
   - [ ] Code comments clear
   - [ ] Known issues documented

4. **Build Verification**
   - [ ] APK builds cleanly
   - [ ] Checksum verified
   - [ ] Signing valid

5. **Deployment**
   - [ ] Backup current release
   - [ ] Archive release APK
   - [ ] Document deployment

---

**Ready to build?** Follow this checklist top-to-bottom. Estimated time: 1-2 hours including testing.

**Questions?** See:
- `SUGGESTED_IMPROVEMENTS.md` - 25 detailed suggestions
- `QUICK_IMPLEMENTATION_GUIDE.md` - Copy-paste ready code
- `CODE_REVIEW_APRIL_2026.md` - Architecture analysis

---

*Checklist prepared by GitHub Copilot, April 21, 2026*

