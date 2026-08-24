package com.example.btchatshare.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    @Query("""
        SELECT session_id, MAX(timestamp) AS last_time, text AS last_message
        FROM messages
        GROUP BY session_id
        ORDER BY last_time DESC
    """)
    fun observeSessions(): Flow<List<SessionSummary>>

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
