package com.example.btchatshare.db

import androidx.room.ColumnInfo

/** Tóm tắt một phiên chat — kết quả query gộp từ bảng messages. */
data class SessionSummary(
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "last_time")
    val lastTime: Long,

    @ColumnInfo(name = "last_message")
    val lastMessage: String
)
