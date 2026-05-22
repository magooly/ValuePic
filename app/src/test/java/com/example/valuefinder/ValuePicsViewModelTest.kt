package com.example.valuefinder

import com.example.valuefinder.ui.AppUiEvent
import com.example.valuefinder.ui.AppUiState
import com.example.valuefinder.ui.UiError
import com.example.valuefinder.ui.appUiStateReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ValuePicsViewModelTest {


    @Test
    fun photoTargetEvent_updatesRequestedSize() {
        val initial = AppUiState(photoTargetSizeKb = 512)

        val updated = appUiStateReducer(initial, AppUiEvent.SetPhotoTargetSize(1024))

        assertEquals(1024, updated.photoTargetSizeKb)
    }

    @Test
    fun errorEvent_setsAndClearsError() {
        val initial = AppUiState()

        val withError = appUiStateReducer(
            initial,
            AppUiEvent.SetError(UiError.GeneralError("boom"))
        )
        val cleared = appUiStateReducer(withError, AppUiEvent.ClearError)

        assertTrue(withError.currentError is UiError.GeneralError)
        assertNull(cleared.currentError)
    }

    @Test
    fun dialogEvents_toggleRestoreDialogFlag() {
        val shown = appUiStateReducer(AppUiState(), AppUiEvent.ShowRestoreConfirmDialog)
        val hidden = appUiStateReducer(shown, AppUiEvent.HideRestoreConfirmDialog)

        assertTrue(shown.showRestoreConfirmDialog)
        assertFalse(hidden.showRestoreConfirmDialog)
    }
}

