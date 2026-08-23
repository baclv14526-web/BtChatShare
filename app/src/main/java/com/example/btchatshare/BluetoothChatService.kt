package com.example.btchatshare

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Bluetooth RFCOMM service — chat + file transfer.
 *
 * Giao thức trên socket:
 *   byte  type  (0 = TEXT, 1 = FILE)
 *   TEXT  → UTF string
 *   FILE  → UTF string (name) + long (size) + raw bytes
 *
 * Chiến lược kết nối 3 lớp (tránh lỗi SDP trên OEM Android):
 *   1. createRfcommSocketToServiceRecord (chuẩn)
 *   2. createInsecureRfcommSocketToServiceRecord (bỏ xác thực PIN)
 *   3. Reflection: socket.javaClass.getMethod("createRfcommSocket", Int) channel=1
 *      — hoạt động trên hầu hết máy kể cả khi SDP bị chặn
 */
class BluetoothChatService(
    private val adapter: BluetoothAdapter,
    private val receiveDir: File,
    private val listener: Listener
) {

    interface Listener {
        fun onStateChanged(state: Int)
        fun onConnected(deviceName: String, deviceAddress: String)
        fun onMessageReceived(text: String)
        fun onFileReceiveStarted(fileName: String, size: Long)
        fun onFileReceiveProgress(fileName: String, bytesReceived: Long, size: Long)
        fun onFileReceived(fileName: String, file: File)
        fun onFileSendProgress(fileName: String, bytesSent: Long, size: Long)
        fun onFileSent(fileName: String)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "BtChatService"

        const val STATE_NONE       = 0
        const val STATE_LISTEN     = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED  = 3

        private const val TYPE_TEXT = 0
        private const val TYPE_FILE = 1
        private const val CHUNK_SIZE = 8192

        private val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val APP_NAME = "BtChatShare"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var acceptThread:    AcceptThread?    = null
    @Volatile private var connectThread:   ConnectThread?   = null
    @Volatile private var connectedThread: ConnectedThread? = null

    @Volatile var state: Int = STATE_NONE
        private set

    // Lưu lại để onConnected có thể fire dù listener tạm null
    @Volatile private var pendingConnectedName:    String? = null
    @Volatile private var pendingConnectedAddress: String? = null

    init { receiveDir.mkdirs() }

    // ── Listener proxy: fire ngay hoặc treo lại nếu listener null ──────────

    /**
     * Đăng ký listener. Nếu đang có sự kiện connected treo (listener vừa null
     * trong lúc chuyển Activity), fire ngay lập tức.
     */
    @Synchronized
    fun setListener(l: Listener?) {
        // Không lưu lại — listener được truy cập qua [currentListener] ở App.
        // Phương thức này chỉ dùng để flush sự kiện pending.
        if (l != null) {
            val name = pendingConnectedName
            val addr = pendingConnectedAddress
            if (name != null && addr != null) {
                pendingConnectedName    = null
                pendingConnectedAddress = null
                mainHandler.post { l.onConnected(name, addr) }
            }
        }
    }

    // ── State management ───────────────────────────────────────────────────

    @Synchronized
    private fun setState(newState: Int) {
        state = newState
        mainHandler.post { listener.onStateChanged(newState) }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    @Synchronized
    fun start() {
        Log.d(TAG, "start() — cancelando threads anteriores")
        connectThread?.cancel();   connectThread   = null
        connectedThread?.cancel(); connectedThread = null
        if (acceptThread == null) {
            acceptThread = AcceptThread().also { it.start() }
        }
        setState(STATE_LISTEN)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect() → ${device.address}")
        if (state == STATE_CONNECTING) {
            connectThread?.cancel(); connectThread = null
        }
        connectedThread?.cancel(); connectedThread = null
        connectThread = ConnectThread(device).also { it.start() }
        setState(STATE_CONNECTING)
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop()")
        connectThread?.cancel();   connectThread   = null
        connectedThread?.cancel(); connectedThread = null
        acceptThread?.cancel();    acceptThread    = null
        setState(STATE_NONE)
    }

    fun sendText(text: String) {
        val thread = synchronized(this) { connectedThread.takeIf { state == STATE_CONNECTED } }
        if (thread == null) {
            mainHandler.post { listener.onError("Chưa kết nối tới thiết bị nào") }
            return
        }
        thread.sendText(text)
    }

    fun sendFile(fileName: String, size: Long, input: InputStream) {
        val thread = synchronized(this) { connectedThread.takeIf { state == STATE_CONNECTED } }
        if (thread == null) {
            mainHandler.post { listener.onError("Chưa kết nối tới thiết bị nào") }
            return
        }
        thread.sendFile(fileName, size, input)
    }

    // ── Internal: khi socket đã mở thành công ─────────────────────────────

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        Log.d(TAG, "connected() → ${device.address}")
        connectThread?.cancel();   connectThread   = null
        connectedThread?.cancel(); connectedThread = null
        acceptThread?.cancel();    acceptThread    = null

        connectedThread = ConnectedThread(socket).also { it.start() }
        setState(STATE_CONNECTED)

        val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
        val addr = device.address

        mainHandler.post { listener.onConnected(name, addr) }
    }

    // ── AcceptThread (Server) ──────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread("BtAccept") {

        private val serverSocket: BluetoothServerSocket? = openServerSocket()

        private fun openServerSocket(): BluetoothServerSocket? {
            // Thử secure trước, fallback sang insecure
            return try {
                adapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                    .also { Log.d(TAG, "Server: secure RFCOMM socket opened") }
            } catch (e: IOException) {
                Log.w(TAG, "Server: secure failed (${e.message}), trying insecure…")
                try {
                    adapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, APP_UUID)
                        .also { Log.d(TAG, "Server: insecure RFCOMM socket opened") }
                } catch (e2: IOException) {
                    Log.e(TAG, "Server: both socket types failed: ${e2.message}")
                    mainHandler.post { listener.onError("Không thể mở server socket: ${e2.message}") }
                    null
                }
            }
        }

        override fun run() {
            Log.d(TAG, "AcceptThread: waiting for connection…")
            while (state != STATE_CONNECTED) {
                val socket: BluetoothSocket = try {
                    serverSocket?.accept() ?: break
                } catch (e: IOException) {
                    Log.w(TAG, "AcceptThread: accept() failed: ${e.message}")
                    break
                }
                Log.d(TAG, "AcceptThread: got connection from ${socket.remoteDevice.address}")
                synchronized(this@BluetoothChatService) {
                    when (state) {
                        STATE_LISTEN, STATE_CONNECTING -> connected(socket, socket.remoteDevice)
                        STATE_CONNECTED -> {
                            // Đã có kết nối khác — đóng socket mới này
                            try { socket.close() } catch (_: IOException) { }
                        }
                        else -> try { socket.close() } catch (_: IOException) { }
                    }
                }
                break
            }
            Log.d(TAG, "AcceptThread: exiting")
        }

        fun cancel() {
            try { serverSocket?.close() } catch (_: IOException) { }
        }
    }

    // ── ConnectThread (Client) ─────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread("BtConnect") {

        @Volatile private var socket: BluetoothSocket? = null
        @Volatile private var cancelled = false

        override fun run() {
            // Dừng discovery trước để tăng tốc và tránh xung đột
            try { adapter.cancelDiscovery() } catch (_: SecurityException) { }

            val sock = openSocket()
            if (sock == null || cancelled) {
                mainHandler.post {
                    listener.onError("Không tạo được socket Bluetooth. Hãy đảm bảo 2 máy đã ghép nối.")
                }
                this@BluetoothChatService.start()
                return
            }

            socket = sock

            Log.d(TAG, "ConnectThread: attempting connect to ${device.address}")
            try {
                sock.connect()
                Log.d(TAG, "ConnectThread: connect() succeeded")
            } catch (e: IOException) {
                Log.e(TAG, "ConnectThread: connect() failed: ${e.message}")
                try { sock.close() } catch (_: IOException) { }
                if (!cancelled) {
                    mainHandler.post { listener.onError("Kết nối thất bại: ${e.message}") }
                    this@BluetoothChatService.start()
                }
                return
            }

            if (!cancelled) {
                connected(sock, device)
            }
        }

        /**
         * Thử 3 cách tạo socket theo thứ tự ưu tiên:
         * 1. Secure RFCOMM với UUID (chuẩn nhất)
         * 2. Insecure RFCOMM với UUID (bỏ qua xác thực PIN)
         * 3. Reflection createRfcommSocket(channel=1) — bypass SDP hoàn toàn
         */
        private fun openSocket(): BluetoothSocket? {
            // 1. Secure
            try {
                return device.createRfcommSocketToServiceRecord(APP_UUID)
                    .also { Log.d(TAG, "Socket: created secure RFCOMM") }
            } catch (e: IOException) {
                Log.w(TAG, "Socket: secure failed (${e.message})")
            }

            // 2. Insecure
            try {
                return device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                    .also { Log.d(TAG, "Socket: created insecure RFCOMM") }
            } catch (e: IOException) {
                Log.w(TAG, "Socket: insecure failed (${e.message})")
            }

            // 3. Reflection — channel fixé à 1 (contourne SDP complètement)
            return try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                (method.invoke(device, 1) as BluetoothSocket)
                    .also { Log.d(TAG, "Socket: created via reflection channel=1") }
            } catch (e: Exception) {
                Log.e(TAG, "Socket: reflection failed (${e.message})")
                null
            }
        }

        fun cancel() {
            cancelled = true
            try { socket?.close() } catch (_: IOException) { }
        }
    }

    // ── ConnectedThread (I/O loop) ─────────────────────────────────────────

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread("BtConnected") {

        private val input  = DataInputStream(socket.inputStream.buffered(CHUNK_SIZE))
        private val output = DataOutputStream(socket.outputStream.buffered(CHUNK_SIZE))

        @Volatile private var running = true

        override fun run() {
            Log.d(TAG, "ConnectedThread: I/O loop started")
            while (running) {
                try {
                    when (val type = input.readByte().toInt()) {
                        TYPE_TEXT -> {
                            val text = input.readUTF()
                            Log.d(TAG, "Received TEXT: $text")
                            mainHandler.post { listener.onMessageReceived(text) }
                        }
                        TYPE_FILE -> {
                            val fileName = input.readUTF()
                            val size     = input.readLong()
                            Log.d(TAG, "Receiving FILE: $fileName ($size bytes)")
                            mainHandler.post { listener.onFileReceiveStarted(fileName, size) }
                            receiveFile(fileName, size)
                        }
                        else -> Log.w(TAG, "Unknown packet type: $type")
                    }
                } catch (e: IOException) {
                    if (running) {
                        Log.e(TAG, "ConnectedThread: I/O error: ${e.message}")
                        mainHandler.post { listener.onError("Mất kết nối: ${e.message}") }
                        setState(STATE_NONE)
                    }
                    running = false
                }
            }
            Log.d(TAG, "ConnectedThread: I/O loop ended")
        }

        private fun receiveFile(fileName: String, size: Long) {
            val outFile = uniqueFile(receiveDir, sanitizeFileName(fileName))
            try {
                FileOutputStream(outFile).use { fos ->
                    val buffer    = ByteArray(CHUNK_SIZE)
                    var remaining = size
                    while (remaining > 0) {
                        val toRead = minOf(remaining, CHUNK_SIZE.toLong()).toInt()
                        val read   = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                        val received = size - remaining
                        mainHandler.post { listener.onFileReceiveProgress(fileName, received, size) }
                    }
                }
                mainHandler.post { listener.onFileReceived(fileName, outFile) }
            } catch (e: IOException) {
                outFile.delete()
                throw e  // re-throw → caught by outer loop → disconnect
            }
        }

        @Synchronized
        fun sendText(text: String) {
            try {
                output.writeByte(TYPE_TEXT)
                output.writeUTF(text)
                output.flush()
                Log.d(TAG, "Sent TEXT: $text")
            } catch (e: IOException) {
                mainHandler.post { listener.onError("Gửi tin nhắn thất bại: ${e.message}") }
            }
        }

        @Synchronized
        fun sendFile(fileName: String, size: Long, inputStream: InputStream) {
            try {
                output.writeByte(TYPE_FILE)
                output.writeUTF(fileName)
                output.writeLong(size)
                val buffer = ByteArray(CHUNK_SIZE)
                var sent   = 0L
                inputStream.use { stream ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        sent += read
                        val sentSoFar = sent
                        mainHandler.post { listener.onFileSendProgress(fileName, sentSoFar, size) }
                    }
                }
                output.flush()
                mainHandler.post { listener.onFileSent(fileName) }
                Log.d(TAG, "Sent FILE: $fileName ($sent bytes)")
            } catch (e: IOException) {
                mainHandler.post { listener.onError("Gửi file thất bại: ${e.message}") }
            }
        }

        fun cancel() {
            running = false
            try { output.flush() } catch (_: Exception) { }
            try { socket.close() } catch (_: IOException) { }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun sanitizeFileName(name: String) =
        name.replace(Regex("""[/\\:*?"<>|]"""), "_").ifBlank { "file" }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot  = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext  = if (dot > 0) name.substring(dot)    else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$base($i)$ext"); i++ }
        return f
    }
}
