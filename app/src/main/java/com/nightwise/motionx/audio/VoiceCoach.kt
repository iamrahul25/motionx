package com.nightwise.motionx.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Loud-speaker callouts for Assist mode ("Jump!", "Duck!", "Move left!") using the on-device
 * TextToSpeech engine. Falls back silently if no TTS engine is installed.
 */
class VoiceCoach(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            configure()
        }
    }

    private fun configure() {
        val r = tts.setLanguage(Locale.getDefault())
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US)
        tts.setSpeechRate(1.25f)
        tts.setPitch(1.05f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    /** Speaks immediately, interrupting any previous callout. */
    fun say(text: String) {
        if (!ready) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "cue")
    }

    fun shutdown() {
        try {
            tts.stop(); tts.shutdown()
        } catch (_: Throwable) {
        }
    }
}
