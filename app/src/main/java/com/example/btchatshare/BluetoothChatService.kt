package com.example.btchatshare

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Handles a Bluetooth RFCOMM connection between two devices.
 * Supports sending/receiving plain text messages and files over the same socket
 * using a tiny length-prefixed binary protocol:
 *
 *   byte  type        (0 = TEXT, 1 = FILE)
 *   TEXT: UTF string  (message)
 *   FILE: UTF string  (file name), long (file size), raw bytes (file content)
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
        const val STATE_NONE = 0
        const val STATE_LISTEN = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3

        private const val TYPE_TEXT: Int = 0
        private const val TYPE_FILE: Int = 1
        private const val CHUNK_SIZE = 8192

        // Fixed custom UUID shared by both client & server sides of the app.
        private val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val APP_NAME = "BtChatShare"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    @Volatile
    var state: Int = STATE_NONE
        private set

    init {
        receiveDir.mkdirs()
    }

    @Synchronized
    private fun setState(newState: Int) {
        state = newState
        handler.post { listener.onStateChanged(newState) }
    }

    /** Start listening for an incoming connection (server role). */
    @Synchronized
    fun start() {
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        if (acceptThread == null) {
            acceptThread = AcceptThread().also { it.start() }
        }
        setState(STATE_LISTEN)
    }

    /** Connect to a remote device (client role). */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice) {
        if (state == STATE_CONNECTING) {
            connectThread?.cancel(); connectThread = null
        }
        connectedThread?.cancel(); connectedThread = null
        connectThread = ConnectThread(device).also { it.start() }
        setState(STATE_CONNECTING)
    }

    @Synchronized
    private fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        acceptThread?.cancel(); acceptThread = null

        connectedThread = ConnectedThread(socket).also { it.start() }
        setState(STATE_CONNECTED)
        val name = try {
            device.name ?: device.address
        } catch (e: SecurityException) {
            device.address
        }
        handler.post { listener.onConnected(name, device.address) }
    }

    @Synchronized
    fun stop() {
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        acceptThread?.cancel(); acceptThread = null
        setState(STATE_NONE)
    }

    fun sendText(text: String) {
        val thread = synchronized(this) { connectedThread.takeIf { state == STATE_CONNECTED } }
        if (thread == null) {
            handler.post { listener.onError("Chưa kết nối tới thiết bị nào") }
            return
        }
        thread.sendText(text)
    }

    fun sendFile(fileName: String, size: Long, input: InputStream) {
        val thread = synchronized(this) { connectedThread.takeIf { state == STATE_CONNECTED } }
        if (thread == null) {
            handler.post { listener.onError("Chưa kết nối tới thiết bị nào") }
            return
        }
        thread.sendFile(fileName, size, input)
    }

    // ---------------------------------------------------------------------
    // Server: accepts one incoming RFCOMM connection.
    // ---------------------------------------------------------------------
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? = try {
            adapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
        } catch (e: IOException) {
            handler.post { listener.onError("Không thể mở server: ${e.message}") }
            null
        }

        override fun run() {
            var looping = true
            while (looping) {
                val socket: BluetoothSocket? = try {
                    serverSocket?.accept()
                } catch (e: IOException) {
                    looping = false
                    null
                }
                if (socket != null) {
                    connected(socket, socket.remoteDevice)
                    looping = false
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (_: IOException) {
            }
        }
    }

    // ---------------------------------------------------------------------
    // Client: connects out to a chosen remote device.
    // ---------------------------------------------------------------------
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? = try {
            device.createRfcommSocketToServiceRecord(APP_UUID)
        } catch (e: IOException) {
            null
        }

        override fun run() {
            try {
                adapter.cancelDiscovery()
            } catch (_: SecurityException) {
            }

            try {
                socket?.connect()
            } catch (e: IOException) {
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
                handler.post { listener.onError("Không thể kết nối: ${e.message}") }
                this@BluetoothChatService.start()
                return
            }

            socket?.let { connected(it, device) }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    // ---------------------------------------------------------------------
    // Active connection: reads/writes text and file data.
    // ---------------------------------------------------------------------
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val input = DataInputStream(socket.inputStream)
        private val output = DataOutputStream(socket.outputStream)

        @Volatile
        private var running = true

        override fun run() {
            while (running) {
                try {
                    when (input.readByte().toInt()) {
                        TYPE_TEXT -> {
                            val text = input.readUTF()
                            handler.post { listener.onMessageReceived(text) }
                        }
                        TYPE_FILE -> {
                            val fileName = input.readUTF()
                            val size = input.readLong()
                            handler.post { listener.onFileReceiveStarted(fileName, size) }

                            val safeName = sanitizeFileName(fileName)
                            val outFile = uniqueFile(receiveDir, safeName)
                            FileOutputStream(outFile).use { fos ->
                                val buffer = ByteArray(CHUNK_SIZE)
                                var remaining = size
                                while (remaining > 0) {
                                    val toRead = if (remaining < CHUNK_SIZE) remaining.toInt() else CHUNK_SIZE
                                    val read = input.read(buffer, 0, toRead)
                                    if (read == -1) break
                                    fos.write(buffer, 0, read)
                                    remaining -= read
                                    val receivedSoFar = size - remaining
                                    handler.post { listener.onFileReceiveProgress(fileName, receivedSoFar, size) }
                                }
                            }
                            handler.post { listener.onFileReceived(fileName, outFile) }
                        }
                    }
                } catch (e: IOException) {
                    running = false
                    handler.post { listener.onError("Mất kết nối: ${e.message}") }
                    setState(STATE_NONE)
                }
            }
        }

        @Synchronized
        fun sendText(text: String) {
            try {
                output.writeByte(TYPE_TEXT)
                output.writeUTF(text)
                output.flush()
            } catch (e: IOException) {
                handler.post { listener.onError("Gửi tin nhắn thất bại: ${e.message}") }
            }
        }

        @Synchronized
        fun sendFile(fileName: String, size: Long, inputStream: InputStream) {
            try {
                output.writeByte(TYPE_FILE)
                output.writeUTF(fileName)
                output.writeLong(size)
                val buffer = ByteArray(CHUNK_SIZE)
                var sent = 0L
                inputStream.use { stream ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        sent += read
                        val sentSoFar = sent
                        handler.post { listener.onFileSendProgress(fileName, sentSoFar, size) }
                    }
                }
                output.flush()
                handler.post { listener.onFileSent(fileName) }
            } catch (e: IOException) {
                handler.post { listener.onError("Gửi file thất bại: ${e.message}") }
            }
        }

        fun cancel() {
            running = false
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "file" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base($i)$ext")
            i++
        }
        return candidate
    }
}
