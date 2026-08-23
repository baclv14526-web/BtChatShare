package com.example.btchatshare

import android.content.Context

/**
 * Lưu và đọc tuỳ chọn âm thanh / rung bằng SharedPreferences.
 * Không cần dependency ngoài — chỉ dùng API Android thuần.
 */
class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME        = "btchat_settings"
        const val KEY_SOUND_MESSAGE  = "sound_message"   // âm khi nhận tin nhắn
        const val KEY_SOUND_FILE     = "sound_file"      // âm khi nhận file xong
        const val KEY_VIBRATE_MESSAGE = "vibrate_message" // rung khi nhận tin nhắn
        const val KEY_VIBRATE_FILE   = "vibrate_file"    // rung khi nhận file xong
    }

    var soundOnMessage: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_MESSAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_MESSAGE, value).apply()

    var soundOnFile: Boolean
        get()      = prefs.getBoolean(KEY_SOUND_FILE, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_FILE, value).apply()

    var vibrateOnMessage: Boolean
        get()      = prefs.getBoolean(KEY_VIBRATE_MESSAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_MESSAGE, value).apply()

    var vibrateOnFile: Boolean
        get()      = prefs.getBoolean(KEY_VIBRATE_FILE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_FILE, value).apply()
}
