package com.example.valuefinder.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.valuefinder.AppTier
import com.example.valuefinder.BILLS_COLLECTION_NAME
import com.example.valuefinder.R
import com.example.valuefinder.ValuePicsViewModel
import com.example.valuefinder.ui.theme.ValuePicsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BillsUiInstrumentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun personalTier_showsBillsAndPeriodOptionsAfterSelectingBillsCollection() {
        val context = composeRule.activity
        val viewModel = ValuePicsViewModel(context)

        composeRule.setContent {
            ValuePicsTheme(appTier = AppTier.PERSONAL) {
                ValuationScreen(
                    photoPath = "",
                    photoSource = "camera",
                    existingCollections = listOf("Cars and Bikes"),
                    existingTags = emptyList(),
                    isValuating = false,
                    viewModel = viewModel,
                    onSave = {},
                    onSaveAndAddAnother = {},
                    currentRecordCount = 0,
                    recordLimit = 12,
                    isUnlimitedUnlocked = true,
                    unlockAccountId = "",
                    onUnlockWithPassword = { _, _, _ -> },
                    onRestoreUnlimited = { _, _, _ -> },
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeSelected = {},
                    appTier = AppTier.PERSONAL,
                    onAppTierSelected = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.valuation_category_none_selected)).assertCountEquals(1)
        composeRule.onAllNodesWithText(BILLS_COLLECTION_NAME).assertCountEquals(0)

    }

    @Test
    fun insuranceTier_hidesBillsFromCategoryPicker() {
        val context = composeRule.activity
        val viewModel = ValuePicsViewModel(context)

        composeRule.setContent {
            ValuePicsTheme(appTier = AppTier.INSURANCE) {
                ValuationScreen(
                    photoPath = "",
                    photoSource = "camera",
                    existingCollections = listOf(BILLS_COLLECTION_NAME, "Cars and Bikes", "Jewelry"),
                    existingTags = emptyList(),
                    isValuating = false,
                    viewModel = viewModel,
                    onSave = {},
                    onSaveAndAddAnother = {},
                    currentRecordCount = 0,
                    recordLimit = 12,
                    isUnlimitedUnlocked = true,
                    unlockAccountId = "",
                    onUnlockWithPassword = { _, _, _ -> },
                    onRestoreUnlimited = { _, _, _ -> },
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeSelected = {},
                    appTier = AppTier.INSURANCE,
                    onAppTierSelected = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodesWithText(BILLS_COLLECTION_NAME).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.valuation_category_none_selected)).assertCountEquals(1)
    }
}
