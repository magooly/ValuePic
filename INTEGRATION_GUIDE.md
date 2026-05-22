# Integration Guide: Using the Refactored Code

## Quick Start

### Step 1: Verify Dependencies Installed
The following have been added to your build configuration:
```gradle
- Hilt (2.48)
- EncryptedSharedPreferences
- Mockito for testing
- JUnit KTX for testing
```

### Step 2: Replace ValuePicsApp (Optional)
The refactored version is in: `ValuePicsAppRefactored.kt`

To use it:
```kotlin
// Option A: Copy contents
// Copy all code from ValuePicsAppRefactored.kt
// Paste into existing ValuePicsApp.kt
// Delete ValuePicsAppRefactored.kt

// Option B: Simple rename
// Rename ValuePicsApp.kt to ValuePicsApp_OLD.kt
// Rename ValuePicsAppRefactored.kt to ValuePicsApp.kt
```

### Step 3: Run Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (on device/emulator)
./gradlew connectedAndroidTest
```

---

## Key Classes Reference

### AppUiState
Located in: `ui/AppUiState.kt`

Centralized UI state containing all screen, dialog, and operation states.

```kotlin
// Emit an event to change state
val newState = appUiStateReducer(currentState, AppUiEvent.NavigateTo(ValuePicsScreen.DETAILS))

// Available events:
AppUiEvent.NavigateTo(screen)              // Change screen
AppUiEvent.SetPhotoPath(path, source)      // Set photo for valuation
AppUiEvent.ShowDeleteConfirmDialog         // Show delete confirmation
AppUiEvent.SetError(error)                 // Set error state
AppUiEvent.SetThemeMode(mode)              // Change theme
// ... and many more
```

### SecurePreferencesManager
Located in: `util/SecurePreferencesManager.kt`

Encrypted storage for sensitive preferences.

```kotlin
val prefs = SecurePreferencesManager(context)

// Store values (automatically encrypted)
prefs.putString("key", "value")
prefs.putBoolean("feature_enabled", true)
prefs.putInt("count", 42)
prefs.putLong("timestamp", System.currentTimeMillis())

// Retrieve values
val value = prefs.getString("key", "default")
val enabled = prefs.getBoolean("feature_enabled", false)

// Available constants
SecurePreferencesManager.PREF_THEME_MODE
SecurePreferencesManager.PREF_PHOTO_TARGET_SIZE
SecurePreferencesManager.PREF_LAST_BACKUP
```

### RetryUtil
Located in: `util/RetryUtil.kt`

Automatic retry logic with exponential backoff.

```kotlin
val result = RetryUtil.withRetry(
    maxAttempts = 3,
    initialDelayMillis = 100,
    maxDelayMillis = 5000,
    backoffMultiplier = 2.0,
    shouldRetry = RetryUtil::isRetryable
) {
    // Your operation here
    someNetworkCall()
}
```

### UiError
Located in: `ui/AppUiState.kt`

Type-safe error representation.

```kotlin
val error: UiError = when(e) {
    is PdfException -> UiError.PdfError(e.message!!)
    is CsvException -> UiError.CsvError(e.message!!)
    else -> UiError.GeneralError(e.message ?: "Unknown error")
}
```

### Extracted Dialogs
Located in: `ui/dialogs/CommonDialogs.kt`

Reusable dialog composables.

```kotlin
// PDF Export Result Dialog
PdfExportResultDialog(
    result = pdfResult,
    onDismiss = { /* handle dismiss */ },
    onOpen = { /* open pdf */ },
    onPrint = { /* print pdf */ },
    onShare = { /* share pdf */ }
)

// Error Dialog
ErrorDialog(
    message = "Something went wrong",
    onDismiss = { /* handle dismiss */ }
)

// And more...
```

---

## Using Hilt in New Screens

If you create a new screen, use Hilt:

```kotlin
// In your ViewModel
@HiltViewModel
class MyNewViewModel @Inject constructor(
    private val repository: ValuePicsRepository,
    private val preferencesManager: SecurePreferencesManager
) : ViewModel() {
    // Your implementation
}

// In your Composable
@Composable
fun MyNewScreen() {
    val viewModel: MyNewViewModel = hiltViewModel()
    // Use viewModel
}
```

---

## Running Tests

### Unit Tests (ValuePicsViewModelTest)
```bash
# Run specific test
./gradlew test --tests "*.ValuePicsViewModelTest"

# Run all tests
./gradlew test
```

### Instrumented Tests (SecurePreferencesManagerTest)
```bash
# Run on connected device/emulator
./gradlew connectedAndroidTest

# Run specific test
./gradlew connectedAndroidTest --tests "*.SecurePreferencesManagerTest"
```

### Test Results Location
```
app/build/reports/tests/testDebugUnitTest/index.html
app/build/reports/androidTests/
```

---

## Adding New Tests

### Unit Test Template
```kotlin
@RunWith(AndroidJUnit4::class)
class MyNewTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var viewModel: ValuePicsViewModel

    @Before
    fun setUp() {
        // Initialize
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun testSomething() = runTest {
        // Arrange
        val input = "test"
        
        // Act
        val result = viewModel.performAction(input)
        
        // Assert
        assert(result.isSuccess)
    }
}
```

---

## Common Patterns

### Handling Errors from ViewModel
```kotlin
val operationError by viewModel.operationError.collectAsStateWithLifecycle()

if (operationError != null) {
    ErrorDialog(
        message = when(operationError) {
            is UiError.PdfError -> operationError.message
            is UiError.DatabaseError -> operationError.message
            else -> "Unknown error"
        },
        onDismiss = { viewModel.clearError() }
    )
}
```

### State Transitions in Composable
```kotlin
fun emitEvent(event: AppUiEvent) {
    uiState = appUiStateReducer(uiState, event)
}

// Usage
emitEvent(AppUiEvent.NavigateTo(ValuePicsScreen.DETAILS))
emitEvent(AppUiEvent.ShowDeleteConfirmDialog)
emitEvent(AppUiEvent.SetError(UiError.GeneralError("Failed")))
```

### Retrying Failed Operations
```kotlin
scope.launch {
    try {
        val result = RetryUtil.withRetry {
            viewModel.exportCollectionSummaryPdf()
        }
        emitEvent(AppUiEvent.SetPdfExportResult(result))
    } catch (e: Exception) {
        emitEvent(AppUiEvent.SetError(
            UiError.PdfError("Failed to export: ${e.message}")
        ))
    }
}
```

### Saving Preferences Securely
```kotlin
val preferencesManager = SecurePreferencesManager(context)

// Save theme preference
preferencesManager.putString(
    SecurePreferencesManager.PREF_THEME_MODE,
    selectedTheme.name
)

// Load theme preference
val themeName = preferencesManager.getString(
    SecurePreferencesManager.PREF_THEME_MODE,
    ThemeMode.SYSTEM.name
)
val theme = parseThemeMode(themeName)
```

---

## Debugging Tips

### Enable Logging
Logging is already configured in:
- `MainActivity.kt` - Lifecycle events
- `ValuePicsViewModel.kt` - Operation progress
- `RetryUtil.kt` - Retry attempts

View logs with:
```bash
./gradlew connectedAndroidTest -i
```

### Check State in Debugger
Set breakpoints in `appUiStateReducer()` to see state transitions:

```kotlin
fun appUiStateReducer(state: AppUiState, event: AppUiEvent): AppUiState {
    // Breakpoint here to inspect event and resulting state
    return when (event) {
        // ...
    }
}
```

### Test Preferences
```kotlin
val prefs = SecurePreferencesManager(context)
prefs.putString("test", "value")
val retrieved = prefs.getString("test")
// Retrieved should be "value" even though it's encrypted
```

---

## Performance Considerations

### State Management
- `AppUiState` is immutable - safe for concurrent access
- Each event creates a new state object (small overhead)
- Use `remember` for computed values in Composables

### Preferences
- Encrypted preferences have slight performance overhead
- Use lazy loading for large data
- Batch writes when possible

### Retry Logic
- Default: 3 attempts with exponential backoff (100ms → 200ms → 400ms)
- Customizable for different scenarios
- Network-aware retry classification

---

## Troubleshooting

### Issue: Hilt not finding dependencies
**Solution**: Ensure `@HiltAndroidApp` is on `ValueFinderApplication`

### Issue: Tests fail with "No Hilt binding"
**Solution**: Use `@RunWith(HiltAndroidRule::class)` for instrumented tests

### Issue: Preferences not persisting
**Solution**: Ensure you're using `SecurePreferencesManager` not `SharedPreferences`

### Issue: Retry loop not working
**Solution**: Check `RetryUtil.isRetryable()` predicate for your error type

---

## Migration Checklist

- [ ] Dependencies installed successfully
- [ ] Project compiles without errors
- [ ] Unit tests pass
- [ ] Instrumented tests pass
- [ ] All features work as before
- [ ] No performance regression
- [ ] Logging appears correctly
- [ ] Preferences saved correctly
- [ ] Error dialogs display properly
- [ ] State transitions work smoothly

---

## Support

For issues or questions:
1. Check the test files for usage examples
2. Review the AppUiState documentation
3. Inspect the refactored ValuePicsApp for patterns
4. Refer to Hilt documentation: https://dagger.dev/hilt

