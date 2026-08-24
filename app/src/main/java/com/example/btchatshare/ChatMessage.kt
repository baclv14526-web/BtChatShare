package com.example.btchatshare

import com.example.btchatshare.db.MessageEntity

data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isMine: Boolean,
    val type: String = MessageEntity.TYPE_TEXT,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

