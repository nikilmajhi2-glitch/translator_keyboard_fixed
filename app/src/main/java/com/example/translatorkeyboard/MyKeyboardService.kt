package com.example.translatorkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.translatorkeyboard.databinding.KeyboardViewBinding
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var binding: KeyboardViewBinding? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        val root = inflater.inflate(R.layout.keyboard_view, null)
        binding = KeyboardViewBinding.bind(root)

        keyboard = Keyboard(this, R.xml.qwerty)
        binding?.keyboardView?.keyboard = keyboard
        binding?.keyboardView?.setOnKeyboardActionListener(this)

        binding?.btnTranslate?.setOnClickListener {
            translateCurrentText()
        }

        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun getInputConnection(): InputConnection? = currentInputConnection

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // You can customize per app here
    }

    // Keyboard callbacks
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = getInputConnection() ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_ENTER
                    )
                )
            }
            else -> {
                val code = primaryCode.toChar()
                ic.commitText(code.toString(), 1)
            }
        }
    }

    private fun translateCurrentText() {
        val ic = getInputConnection()
        if (ic == null) {
            Toast.makeText(this, "No input connection", Toast.LENGTH_SHORT).show()
            return
        }

        // Try to grab selected text first, fallback to surrounding text
        val selected = ic.getSelectedText(0)?.toString()
        val toTranslate = when {
            !selected.isNullOrEmpty() -> selected
            else -> ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString()
        }

        if (toTranslate.isNullOrBlank()) {
            Toast.makeText(this, "Type something to translate", Toast.LENGTH_SHORT).show()
            return
        }

        binding?.progressBar?.isVisible = true
        binding?.btnTranslate?.isEnabled = false

        serviceScope.launch {
            try {
                val translated = withContext(Dispatchers.IO) {
                    translateWithGemini(toTranslate, "Hindi")
                }

                // Replace selected text or append translated text
                if (!selected.isNullOrEmpty()) {
                    ic.commitText(translated, 1)
                } else {
                    ic.commitText("\n$translated", 1)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyKeyboardService, "Translate error: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding?.progressBar?.isVisible = false
                binding?.btnTranslate?.isEnabled = true
            }
        }
    }

    private suspend fun translateWithGemini(text: String, targetLang: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return "[Gemini API key missing]"
        }

        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )

        val prompt = "Translate this text to ${'$'}targetLang. Only return the translated sentence, no explanation.\nText: ${'$'}text"

        val response = model.generateContent(prompt)
        return response.text ?: "[No response]"
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
