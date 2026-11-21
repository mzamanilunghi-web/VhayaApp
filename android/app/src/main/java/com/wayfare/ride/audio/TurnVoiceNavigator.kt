package com.wayfare.ride.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TurnVoiceNavigator(context: Context) {

    @Volatile
    private var isReady: Boolean = false

    @Volatile
    private var isReleased: Boolean = false

    private var tts: TextToSpeech? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (isReleased) return@TextToSpeech
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    val zaResult = engine?.setLanguage(Locale("en", "ZA"))
                    if (zaResult == TextToSpeech.LANG_MISSING_DATA || zaResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.ENGLISH)
                    }
                    engine?.setSpeechRate(1.02f)
                    isReady = true
                }
            }
        }
    }

    fun speak(prompt: String) {
        if (isReleased || !isReady || prompt.isBlank()) return
        runCatching {
            tts?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "vhaya_nav_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        if (isReleased) return
        runCatching { tts?.stop() }
    }

    fun release() {
        isReleased = true
        isReady = false
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
