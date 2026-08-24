package com.example.btchatshare

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.btchatshare.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity(), BluetoothChatService.Listener {

    private lateinit var binding: ActivityMainBinding
    private val app get() = application as App

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            afterPermissionsGranted()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private val requestEnableBt = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            afterPermissionsGranted()
        } else {
            Toast.makeText(this, R.string.please_enable_bluetooth, Toast.LENGTH_LONG).show()
        }
    }

    private val requestDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op, just informs the user their phone is discoverable for a while */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (app.bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_LONG).show()
            binding.btnConnect.isEnabled = false
            binding.btnDiscoverable.isEnabled = false
            return
        }

        binding.btnConnect.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        binding.btnDiscoverable.setOnClickListener {
            makeDiscoverable()
        }

        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        app.currentListener = this
    }

    override fun onPause() {
        super.onPause()
        app.currentListener = null
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions().all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndStart() {
        if (hasAllPermissions()) {
            afterPermissionsGranted()
        } else {
            requestPermissions.launch(requiredPermissions())
        }
    }

    @SuppressLint("MissingPermission")
    private fun afterPermissionsGranted() {
        val adapter = app.bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            requestEnableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        // Start listening as a server so either phone can initiate the chat.
        app.chatService.start()
    }

    @SuppressLint("MissingPermission")
    private fun makeDiscoverable() {
        if (!hasAllPermissions()) {
            requestPermissions.launch(requiredPermissions())
            return
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        requestDiscoverable.launch(intent)
    }

    // ---- BluetoothChatService.Listener ----

    override fun onStateChanged(state: Int) {
        binding.tvStatus.text = when (state) {
            BluetoothChatService.STATE_LISTEN -> getString(R.string.status_waiting)
            BluetoothChatService.STATE_CONNECTING -> getString(R.string.status_connecting)
            else -> getString(R.string.status_waiting)
        }
    }

    override fun onConnected(deviceName: String, deviceAddress: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_DEVICE_NAME, deviceName)
            putExtra(ChatActivity.EXTRA_DEVICE_ADDRESS, deviceAddress)
        })
    }

    override fun onMessageReceived(text: String) { /* handled in ChatActivity */ }
    override fun onFileReceiveStarted(fileName: String, size: Long) { /* handled in ChatActivity */ }
    override fun onFileReceiveProgress(fileName: String, bytesReceived: Long, size: Long) { /* handled in ChatActivity */ }
    override fun onFileReceived(fileName: String, file: File) { /* handled in ChatActivity */ }
    override fun onFileSendProgress(fileName: String, bytesSent: Long, size: Long) { /* handled in ChatActivity */ }
    override fun onFileSent(fileName: String) { /* handled in ChatActivity */ }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
