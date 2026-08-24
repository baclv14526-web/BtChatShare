package com.example.btchatshare

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Phát âm thanh thông báo và rung thiết bị khi nhận tin nhắn hoặc file.
 *
 * - Âm thanh: dùng ringtone thông báo mặc định của hệ thống (TYPE_NOTIFICATION)
 *   thông qua [MediaPlayer] — không cần file âm thanh đính kèm trong app.
 * - Rung: dùng [VibrationEffect] (API 26+) với pattern ngắn cho tin nhắn,
 *   pattern dài hơn cho file nhận xong.
 * - Tôn trọng chế độ im lặng / rung của máy thông qua [AudioManager].
 */
class NotificationHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // ── Âm thanh ──────────────────────────────────────────────────────────

    /**
     * Phát âm thông báo mặc định của hệ thống.
     * Tự động tôn trọng chế độ im lặng / Không làm phiền.
     */
    fun playMessageSound() = playNotificationSound()

    fun playFileSound() = playNotificationSound()

    private fun playNotificationSound() {
        // Không phát nếu điện thoại đang im lặng hoàn toàn
        val mode = audioManager.ringerMode
        if (mode == AudioManager.RINGER_MODE_SILENT) return

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener { start() }
                setOnCompletionListener { release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
                prepareAsync()
            }
        } catch (_: Exception) {
            // Thiết bị không có ringtone mặc định — bỏ qua
        }
    }

    // ── Rung ──────────────────────────────────────────────────────────────

    /**
     * Rung ngắn 1 lần — dùng khi nhận tin nhắn văn bản.
     * Pattern: rung 200ms.
     */
    fun vibrateForMessage() {
        if (!vibrator.hasVibrator()) return
        vibrate(longArrayOf(0, 200))
    }

    /**
     * Rung 2 nhịp — dùng khi nhận file xong.
     * Pattern: rung 150ms, nghỉ 100ms, rung 300ms.
     */
    fun vibrateForFile() {
        if (!vibrator.hasVibrator()) return
        vibrate(longArrayOf(0, 150, 100, 300))
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1 /* không lặp */)
            )
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }
}
