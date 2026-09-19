package com.ebuddy.android.data.remote

import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class StompClient(private val client: OkHttpClient, private val wsUrl: String, private val tokenProvider: () -> String?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<MessageDto> = _events.asSharedFlow()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(StompEnvelope::class.java)
    private val sendAdapter = moshi.adapter(SendRequest::class.java)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var wanted = false
    private var attempt = 0
    fun connect() { wanted = true; if (socket == null) open() }
    fun close() { wanted=false; socket?.close(1000,"client close"); socket=null }
    fun sendMessage(request: SendRequest): Boolean { val body = sendAdapter.toJson(request); return socket?.send(frame("SEND", "destination:/app/message\ncontent-type:application/json", body)) ?: false }
    fun sendReceipt(id: String, state: String): Boolean { val body="{\"message_id\":\"$id\",\"state\":\"$state\"}"; return socket?.send(frame("SEND","destination:/app/receipt\ncontent-type:application/json\nreceipt:$id",body)) ?: false }
    private fun open() { val token=tokenProvider() ?: return; val request=Request.Builder().url(wsUrl).header("Authorization","Bearer $token").build(); socket=client.newWebSocket(request, object: WebSocketListener(){ override fun onOpen(w:WebSocket,r:Response){attempt=0;w.send(frame("CONNECT","accept-version:1.2\nheart-beat:10000,10000\nauthorization:Bearer $token",null))} override fun onMessage(w:WebSocket,text:String){handle(text,w)} override fun onFailure(w:WebSocket,t:Throwable,r:Response?){socket=null;retry()} override fun onClosed(w:WebSocket,c:Int,reason:String){socket=null;retry()} }) }
    private fun handle(raw:String,w:WebSocket){val command=raw.substringBefore('\n').trim();if(command=="CONNECTED"){w.send(frame("SUBSCRIBE","id:ebuddy-user\ndestination:/user/queue/events\nack:client-individual",null));return};if(command=="MESSAGE"){val body=raw.substringAfter("\n\n","").trimEnd('\u0000');runCatching{adapter.fromJson(body)?.data}.getOrNull()?.let{_events.tryEmit(it)}}}
    private fun retry(){if(!wanted)return;val delayMs=minOf(30000L,1000L shl minOf(attempt++,5));scope.launch{delay(delayMs);if(wanted&&socket==null)open()}}
    private fun frame(command:String,headers:String,body:String?):String{return command+"\n"+headers+"\n\n"+(body ?: "")+'\u0000'}
    @JsonClass(generateAdapter=true) data class StompEnvelope(val data: MessageDto?)
}
