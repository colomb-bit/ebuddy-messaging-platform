package com.ebuddy.android.data.remote

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private val Context.sessionDataStore by preferencesDataStore("session")
class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    suspend fun save(token: String) { context.sessionDataStore.edit { it[tokenKey] = token } }
    suspend fun clear() { context.sessionDataStore.edit { it.remove(tokenKey) } }
    fun tokenBlocking(): String? = runBlocking { context.sessionDataStore.data.first()[tokenKey] }
}
class AuthInterceptor(private val store: SessionStore) : Interceptor {
    @Volatile private var current: String? = null
    fun setToken(token: String?) { current = token }
    fun token(): String? = current ?: store.tokenBlocking()
    override fun intercept(chain: Interceptor.Chain): Response { val token = current ?: store.tokenBlocking(); val request = chain.request().newBuilder().apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token"); header("X-Client", "android") }.build(); return chain.proceed(request) }
}
object ApiFactory {
    fun create(context: Context, baseUrl: String): Pair<EbuddyApi, AuthInterceptor> { val store=SessionStore(context); val auth=AuthInterceptor(store); val client=OkHttpClient.Builder().addInterceptor(auth).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).retryOnConnectionFailure(true).build(); val api=Retrofit.Builder().baseUrl(baseUrl.trimEnd('/')+"/").client(client).addConverterFactory(MoshiConverterFactory.create()).build().create(EbuddyApi::class.java); return api to auth }
}
