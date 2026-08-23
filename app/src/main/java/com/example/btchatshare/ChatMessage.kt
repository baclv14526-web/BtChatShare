package com.example.btchatshare

data class ChatMessage(
    val text: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
