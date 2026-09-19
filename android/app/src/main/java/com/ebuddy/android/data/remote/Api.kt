package com.ebuddy.android.data.remote

import com.squareup.moshi.Json
import retrofit2.http.*

data class LoginRequest(val username: String, val password: String, @Json(name="client_type") val clientType: String = "android", @Json(name="device_label") val deviceLabel: String? = "Android")
data class RegisterRequest(val username: String, val password: String, @Json(name="displayName") val displayName: String)
data class UserDto(val id: String, val username: String, @Json(name="display_name") val displayName: String)
data class LoginResponse(val token: String, @Json(name="expires_at") val expiresAt: Long, val user: UserDto)
data class ContactDto(val user: UserDto, val state: String, val alias: String?)
data class SendRequest(@Json(name="to_user_id") val toUserId: String, @Json(name="client_message_id") val clientMessageId: String, val body: String)
data class MessageDto(val id: String, @Json(name="from_user_id") val fromUserId: String, @Json(name="to_user_id") val toUserId: String, @Json(name="client_message_id") val clientMessageId: String, val sequence: Long, val body: String, val status: String, @Json(name="created_at") val createdAt: String, @Json(name="delivered_at") val deliveredAt: String? = null, @Json(name="read_at") val readAt: String? = null)
data class SyncResponse(val items: List<MessageDto>, @Json(name="next_cursor") val nextCursor: String, val more: Boolean)
data class ReceiptRequest(val state: String)

data class ErrorDto(val code: String?, val message: String?, val retryable: Boolean?)
interface EbuddyApi {
    @POST("api/auth/signin") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("api/auth/signup") suspend fun register(@Body request: RegisterRequest): J2meLoginResponse
    @GET("api/contacts") suspend fun contacts(@Query("limit") limit: Int = 20): List<ContactDto>
    @POST("api/messages") suspend fun send(@Body request: SendRequest): MessageDto
    @GET("api/sync") suspend fun sync(@Query("cursor") cursor: Long, @Query("limit") limit: Int = 20): SyncResponse
    @POST("api/messages/{id}/receipt") suspend fun receipt(@Path("id") id: String, @Body request: ReceiptRequest)
}
data class J2meLoginResponse(val token: String, @Json(name="expires_at") val expiresAt: Long)
