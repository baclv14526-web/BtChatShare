package com.example.btchatshare.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Một hàng trong bảng "messages".
 *
 * @param id          khóa chính, tự tăng
 * @param sessionId   địa chỉ MAC của thiết bị đối diện — dùng để
 *                    phân tách lịch sử chat theo từng người/thiết bị
 * @param text        nội dung tin nhắn (hoặc mô tả file)
 * @param isMine      true = mình gửi, false = nhận từ đối phương
 * @param type        "text" | "file_sent" | "file_received"
 * @param timestamp   Unix epoch milliseconds — lúc tin nhắn được tạo
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    val text: String,

    @ColumnInfo(name = "is_mine")
    val isMine: Boolean,

    val type: String = TYPE_TEXT,

    @ColumnInfo(name = "file_path")
    val filePath: String? = null,

    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_TEXT          = "text"
        const val TYPE_FILE_SENT     = "file_sent"
        const val TYPE_FILE_RECEIVED = "file_received"
    }
}
