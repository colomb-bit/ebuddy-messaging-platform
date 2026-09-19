package com.ebuddy.android.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ebuddy_secure_session",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun token(): String? = prefs.getString("token", null)
    fun read(): StoredSession? {
        val token = token() ?: return null
        val id = prefs.getString("user_id", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val displayName = prefs.getString("display_name", null) ?: username
        return StoredSession(token, prefs.getLong("expires_at", 0L), UserDto(id, username, displayName))
    }
    suspend fun save(session: LoginResponse) = withContext(Dispatchers.IO) {
        prefs.edit().putString("token", session.token).putLong("expires_at", session.expiresAt)
            .putString("user_id", session.user.id).putString("username", session.user.username)
            .putString("display_name", session.user.displayName).apply()
    }
    suspend fun clear() = withContext(Dispatchers.IO) { prefs.edit().clear().apply() }
}

data class StoredSession(val token: String, val expiresAt: Long, val user: UserDto)

class AuthInterceptor(private val store: SessionStore) : Interceptor {
    @Volatile private var current: String? = null
    fun setToken(token: String?) { current = token }
    fun token(): String? = current ?: store.token()
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = current ?: store.token()
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            header("X-Client", "android")
        }.build()
        return chain.proceed(request)
    }
}

object ApiFactory {
    fun create(context: Context, baseUrl: String): Pair<EbuddyApi, AuthInterceptor> {
        val store = SessionStore(context)
        val auth = AuthInterceptor(store)
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val api = Retrofit.Builder().baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client).addConverterFactory(MoshiConverterFactory.create()).build()
            .create(EbuddyApi::class.java)
        return api to auth
    }
}
