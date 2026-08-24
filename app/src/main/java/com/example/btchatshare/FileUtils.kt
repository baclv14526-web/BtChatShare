package com.example.btchatshare

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat

object FileUtils {

    /** Lấy MIME type của file dựa trên đuôi mở rộng. */
    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    /** Mở file bằng ứng dụng mặc định phù hợp (trình xem ảnh, video, tài liệu...). */
    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "File không tồn tại hoặc đã bị xóa", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Mở file: ${file.name}"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Không tìm thấy ứng dụng phù hợp để mở định dạng file này",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi mở file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Mở thư mục / vị trí lưu file (tương tự 'Open file location' trên Windows). */
    fun openFileLocation(context: Context, file: File) {
        val folder = if (file.isDirectory) file else (file.parentFile ?: file)
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val folderUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                folder
            )
        } catch (_: Exception) {
            null
        }

        var opened = false

        // Thử cách 1: Mở File Provider URI với mime resource/folder
        if (folderUri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folderUri, "resource/folder")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu file"))
                opened = true
            } catch (_: Exception) {
            }
        }

        // Thử cách 2: Mở File Provider URI với mime vnd.android.document/directory
        if (!opened && folderUri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folderUri, "vnd.android.document/directory")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu file"))
                opened = true
            } catch (_: Exception) {
            }
        }

        // Thử cách 3: Mở File Provider URI với mime */*
        if (!opened && folderUri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folderUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở vị trí lưu: ${folder.name}"))
                opened = true
            } catch (_: Exception) {
            }
        }

        // Nếu máy không có trình quản lý tệp nào nhận URI thư mục -> sao chép đường dẫn & thông báo
        if (!opened) {
            copyToClipboard(context, file.absolutePath, "Đường dẫn file")
            Toast.makeText(
                context,
                "📂 Vị trí lưu:\n${file.absolutePath}\n(Đã sao chép đường dẫn)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Chia sẻ file qua các ứng dụng khác (Zalo, Messenger, Gmail, Drive...). */
    fun shareFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "File không tồn tại", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ file: ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi chia sẻ file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Sao chép văn bản vào bộ nhớ tạm (Clipboard). */
    fun copyToClipboard(context: Context, text: String, label: String = "BtChatShare") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    /** Hiển thị hộp thoại menu đầy đủ khi tương tác với file. */
    fun showFileOptionsDialog(context: Context, filePath: String) {
        val file = File(filePath)
        val fileName = file.name
        val fileSizeText = if (file.exists()) formatFileSize(file.length()) else "Không xác định"

        val options = arrayOf(
            "📄 Mở file",
            "📂 Mở vị trí lưu file (Open Location)",
            "📤 Chia sẻ file",
            "📋 Sao chép đường dẫn file"
        )

        AlertDialog.Builder(context)
            .setTitle("$fileName ($fileSizeText)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFile(context, file)
                    1 -> openFileLocation(context, file)
                    2 -> shareFile(context, file)
                    3 -> {
                        copyToClipboard(context, file.absolutePath, "Đường dẫn file")
                        Toast.makeText(context, "Đã sao chép đường dẫn:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    /** Định dạng dung lượng file thành chuỗi dễ đọc (B, KB, MB, GB). */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = size / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }
}
