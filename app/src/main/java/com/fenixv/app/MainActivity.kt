package com.fenixv.app

import java.io.IOException

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import okhttp3.Response
import org.json.JSONObject
import android.view.View
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import androidx.constraintlayout.widget.ConstraintLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.fenixv.app.databinding.ActivityMainBinding
import android.util.Log

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var isActive = false
    private var lastTranslation: String = ""
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var tts: TextToSpeech
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        setupListeners()
        initClipboard()
        updateToggleUI()
    }

    private fun setupListeners() {
        binding.btnToggle.setOnClickListener {
            isActive = !isActive
            updateToggleUI()
            if (isActive) {
                Toast.makeText(this, getString(R.string.status_active), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.status_paused), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRepeat.setOnClickListener {
            repeatLastTranslation()
        }
    }

    private fun updateToggleUI() {
        if (isActive) {
            binding.btnToggle.text = getString(R.string.pause)
            binding.btnToggle.setBackgroundColor(Color.parseColor("#4CAF50")) // verde
        } else {
            binding.btnToggle.text = getString(R.string.start)
            binding.btnToggle.setBackgroundColor(Color.parseColor("#F44336")) // rojo
        }
    }

    private fun initClipboard() {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener {
            if (isActive) {
                handleClipboard()
            }
        }
    }

    private fun handleClipboard() {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString().trim()
            if (text.isNotEmpty()) {
                if (text.length > 500) {
                    showError(getString(R.string.error_long_text))
                } else {
                    translateAndShow(text)
                }
            }
        }
    }

    private fun translateAndShow(original: String) {
        lifecycleScope.launch {
            try {
                val translation = fetchTranslation(original)
                withContext(Dispatchers.Main) {
                    binding.tvOriginal.text = original
                    binding.tvTranslation.text = translation
                    lastTranslation = translation
                    speak(translation)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Translation error", e)
                withContext(Dispatchers.Main) {
                    showError(getString(R.string.error_translation))
                }
            }
        }
    }

    private suspend fun fetchTranslation(text: String): String = withContext(Dispatchers.IO) {
        val url = "https://libretranslate.de/translate"
        val formBody = FormBody.Builder()
            .add("q", text)
            .add("source", "fr")
            .add("target", "es")
            .add("format", "text")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val json = response.body?.string() ?: throw IOException("Empty response")
            val obj = JSONObject(json)
            return@withContext obj.getString("translatedText")
        }
    }

    private fun speak(text: String) {
        if (tts.isLanguageAvailable(Locale("es", "ES")) >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = Locale("es", "ES")
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
    }

    private fun repeatLastTranslation() {
        if (lastTranslation.isNotEmpty()) {
            speak(lastTranslation)
        } else {
            Toast.makeText(this, getString(R.string.error_no_translation), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        binding.tvTranslation.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onInit(status: Int) {
        // No extra initialization needed
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        clipboardManager.removePrimaryClipChangedListener { }
    }
}