package com.saubh.deskbuddy.ime

import android.app.Activity
import android.view.inputmethod.InputMethodManager

/**
 * Invisible: the keyboard picker needs a focused window, which a notification tap alone does not
 * give. Shows the picker once focused, and finishes when focus returns after the picker closes.
 * If the picker never appears (some OEMs ignore the request), it finishes anyway so this
 * transparent window cannot swallow taps.
 */
class KeyboardPickerActivity : Activity() {
    private companion object {
        const val PICKER_TIMEOUT_MS = 1_500L
    }

    private var pickerShown = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (pickerShown) {
            finish()
        } else {
            pickerShown = true
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            window.decorView.postDelayed({ if (hasWindowFocus()) finish() }, PICKER_TIMEOUT_MS)
        }
    }
}
