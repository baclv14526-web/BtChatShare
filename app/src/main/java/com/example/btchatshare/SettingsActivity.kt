package com.example.btchatshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.btchatshare.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsManager
    private lateinit var notifHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding  = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        settings    = SettingsManager(this)
        notifHelper = NotificationHelper(this)

        // ── Khởi tạo trạng thái ban đầu từ SharedPreferences ──────────────
        binding.switchSoundMessage.isChecked  = settings.soundOnMessage
        binding.switchSoundFile.isChecked     = settings.soundOnFile
        binding.switchVibrateMessage.isChecked = settings.vibrateOnMessage
        binding.switchVibrateFile.isChecked   = settings.vibrateOnFile

        // ── Lắng nghe thay đổi ────────────────────────────────────────────
        binding.switchSoundMessage.setOnCheckedChangeListener { _, checked ->
            settings.soundOnMessage = checked
            if (checked) notifHelper.playMessageSound() // preview
        }

        binding.switchSoundFile.setOnCheckedChangeListener { _, checked ->
            settings.soundOnFile = checked
            if (checked) notifHelper.playFileSound()   // preview
        }

        binding.switchVibrateMessage.setOnCheckedChangeListener { _, checked ->
            settings.vibrateOnMessage = checked
            if (checked) notifHelper.vibrateForMessage() // preview
        }

        binding.switchVibrateFile.setOnCheckedChangeListener { _, checked ->
            settings.vibrateOnFile = checked
            if (checked) notifHelper.vibrateForFile()    // preview
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
