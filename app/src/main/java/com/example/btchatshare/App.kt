package com.example.btchatshare

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.btchatshare.db.AppDatabase
import com.example.btchatshare.db.ChatRepository
import com.example.btchatshare.NotificationHelper
import com.example.btchatshare.SettingsManager
import java.io.File

class App : Application() {

    var bluetoothAdapter: BluetoothAdapter? = null
        private set

    lateinit var chatService: BluetoothChatService
        private set

    /** Repository truy cập lịch sử chat — dùng chung toàn app. */
    lateinit var chatRepository: ChatRepository
        private set

    /** Quản lý tuỳ chọn âm thanh / rung. */
    lateinit var settings: SettingsManager
        private set

    /** Phát âm thanh và rung — dùng chung toàn app. */
    lateinit var notifHelper: NotificationHelper
        private set

    var currentListener: BluetoothChatService.Listener? = null

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter

        // Khởi tạo Room database và repository
        val db = AppDatabase.getInstance(this)
        chatRepository = ChatRepository(db.messageDao())

        // Khởi tạo Settings và NotificationHelper
        settings    = SettingsManager(this)
        notifHelper = NotificationHelper(this)

        val receiveDir = File(getExternalFilesDir(null), "Received")

        val adapter = bluetoothAdapter
        if (adapter != null) {
            chatService = BluetoothChatService(adapter, receiveDir, object : BluetoothChatService.Listener {
                override fun onStateChanged(state: Int) {
                    currentListener?.onStateChanged(state)
                }
                override fun onConnected(deviceName: String, deviceAddress: String) {
                    currentListener?.onConnected(deviceName, deviceAddress)
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
