package com.example.btchatshare

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import java.io.File

/**
 * Holds a single, app-wide BluetoothChatService instance so the connected socket
 * survives navigation between MainActivity -> DeviceListActivity -> ChatActivity.
 * Each foreground Activity registers itself as [currentListener] to receive callbacks.
 */
class App : Application() {

    var bluetoothAdapter: BluetoothAdapter? = null
        private set

    lateinit var chatService: BluetoothChatService
        private set

    var currentListener: BluetoothChatService.Listener? = null

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter

        val receiveDir = File(getExternalFilesDir(null), "Received")

        val adapter = bluetoothAdapter
        if (adapter != null) {
            chatService = BluetoothChatService(adapter, receiveDir, object : BluetoothChatService.Listener {
                override fun onStateChanged(state: Int) {
                    currentListener?.onStateChanged(state)
                }

                override fun onConnected(deviceName: String) {
                    currentListener?.onConnected(deviceName)
                }

                override fun onMessageReceived(text: String) {
                    currentListener?.onMessageReceived(text)
                }

                override fun onFileReceiveStarted(fileName: String, size: Long) {
                    currentListener?.onFileReceiveStarted(fileName, size)
                }

                override fun onFileReceiveProgress(fileName: String, bytesReceived: Long, size: Long) {
                    currentListener?.onFileReceiveProgress(fileName, bytesReceived, size)
                }

                override fun onFileReceived(fileName: String, file: File) {
                    currentListener?.onFileReceived(fileName, file)
                }

                override fun onFileSendProgress(fileName: String, bytesSent: Long, size: Long) {
                    currentListener?.onFileSendProgress(fileName, bytesSent, size)
                }

                override fun onFileSent(fileName: String) {
                    currentListener?.onFileSent(fileName)
                }

                override fun onError(message: String) {
                    currentListener?.onError(message)
                }
            })
        }
    }
}
