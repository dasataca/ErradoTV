package com.gareca.tv

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatAutoCompleteTextView

class SearchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    var onBackListener: (() -> Boolean)? = null
    private var backHandledOnDown = false

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                backHandledOnDown = onBackListener?.invoke() ?: false
                if (backHandledOnDown) return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (backHandledOnDown) {
                    backHandledOnDown = false
                    return true
                }
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }
}
