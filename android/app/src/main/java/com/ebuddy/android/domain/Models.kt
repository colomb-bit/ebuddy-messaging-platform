package com.ebuddy.android.domain

import java.time.Instant

data class User(val id: String, val username: String, val displayName: String)
data class Session(val token: String, val expiresAt: Long, val user: User)
data class Contact(val id: String, val username: String, val displayName: String, val state: String)
data class Message(val id: String, val fromUserId: String, val toUserId: String, val clientMessageId: String, val sequence: Long, val body: String, val status: String, val createdAt: Long, val deliveredAt: Long? = null, val readAt: Long? = null)
data class SyncPage(val items: List<Message>, val nextCursor: Long, val more: Boolean)
