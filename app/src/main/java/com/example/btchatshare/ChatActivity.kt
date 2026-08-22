package com.example.btchatshare

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.btchatshare.databinding.ActivityChatBinding
import java.io.File

class ChatActivity : AppCompatActivity(), BluetoothChatService.Listener {

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }

    private lateinit var binding: ActivityChatBinding
    private val app get() = application as App
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { sendFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: getString(R.string.app_name)

        chatAdapter = ChatAdapter(messages)
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = chatAdapter

        binding.btnSend.setOnClickListener { sendTypedMessage() }
        binding.btnAttach.setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        app.currentListener = this
    }

    override fun onPause() {
        super.onPause()
        app.currentListener = null
    }

    private fun sendTypedMessage() {
        val text = binding.etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        app.chatService.sendText(text)
        addMessage(text, mine = true)
        binding.etMessage.setText("")
    }

    private fun sendFile(uri: Uri) {
        val resolver = contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        if (size <= 0) {
            Toast.makeText(this, "Không đọc được kích thước file", Toast.LENGTH_SHORT).show()
            return
        }
        val input = resolver.openInputStream(uri)
        if (input == null) {
            Toast.makeText(this, "Không thể mở file", Toast.LENGTH_SHORT).show()
            return
        }
        addMessage("📎 Đang gửi: $name", mine = true)
        app.chatService.sendFile(name, size, input)
    }

    private fun addMessage(text: String, mine: Boolean) {
        chatAdapter.addMessage(ChatMessage(text, mine))
        binding.recyclerMessages.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun showTransfer(text: String, percent: Int) {
        binding.layoutTransfer.visibility = android.view.View.VISIBLE
        binding.tvTransferStatus.text = text
        binding.progressTransfer.progress = percent
    }

    private fun hideTransfer() {
        binding.layoutTransfer.visibility = android.view.View.GONE
    }

    // ---- BluetoothChatService.Listener ----

    override fun onStateChanged(state: Int) {
        if (state == BluetoothChatService.STATE_NONE) {
            Toast.makeText(this, "Đã ngắt kết nối", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onConnected(deviceName: String) {
        title = deviceName
    }

    override fun onMessageReceived(text: String) {
        addMessage(text, mine = false)
    }

    override fun onFileReceiveStarted(fileName: String, size: Long) {
        addMessage("📎 Đang nhận: $fileName", mine = false)
        showTransfer("Đang nhận $fileName (0%)", 0)
    }

    override fun onFileReceiveProgress(fileName: String, bytesReceived: Long, size: Long) {
        val percent = if (size > 0) ((bytesReceived * 100) / size).toInt() else 0
        showTransfer("Đang nhận $fileName ($percent%)", percent)
    }

    override fun onFileReceived(fileName: String, file: File) {
        hideTransfer()
        addMessage("✅ Đã nhận file: $fileName (${file.length()} bytes)\nLưu tại: ${file.absolutePath}", mine = false)
    }

    override fun onFileSendProgress(fileName: String, bytesSent: Long, size: Long) {
        val percent = if (size > 0) ((bytesSent * 100) / size).toInt() else 0
        showTransfer("Đang gửi $fileName ($percent%)", percent)
    }

    override fun onFileSent(fileName: String) {
        hideTransfer()
        addMessage("✅ Đã gửi xong: $fileName", mine = true)
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
