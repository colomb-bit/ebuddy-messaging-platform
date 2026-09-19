package com.ebuddy.android.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages", indices = [Index(value = ["conversationId", "sequence"], unique = true)])
data class MessageEntity(@PrimaryKey val id: String, val conversationId: String, val fromUserId: String, val toUserId: String, val clientMessageId: String, val sequence: Long, val body: String, val status: String, val createdAt: Long, val deliveredAt: Long? = null, val readAt: Long? = null)

@Dao interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sequence ASC") fun observeConversation(conversationId: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE sequence > :cursor ORDER BY sequence ASC LIMIT :limit") suspend fun after(cursor: Long, limit: Int): List<MessageEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(messages: List<MessageEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(message: MessageEntity)
    @Query("UPDATE messages SET status=:status, deliveredAt=:deliveredAt, readAt=:readAt WHERE id=:id") suspend fun updateStatus(id: String, status: String, deliveredAt: Long?, readAt: Long?)
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class EbuddyDatabase : RoomDatabase() { abstract fun messages(): MessageDao }
