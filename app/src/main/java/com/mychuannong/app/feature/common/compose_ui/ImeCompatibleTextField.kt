package com.mychuannong.app.feature.common.compose_ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.doAfterTextChanged
import com.mychuannong.app.core.theme.AppTheme

/**
 * Platform EditText bridge for IMEs that cannot keep a usable Compose input connection.
 *
 * A plain EditText is intentional here: the app's legacy density adaptation can offset the
 * internal touch target of TextInputLayout when hosted by AndroidView on large-screen devices.
 * Keeping the editable view as the AndroidView root makes its drawn and touch bounds identical.
 */
@Composable
fun ImeCompatibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    numeric: Boolean = false,
    password: Boolean = false,
    imeAction: Int = if (singleLine) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_NONE,
    showSearchIcon: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnClear by rememberUpdatedState(onClear)
    // A rejected edit may leave the caller's value unchanged. Still reconcile the native
    // buffer on that edit, including when the password has reached its length limit.
    var nativeValue by remember { mutableStateOf(value) }
    // Observe during composition, not inside AndroidView.update: the latter could run with
    // the old caller value before its recomposition has accepted the same native edit.
    val observedNativeValue = nativeValue
    val textColor = colors.primaryTextColor.toArgb()
    val hintColor = colors.secondaryTextColor.toArgb()
    val borderColor = colors.commonColor.toArgb()
    val containerColor = colors.bgCardColor.toArgb()
    val inputRef = remember { arrayOfNulls<AppCompatEditText>(1) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
        ) {
            inputRef[0]?.let { input ->
                input.requestFocus()
                input.showKeyboard()
            }
        },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                val horizontalPadding = (18 * density).toInt()
                val verticalPadding = (13 * density).toInt()
                val iconSize = (22 * density).toInt()

                ImeEditText(context).apply {
                    inputRef[0] = this
                    background = fieldBackground(containerColor, borderColor, density)
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    setText(value)
                    setTextColor(textColor)
                    setHintTextColor(hintColor)
                    hint = fieldHint(label, placeholder)
                    textSize = 16f
                    gravity = if (singleLine) Gravity.CENTER_VERTICAL else Gravity.TOP or Gravity.START
                    configureSingleLine(singleLine)
                    setMinLines(minLines)
                    setMaxLines(maxLines)
                    inputType = desiredInputType(numeric, password, singleLine)
                    if(password) {
                        transformationMethod = PasswordTransformationMethod.getInstance()
                        isSaveEnabled = false
                        if(android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    }
                    setSelection(length())
                    imeOptions = imeAction
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    showSoftInputOnFocus = true
                    setCompoundDrawablePadding((10 * density).toInt())
                    updateIcons(showSearchIcon, onClear != null && value.isNotEmpty(), hintColor, iconSize)
                    doAfterTextChanged { editable ->
                        val text = editable?.toString().orEmpty()
                        nativeValue = text
                        latestOnValueChange(text)
                    }
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) showKeyboard()
                    }
                    setOnTouchListener { _, event ->
                        val endDrawable = compoundDrawablesRelative[2]
                        if (
                            event.action == MotionEvent.ACTION_UP &&
                            endDrawable != null &&
                            event.x >= width - paddingEnd - endDrawable.bounds.width()
                        ) {
                            latestOnClear?.invoke()
                            return@setOnTouchListener true
                        }
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            requestFocus()
                            showKeyboard()
                        }
                        false
                    }
                    setOnClickListener {
                        requestFocus()
                        showKeyboard()
                    }
                }
            },
            update = { input ->
                // Capture both ends before setters which may reset TextView's selection.
                val selectionStart = input.selectionStart
                val selectionEnd = input.selectionEnd
                val caretAtEnd = selectionStart == input.length() && selectionEnd == input.length()
                val density = input.resources.displayMetrics.density
                input.background = fieldBackground(containerColor, borderColor, density)
                input.setTextColor(textColor)
                input.setHintTextColor(hintColor)
                input.hint = fieldHint(label, placeholder)
                // Reapplying this on every keystroke changes the password transformation
                // and rebuilds the editable buffer, losing its caret and composing spans.
                input.configureSingleLine(singleLine)
                if (input.minLines != minLines) input.setMinLines(minLines)
                if (input.maxLines != maxLines) input.setMaxLines(maxLines)
                val desiredType = desiredInputType(numeric, password, singleLine)
                if (input.inputType != desiredType) input.inputType = desiredType
                if(password) {
                    if (input.transformationMethod !is PasswordTransformationMethod) {
                        input.transformationMethod = PasswordTransformationMethod.getInstance()
                    }
                    input.isSaveEnabled = false
                    if(android.os.Build.VERSION.SDK_INT >= 26) input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }
                if (input.imeOptions != imeAction) input.imeOptions = imeAction
                input.updateIcons(
                    showSearchIcon = showSearchIcon,
                    showClearIcon = onClear != null && value.isNotEmpty(),
                    tintColor = hintColor,
                    iconSize = (22 * density).toInt(),
                )
                if (observedNativeValue != value) {
                    // A previous update may already have applied this value. Never rewrite
                    // an equal buffer: setText would discard the IME's composing region.
                    if (input.text?.toString() != value) input.setText(value)
                    nativeValue = value
                }
                // Normal typing stays at the end; deliberate cursor/range edits stay put.
                // Clamp after upstream validation (e.g. the 200-character password limit).
                val length = input.length()
                val start = if (caretAtEnd || selectionStart < 0) length else selectionStart.coerceAtMost(length)
                val end = if (caretAtEnd || selectionEnd < 0) length else selectionEnd.coerceAtMost(length)
                if (input.selectionStart != start || input.selectionEnd != end) {
                    input.setSelection(start, end)
                }
            },
        )
        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                color = colors.secondaryTextColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
    }
}

/** Tracks line configuration per native view without the API 29-only isSingleLine getter. */
private class ImeEditText(context: Context) : AppCompatEditText(context) {
    private var configuredSingleLine: Boolean? = null

    fun configureSingleLine(singleLine: Boolean) {
        if (configuredSingleLine != singleLine) {
            setSingleLine(singleLine)
            configuredSingleLine = singleLine
        }
    }
}

private fun desiredInputType(
    numeric: Boolean,
    password: Boolean,
    singleLine: Boolean,
): Int =
    if (password) {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    } else if (numeric) {
        InputType.TYPE_CLASS_NUMBER
    } else {
        InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

private fun fieldHint(label: String?, placeholder: String?): String? = when {
    !label.isNullOrBlank() && !placeholder.isNullOrBlank() -> "$label · $placeholder"
    !label.isNullOrBlank() -> label
    else -> placeholder
}

private fun fieldBackground(containerColor: Int, borderColor: Int, density: Float) =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18 * density
        setColor(containerColor)
        setStroke((1 * density).coerceAtLeast(1f).toInt(), borderColor)
    }

private fun AppCompatEditText.showKeyboard() {
    post {
        context.getSystemService<InputMethodManager>()
            ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

private fun AppCompatEditText.updateIcons(
    showSearchIcon: Boolean,
    showClearIcon: Boolean,
    tintColor: Int,
    iconSize: Int,
) {
    fun icon(resource: Int): Drawable? = context.getDrawable(resource)?.mutate()?.also { drawable ->
        drawable.setBounds(0, 0, iconSize, iconSize)
        DrawableCompat.setTint(drawable, tintColor)
    }
    val start = if (showSearchIcon) icon(android.R.drawable.ic_menu_search) else null
    val end = if (showClearIcon) icon(android.R.drawable.ic_menu_close_clear_cancel) else null
    setCompoundDrawablesRelative(start, null, end, null)
}
