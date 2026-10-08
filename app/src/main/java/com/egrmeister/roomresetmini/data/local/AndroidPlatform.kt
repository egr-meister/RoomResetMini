package com.egrmeister.roomresetmini.data.local

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.egrmeister.roomresetmini.domain.timer.BootReferenceProvider
import com.egrmeister.roomresetmini.domain.timer.MonotonicClock
import com.egrmeister.roomresetmini.domain.timer.WallClock

object AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

object SystemWallClock : WallClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Uses Settings.Global.BOOT_COUNT, which increments on every boot and is unaffected by wall-clock changes.
 * If it is unavailable, falls back to the boot instant rounded to 10 minutes (wall-clock based, so a manual
 * clock change could only cause a harmless "paused after restart", never a wrong countdown).
 */
class AndroidBootReference(context: Context) : BootReferenceProvider {
    private val resolver = context.applicationContext.contentResolver

    override fun bootReference(): String {
        val count = runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        if (count >= 0) return "boot:$count"
        val bootInstant = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return "approx:${bootInstant / 600_000L}"
    }
}

/** Optional short tone played only while the app is visible and sound is enabled. */
class CompletionSound {
    fun play() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
    }
}
