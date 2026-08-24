package com.example.btchatshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.btchatshare.databinding.ActivityChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class ChatActivity : AppCompatActivity(), BluetoothChatService.Listener {

    companion object {
        const val EXTRA_DEVICE_NAME    = "extra_device_name"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
    }

    private lateinit var binding: ActivityChatBinding
    private val app        get() = application as App
    private val repo        get() = app.chatRepository
    private val settings    get() = app.settings
    private val notifHelper get() = app.notifHelper
    private lateinit var chatAdapter: ChatAdapter

    /** Địa chỉ MAC của thiết bị đối diện — dùng làm session ID trong DB. */
    private var sessionId: String = "unknown"

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { sendFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: "unknown"
        title     = intent.getStringExtra(EXTRA_DEVICE_NAME)    ?: getString(R.string.app_name)

        chatAdapter = ChatAdapter(mutableListOf())
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = chatAdapter

        binding.btnSend.setOnClickListener   { sendTypedMessage() }
        binding.btnAttach.setOnClickListener { pickFile.launch(arrayOf("*/*")) }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        app.currentListener = this
    }

    override fun onPause() {
        super.onPause()
        app.currentListener = null
    }

    // ── Menu (nút xóa lịch sử) ─────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        if (item.itemId == R.id.action_clear_history) {
            confirmClearHistory()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Xóa lịch sử chat")
            .setMessage("Toàn bộ tin nhắn với thiết bị này sẽ bị xóa vĩnh viễn. Tiếp tục?")
            .setPositiveButton("Xóa") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.deleteSession(sessionId)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ── Load lịch sử từ DB ─────────────────────────────────────────────────

    /**
     * Observe Flow từ DB — khi có tin nhắn mới được lưu (kể cả từ luồng BT),
     * RecyclerView tự cập nhật.
     */
    private fun loadHistory() {
        lifecycleScope.launch {
            repo.observeMessages(sessionId).collectLatest { history ->
                chatAdapter.submitList(history)
                if (history.isNotEmpty()) {
                    binding.recyclerMessages.scrollToPosition(history.size - 1)
                }
            }
        }
    }

    // ── Gửi ────────────────────────────────────────────────────────────────

    private fun sendTypedMessage() {
        val text = binding.etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        app.chatService.sendText(text)
        // Lưu vào DB — Flow sẽ tự cập nhật RecyclerView
        lifecycleScope.launch(Dispatchers.IO) {
            repo.saveText(sessionId, text, isMine = true)
        }
        binding.etMessage.setText("")
    }

    private fun sendFile(uri: Uri) {
        val resolver = contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (ni >= 0) name = cursor.getString(ni) ?: name
                if (si >= 0) size = cursor.getLong(si)
            }
        }
        if (size <= 0) {
            Toast.makeText(this, "Không đọc được kích thước file", Toast.LENGTH_SHORT).show()
            return
        }
        val input = resolver.openInputStream(uri) ?: run {
            Toast.makeText(this, "Không thể mở file", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            repo.saveFileSent(sessionId, "📎 Đang gửi: $name")
        }
        app.chatService.sendFile(name, size, input)
    }

    // ── Helpers UI ─────────────────────────────────────────────────────────

    private fun showTransfer(text: String, percent: Int) {
        binding.layoutTransfer.visibility = android.view.View.VISIBLE
        binding.tvTransferStatus.text     = text
        binding.progressTransfer.progress = percent
    }

    private fun hideTransfer() {
        binding.layoutTransfer.visibility = android.view.View.GONE
    }

    // ── BluetoothChatService.Listener ──────────────────────────────────────

    override fun onStateChanged(state: Int) {
        if (state == BluetoothChatService.STATE_NONE) {
            Toast.makeText(this, "Đã ngắt kết nối", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onConnected(deviceName: String, deviceAddress: String) {
        title = deviceName
    }

    override fun onMessageReceived(text: String) {
        // App.kt đã lưu DB và phát chuông/rung — Room Flow tự động cập nhật RecyclerView
    }

    override fun onFileReceiveStarted(fileName: String, size: Long) {
        showTransfer("Đang nhận $fileName (0%)", 0)
    }

    override fun onFileReceiveProgress(fileName: String, bytesReceived: Long, size: Long) {
        val pct = if (size > 0) ((bytesReceived * 100) / size).toInt() else 0
        showTransfer("Đang nhận $fileName ($pct%)", pct)
    }

    override fun onFileReceived(fileName: String, file: File) {
        hideTransfer()
        // App.kt đã lưu DB và phát chuông/rung — Room Flow tự động cập nhật RecyclerView
    }

    override fun onFileSendProgress(fileName: String, bytesSent: Long, size: Long) {
        val pct = if (size > 0) ((bytesSent * 100) / size).toInt() else 0
        showTransfer("Đang gửi $fileName ($pct%)", pct)
    }

    override fun onFileSent(fileName: String) {
        hideTransfer()
        lifecycleScope.launch(Dispatchers.IO) {
            repo.saveFileSent(sessionId, "✅ Đã gửi xong: $fileName")
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
