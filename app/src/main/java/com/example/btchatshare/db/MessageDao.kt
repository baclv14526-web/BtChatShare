package com.example.btchatshare.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Chèn một tin nhắn mới, trả về rowId vừa insert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    /**
     * Lấy toàn bộ tin nhắn của một phiên (theo địa chỉ MAC),
     * sắp xếp từ cũ nhất đến mới nhất.
     * Trả về Flow nên UI tự cập nhật khi có dữ liệu mới.
     */
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    /**
     * Load một lần (không reactive) — dùng khi cần preload
     * trước khi hiển thị màn hình chat.
     */
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    /** Danh sách các phiên đã từng chat, kèm tin nhắn cuối và thời gian. */
    @Query("""
        SELECT session_id, MAX(timestamp) AS last_time, text AS last_message
        FROM messages
        GROUP BY session_id
        ORDER BY last_time DESC
    """)
    fun observeSessions(): Flow<List<SessionSummary>>

    /** Xóa toàn bộ lịch sử của một phiên. */
    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /** Xóa toàn bộ database. */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

/** Kết quả query tóm tắt danh sách phiên chat (không phải Entity đầy đủ). */
data class SessionSummary(
    val session_id: String,
    val last_time: Long,
    val last_message: String
)
