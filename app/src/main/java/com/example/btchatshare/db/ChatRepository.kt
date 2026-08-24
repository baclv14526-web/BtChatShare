package com.example.btchatshare.db

import com.example.btchatshare.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Đơn giản hóa việc đọc/ghi database cho các Activity/ViewModel.
 * Chuyển đổi qua lại giữa [MessageEntity] (tầng DB) và [ChatMessage] (tầng UI).
 */
class ChatRepository(private val dao: MessageDao) {

    // ── Ghi ────────────────────────────────────────────────────────────────

    suspend fun saveText(sessionId: String, text: String, isMine: Boolean): Long =
        dao.insert(
            MessageEntity(
                sessionId = sessionId,
                text      = text,
                isMine    = isMine,
                type      = MessageEntity.TYPE_TEXT
            )
        )

    suspend fun saveFileSent(sessionId: String, description: String, filePath: String? = null): Long =
        dao.insert(
            MessageEntity(
                sessionId = sessionId,
                text      = description,
                isMine    = true,
                type      = MessageEntity.TYPE_FILE_SENT,
                filePath  = filePath
            )
        )

    suspend fun saveFileReceived(sessionId: String, description: String, filePath: String? = null): Long =
        dao.insert(
            MessageEntity(
                sessionId = sessionId,
                text      = description,
                isMine    = false,
                type      = MessageEntity.TYPE_FILE_RECEIVED,
                filePath  = filePath
            )
        )

    // ── Đọc ────────────────────────────────────────────────────────────────

    /** Flow tự cập nhật — dùng trong ChatActivity để observe real-time. */
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(sessionId).map { list ->
            list.map { it.toChatMessage() }
        }

    /** Load một lần — dùng khi cần preload trước khi UI hiện. */
    suspend fun loadMessages(sessionId: String): List<ChatMessage> =
        dao.getMessages(sessionId).map { it.toChatMessage() }

    /** Danh sách phiên chat gần đây. */
    fun observeSessions(): Flow<List<SessionSummary>> =
        dao.observeSessions()

    // ── Xóa ────────────────────────────────────────────────────────────────

    suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)

    suspend fun deleteAll() = dao.deleteAll()

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun MessageEntity.toChatMessage() = ChatMessage(
        id        = id,
        text      = text,
        isMine    = isMine,
        type      = type,
        filePath  = filePath,
        timestamp = timestamp
    )
}
