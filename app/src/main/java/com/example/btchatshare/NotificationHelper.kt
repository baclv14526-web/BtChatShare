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
import androidx.annotation.RequiresApi

/**
 * Phát âm thanh thông báo và rung thiết bị khi nhận tin nhắn hoặc file.
 * Tôn trọng chế độ im lặng / rung của máy thông qua [AudioManager].
 */
class NotificationHelper(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Lấy Vibrator đúng API — tránh reference trực tiếp VibratorManager ở tầng class
    // để không cần @RequiresApi trên toàn class.
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator = getVibrator()

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getVibratorApi31()
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getVibratorApi31(): Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator

    // ── Âm thanh ──────────────────────────────────────────────────────────

    fun playMessageSound() = playNotificationSound()
    fun playFileSound()    = playNotificationSound()

    private fun playNotificationSound() {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener  { start() }
                setOnCompletionListener { release() }
                setOnErrorListener     { mp, _, _ -> mp.release(); true }
                prepareAsync()
            }
        } catch (_: Exception) { /* thiết bị không có ringtone mặc định */ }
    }

    // ── Rung ──────────────────────────────────────────────────────────────

    /** Rung 1 nhịp ngắn — khi nhận tin nhắn. */
    fun vibrateForMessage() {
        if (vibrator.hasVibrator()) vibrate(longArrayOf(0, 200))
    }

    /** Rung 2 nhịp — khi nhận file xong. */
    fun vibrateForFile() {
        if (vibrator.hasVibrator()) vibrate(longArrayOf(0, 150, 100, 300))
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }
}
