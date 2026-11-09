package com.chronosbvrx.visionplus.ui.profile

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import kotlinx.coroutines.delay

/**
 * Campo para escribir el nombre de un perfil con el **teclado predeterminado del sistema**
 * (el IME de Android/Android TV), no con un teclado dibujado por la app.
 *
 * Se usa un `EditText` real dentro de un `AndroidView` a propósito: en la tele, un
 * `BasicTextField` de Compose a veces recibe el foco pero el IME no se abre; el `EditText`
 * de toda la vida pide `showSoftInput` al enfocarse y funciona igual en móvil que en tele.
 * El campo se enfoca solo al abrir el diálogo y, si el usuario cierra el teclado, basta
 * tocar/OK en el campo para volver a abrirlo.
 */
@Composable
internal fun ProfileNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = 24,
    autoFocus: Boolean = true,
    textSizeSp: Float = 20f,
    backgroundColor: Color = VisionSurfaceHigh,
    /** Color del aro al enfocar (el editor de perfiles usa el cian del foco de la tele). */
    accentColor: Color = VisionBlue,
    onDone: () -> Unit = {},
) {
    val density = LocalDensity.current
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnDone by rememberUpdatedState(onDone)
    var field by remember { mutableStateOf<EditText?>(null) }

    fun paint(edit: EditText, focused: Boolean) {
        val corner = with(density) { 14f * this.density }
        edit.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor.toArgb())
            this.cornerRadius = corner
            if (focused) {
                setStroke(with(density) { 2f * this.density }.toInt(), accentColor.toArgb())
            } else {
                setStroke(with(density) { 1f * this.density }.toInt(), 0x26FFFFFF)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            EditText(ctx).apply {
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_DONE
                filters = arrayOf(InputFilter.LengthFilter(maxLength))
                setTextColor(android.graphics.Color.WHITE)
                setHintTextColor(0x99FFFFFF.toInt())
                hint = "Escribe el nombre"
                textSize = textSizeSp
                setPadding(
                    with(density) { 16.dp.toPx() }.toInt(),
                    with(density) { 12.dp.toPx() }.toInt(),
                    with(density) { 16.dp.toPx() }.toInt(),
                    with(density) { 12.dp.toPx() }.toInt(),
                )
                includeFontPadding = false
                setSelectAllOnFocus(false)
                paint(this, isFocused)
                setText(latestValue)
                setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val text = s?.toString().orEmpty()
                        if (text != latestValue) latestOnValueChange(text)
                    }
                })
                setOnFocusChangeListener { view, hasFocus ->
                    paint(view as EditText, hasFocus)
                    if (hasFocus) {
                        // En Android TV el foco no basta: hay que pedir el IME explícitamente.
                        view.postDelayed({ if (view.isFocused) showSystemKeyboard(view) }, 120)
                    }
                }
                setOnEditorActionListener { view, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        if (view is EditText) hideSystemKeyboard(view)
                        latestOnDone()
                        true
                    } else {
                        false
                    }
                }
                setOnClickListener { showSystemKeyboard(this) }
                field = this
            }
        },
        update = { edit ->
            if (edit.text.toString() != value) {
                edit.setText(value)
                edit.setSelection(edit.text.length)
            }
        },
        modifier = modifier.fillMaxWidth(),
    )

    LaunchedEffect(autoFocus, field) {
        if (autoFocus && field != null) {
            // La ventana del diálogo tarda unos frames en aceptar el foco; después se pide
            // el teclado del sistema (el mismo de cualquier campo de Android TV). En teles
            // lentas el primer `showSoftInput` puede llegar antes de que la ventana esté
            // lista, así que se reintenta sin spamear.
            repeat(4) { attempt ->
                delay(if (attempt == 0) 220 else 260)
                if (field == null) return@LaunchedEffect
                field?.requestFocus()
                if (showSystemKeyboard(field)) return@LaunchedEffect
            }
        }
    }
}

private fun showSystemKeyboard(view: EditText?): Boolean {
    view ?: return false
    view.requestFocus()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    return imm?.showSoftInput(view, 0) == true
}

private fun hideSystemKeyboard(view: EditText) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}
