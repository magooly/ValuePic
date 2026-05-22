# ValuePics New PC Setup Checklist

This checklist lets you move the project to another Windows PC and build both app variants:
- Main (internal flavor id: `abc`)
- Other (internal flavor id: `xyz`)

## 1) Install prerequisites

1. Android Studio (includes SDK Manager)
2. JDK 17 (or Android Studio embedded JDK)
3. Android SDK components:
   - Platform Tools
   - Build Tools (matching project requirements)
   - Android API 36 platform
4. Git (optional, but recommended)

## 2) Restore project folder

Restore the full project folder to a path such as:

- `C:\wrhor\DataBase`

## 3) Configure local SDK path

Update `local.properties` (or create it if missing):

```properties
sdk.dir=C:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk
```

## 4) (Optional) Configure release signing

For signed release builds, ensure these are available (Gradle properties or env vars):

- `VALUEFINDER_STORE_FILE`
- `VALUEFINDER_STORE_PASSWORD`
- `VALUEFINDER_KEY_ALIAS`
- `VALUEFINDER_KEY_PASSWORD`

If you use a keystore file, restore it to the expected location too.

## 5) Verify both variants compile

Run from project root:

```powershell
cd C:\wrhor\DataBase
.\gradlew assembleAbcDebug assembleXyzDebug
```

## 6) Build and sync release APKs for distribution

Use the one-command script:

```powershell
& "C:\wrhor\DataBase\Distribution\build-and-sync.ps1"
```

Expected outputs:

- `C:\wrhor\DataBase\Distribution\ValuePics-Main-Release.apk`
- `C:\wrhor\DataBase\Distribution\ValuePics-Other-Release.apk`

## 7) Quick sanity checks

- App labels should be:
  - `ValuePics Main`
  - `ValuePics Other`
- Default backup ZIP names should be flavor-specific:
  - `VPicMain-<date-time>.zip`
  - `VPicOther-<date-time>.zip`

## Notes

- Internal Gradle flavor IDs remain `abc` and `xyz` for compatibility.
- User-facing naming is Main/Other.

