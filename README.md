# 💰 ValuePics - Photo-Based Item Valuation App

A powerful Android application that takes photos of items and automatically looks up their values on the internet.

## 🎯 Features

### Core Functionality
- 📸 **Photo Capture** - Take photos with device camera
- 🔍 **Image Recognition** - Detect objects in photos using ML Kit
- 💻 **Web Lookup** - Search internet for item values
- 💰 **Automatic Valuation** - Find estimated market values
- 💾 **Database Storage** - Store items with valuations
- 📊 **Statistics** - Track total value and averages
- 🔎 **Search & Filter** - Find previously valued items

### Valuation Features
- Multi-source price lookup
- Confidence scoring
- Manual value override
- Price history tracking
- Source attribution
- No paid API keys required for the initial lookup flow

## 🏗️ Architecture

```
Compose UI Layer
    ↓
ViewModel (State Management)
    ↓
Repository (Business Logic)
    ↓
Database DAO (Data Access)
    ↓
Room Database (SQLite)

Supporting Services:
- ImageRecognitionService (ML Kit)
- WebValuationService (Web Search)
- PhotoUtils (Photo Management)
```

## 📦 Project Structure

```
C:\wrhor\DataBase\

app/src/main/
├── java/com/example/valuefinder/
│   ├── ValuedItem.kt              # Data model
│   ├── ValuedItemDao.kt           # Database queries
│   ├── ValuePicsDatabase.kt       # Room setup
│   ├── ValuePicsRepository.kt     # Business logic
│   ├── ValuePicsViewModel.kt      # State management
│   ├── ImageRecognitionService.kt # Image analysis
│   ├── WebValuationService.kt     # Web search
│   ├── PhotoUtils.kt              # Photo utilities
│   ├── MainActivity.kt            # Entry point
│   └── ui/
│       ├── ValuePicsApp.kt        # Navigation
│       ├── ItemListScreen.kt      # List view
│       ├── ValuationScreen.kt     # Valuation form
│       ├── DetailsScreen.kt       # Item details
│       └── theme/
│           └── Theme.kt           # Material design
├── AndroidManifest.xml            # Permissions
├── res/
└── build.gradle.kts               # Build config
```

## 🚀 Getting Started

### Prerequisites
- Android Studio latest
- Android SDK 26+
- Kotlin 2.0.21
- Java 11+

### Build & Run
```bash
# Build APK
./gradlew build

# Install on device
./gradlew installDebug

# Run in Android Studio
Run → Run 'app'
```

### One-Click Release (Windows PowerShell)
```powershell
cd C:\wrhor\DataBase
powershell -ExecutionPolicy Bypass -File .\release.ps1
```

### Regenerate Notes PDFs (Windows PowerShell)
```powershell
cd C:\wrhor\DataBase
powershell -ExecutionPolicy Bypass -File .\make-notes-pdf.ps1
```

Optional flags:

```powershell
# Skip instrumentation tests (faster local packaging)
powershell -ExecutionPolicy Bypass -File .\release.ps1 -SkipConnectedTests

# Skip unit tests
powershell -ExecutionPolicy Bypass -File .\release.ps1 -SkipUnitTests

# Fail if git working tree has uncommitted changes
powershell -ExecutionPolicy Bypass -File .\release.ps1 -FailOnDirtyGit

# Copy APK + hash into releases\<timestamp>\
powershell -ExecutionPolicy Bypass -File .\release.ps1 -CopyArtifacts
```

This script runs instrumentation tests, builds the release APK, and writes a SHA-256 file at:

- `app/build/outputs/apk/release/app-release.apk.sha256.txt`

## 🧭 Canonical User Workflow

1. Tap `+` on the list screen.
2. Capture a photo or choose one from gallery.
3. Review detected labels and enter/edit item details.
4. Tap `Search Online for Value`.
5. Review estimated value, confidence, and source.
6. Tap `Save Valuation`.
7. Choose an existing collection or create a new one.
8. Open item details to edit, open source links, or delete if needed.

## 📋 Database Schema

### valued_items Table
```sql
id                  INTEGER PK
photoPath           TEXT
itemName            TEXT (required)
itemDescription     TEXT
detectedLabels      TEXT (JSON)
estimatedValue      REAL
currency            TEXT (default: USD)
valueSource         TEXT
sourceUrl           TEXT
searchResults       TEXT (JSON)
confidence          FLOAT (0-1)
dateTaken           TEXT
dateValued          INTEGER
notes               TEXT
```

## 🔗 API Integration Ready

The app is pre-configured to integrate with:

### Image Recognition APIs
- ✅ Google ML Kit (included)
- ⏳ Firebase ML Kit
- ⏳ AWS Rekognition
- ⏳ Microsoft Computer Vision
- ⏳ Custom ML models

### Current Price Lookup Strategy
- ✅ Public eBay listing page scrape
- ✅ DuckDuckGo HTML search fallback for visible price snippets
- ⏳ Official eBay API
- ⏳ Amazon Product Advertising API
- ⏳ Google Shopping API
- ⏳ Etsy API

### Implementation Notes
Current implementation already attempts live public web lookup without API keys. To improve reliability further, you can later add official API calls:

1. **eBay API**
```kotlin
// app/build.gradle.kts
implementation("com.ebay.api:ebay-api-java:1.0")
```

2. **Google Custom Search**
```kotlin
val searchResults = searchGoogle(query)
// Requires: API key and Custom Search Engine ID
```

3. **Amazon Advertising API**
```kotlin
// Requires: AWS credentials and Product API
```

## 💻 Usage Example

```kotlin
// Take photo and valuate
1. Tap + button
2. Camera opens - take photo
3. App analyzes image (detects: Watch, Jewelry, Vintage)
4. Enter item name or select detected label
5. Click "Search Online for Value"
6. App searches public web listings for similar items
7. Shows: Estimated value, confidence, sources
8. Confirm or manually override value
9. Save to database

Result: Item stored with photo, description, and valuation
```

## 🔐 Permissions

- **CAMERA** - Take photos
- **READ_EXTERNAL_STORAGE** - Access images
- **READ_MEDIA_IMAGES** - Android 13+ image access
- **INTERNET** - Web search for valuations

## 📊 Key Data Points

Each valued item stores:
- Photo path and display URI
- Item name and description
- Detected objects with confidence scores
- Estimated market value
- Valuation source (which website/service)
- Confidence score (0-100%)
- Multiple search results
- Timestamps
- Custom notes

## 🛠️ Customization

### Add Custom Valuation Logic
```kotlin
// WebValuationService.kt
suspend fun searchForValue(itemName: String, description: String): ValuationResult? {
    // Add custom pricing logic
}
```

### Enable Real API
```kotlin
// WebValuationService.kt
private suspend fun searchOnline(query: String): List<SearchResult> {
    // Extend current live lookup with official API calls when needed
}
```

### Change Currency
```kotlin
// ValuedItem.kt
currency: String = "EUR" // Change default from USD
```

## 📈 Features Roadmap

### Current (v1.0)
- ✅ Photo capture
- ✅ Image detection with ML Kit
- ✅ Best-effort live web search
- ✅ Database storage
- ✅ Search & filter

### Planned (v1.1+)
- ⏳ Real API integration (eBay, Amazon)
- ⏳ Advanced ML recognition
- ⏳ Price history tracking
- ⏳ Bulk valuation
- ⏳ Export valuations
- ⏳ Cloud backup
- ⏳ Multi-currency support
- ⏳ Valuation alerts

## 🔍 Testing

### Manual Testing
1. Build and install app
2. Grant camera permission
3. Take photo of item
4. Auto-detect should show labels
5. Tap "Search Online for Value" and review live best-effort lookup results
6. Save and view in list
7. Check statistics update

### Integration Testing
```kotlin
// Test with different item types:
- Watch (should detect Jewelry, Vintage)
- Camera (should detect Photography equipment)
- Phone (should detect Electronics)
- Furniture (should detect Home items)
```

## 🐛 Troubleshooting

### Camera Not Working
- Grant camera permission
- Restart app
- Check device has camera

### Web Search Not Finding Items
- Item name too generic (add more description)
- Internet not connected
- Public site markup may have changed
- No visible price snippets were found for that search

### Photos Not Displaying
- Check storage permission
- Verify file path in database
- Check free storage space

## 📞 Integration Guide

To integrate real APIs:

1. **Get API Keys**
   - Sign up for eBay, Amazon, Google services
   - Get API keys/credentials

2. **Update Dependencies**
   - Add library in build.gradle.kts
   - Add in gradle/libs.versions.toml

3. **Implement Service**
   - Update WebValuationService.kt
   - Extend current live lookup with official APIs

4. **Handle Responses**
   - Parse API responses
   - Extract prices
   - Update ValuationResult

## 📝 Configuration Files

### build.gradle.kts
```kotlin
// Network
implementation("com.squareup.okhttp3:okhttp:4.11.0")
implementation("com.google.code.gson:gson:2.10.1")

// ML Kit
implementation("com.google.mlkit:image-labeling:12.0.0")

// Retrofit
implementation("com.squareup.retrofit2:retrofit:2.9.0")
```

### gradle/libs.versions.toml
```toml
[versions]
room = "2.6.1"
composeBom = "2024.09.00"
```

## 🎨 UI/UX

### Screens
- **List Screen** - All valued items with total/average
- **Camera Screen** - Photo capture
- **Valuation Screen** - Form with web search results
- **Details Screen** - View full item details

### Material Design 3
- Modern color scheme
- Responsive layout
- Smooth transitions
- Accessible components

## 📱 System Requirements

- Android: 8.0+ (API 26)
- RAM: 2GB minimum
- Storage: 50MB+
- Camera: Required
- Internet: Required for valuations

## 📄 License

Copyright (c) 2026 Wally Horsman, Queensland Australia. All rights reserved.
Email: wrhorsman@gmail.com

This project is provided as-is for development and personal use. See `LICENSE` and `NOTICE`.

## 🎉 Next Steps

1. Build and run the app
2. Test basic functionality
3. Get API keys for real integrations
4. Implement real web search
5. Deploy to Play Store

---

**Ready to valuate items with photos!** 📸💰

For more details, see the source code and inline comments.

