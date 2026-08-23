package com.example.btchatshare

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.btchatshare.db.AppDatabase
import com.example.btchatshare.db.ChatRepository
import java.io.File

class App : Application() {

    var bluetoothAdapter: BluetoothAdapter? = null
        private set

    lateinit var chatService: BluetoothChatService
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var settings: SettingsManager
        private set

    lateinit var notifHelper: NotificationHelper
        private set

    /**
     * Activity hiện tại đăng ký nhận sự kiện Bluetooth.
     * Set trong onResume(), clear trong onPause().
     * Khi set lại (không null), flush pending onConnected nếu có.
     */
    var currentListener: BluetoothChatService.Listener? = null
        set(value) {
            field = value
            // Flush sự kiện onConnected bị treo do listener null lúc chuyển Activity.
            // Guard bằng isInitialized vì setter có thể được gọi trước onCreate() hoàn tất.
            if (value != null && ::chatService.isInitialized) {
                chatService.setListener(value)
            }
        }

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter

        val db = AppDatabase.getInstance(this)
        chatRepository = ChatRepository(db.messageDao())

        settings    = SettingsManager(this)
        notifHelper = NotificationHelper(this)

        val adapter = bluetoothAdapter
        if (adapter != null) {
            val receiveDir = File(getExternalFilesDir(null), "Received")
            chatService = BluetoothChatService(
                adapter, receiveDir,
                object : BluetoothChatService.Listener {
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
                }
            )
        }
    }
}
