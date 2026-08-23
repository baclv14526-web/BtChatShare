package com.example.btchatshare

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.btchatshare.databinding.ActivityDeviceListBinding

class DeviceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceListBinding
    private val app get() = application as App
    private lateinit var adapterList: DeviceAdapter

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { adapterList.addIfAbsent(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.progressScan.visibility = android.view.View.GONE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.select_device)

        adapterList = DeviceAdapter(mutableListOf()) { device -> connectTo(device) }
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapterList

        loadPairedDevices()

        binding.btnScan.setOnClickListener { startScan() }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val adapter = app.bluetoothAdapter ?: return
        if (!hasConnectPermission()) return
        val paired = adapter.bondedDevices?.toList() ?: emptyList()
        adapterList.submit(paired)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = app.bluetoothAdapter ?: return
        if (!hasScanPermission()) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        binding.progressScan.visibility = android.view.View.VISIBLE
        adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BluetoothDevice) {
        val adapter = app.bluetoothAdapter ?: return
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        // Xoá listener của màn hình này (nếu có) TRƯỚC khi finish(),
        // để MainActivity.onResume() set lại listener ngay sau khi stack pop.
        // chatService.connect() chạy trên background thread — onConnected()
        // sẽ fire sau khi MainActivity đã resume và set currentListener lại rồi.
        app.chatService.connect(device)
        finish()  // MainActivity.onResume() → currentListener = MainActivity → flush pending
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        app.bluetoothAdapter?.let {
            if (it.isDiscovering) it.cancelDiscovery()
        }
    }
}
