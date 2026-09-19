package com.ebuddy.android.data.repository

import com.ebuddy.android.data.local.*
import com.ebuddy.android.data.remote.*
import com.ebuddy.android.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class EbuddyRepository(private val api:EbuddyApi, private val auth:AuthInterceptor, private val store:SessionStore, private val dao:MessageDao, private val stomp:StompClient) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val _session=MutableStateFlow<Session?>(null); val session:StateFlow<Session?> = _session.asStateFlow()
    private val _error=MutableSharedFlow<String>(extraBufferCapacity=8); val errors:SharedFlow<String> = _error.asSharedFlow()
    init { scope.launch { stomp.events.collect { dao.upsert(toEntity(it)); stomp.sendReceipt(it.id,"delivered") } } }
    suspend fun login(username:String,password:String):Result<Session> = runCatching { val r=api.login(LoginRequest(username,password));val s=Session(r.token,r.expiresAt,User(r.user.id,r.user.username,r.user.displayName));store.save(s.token);auth.setToken(s.token);_session.value=s;stomp.connect();s }.onFailure{_error.tryEmit(message(it))}
    suspend fun logout(){store.clear();auth.setToken(null);_session.value=null;stomp.close()}
    fun observeConversation(otherId:String,myId:String):Flow<List<Message>> = dao.observeConversation(conversation(myId,otherId)).map{it.map(::fromEntity)}
    suspend fun loadContacts():Result<List<Contact>> = runCatching { api.contacts().map{Contact(it.user.id,it.user.username,it.user.displayName,it.state)} }.onFailure{_error.tryEmit(message(it))}
    suspend fun send(myId:String,toId:String,body:String):Result<Message>{val id=UUID.randomUUID().toString();val local=Message(id, myId,toId,id,Long.MIN_VALUE+System.currentTimeMillis(),body,"sent",System.currentTimeMillis()/1000);dao.upsert(toEntity(local));return runCatching{val r=api.send(SendRequest(toId,id,body));val m=fromDto(r);dao.upsert(toEntity(m));m}.onFailure{_error.tryEmit(message(it))}}
    suspend fun sync(cursor:Long):Result<Long> = runCatching { val r=api.sync(cursor);dao.upsertAll(r.items.map(::toEntity));r.nextCursor.toLongOrNull()?:cursor }.onFailure{_error.tryEmit(message(it))}
    private fun conversation(a:String,b:String)=if(a<b)"$a:$b" else "$b:$a"
    private fun toEntity(m:Message)=MessageEntity(m.id,conversation(m.fromUserId,m.toUserId),m.fromUserId,m.toUserId,m.clientMessageId,m.sequence,m.body,m.status,m.createdAt,m.deliveredAt,m.readAt)
    private fun toEntity(d:MessageDto)=MessageEntity(d.id,conversation(d.fromUserId,d.toUserId),d.fromUserId,d.toUserId,d.clientMessageId,d.sequence,d.body,d.status,parseTime(d.createdAt),d.deliveredAt?.let(::parseTime),d.readAt?.let(::parseTime))
    private fun fromEntity(e:MessageEntity)=Message(e.id,e.fromUserId,e.toUserId,e.clientMessageId,e.sequence,e.body,e.status,e.createdAt,e.deliveredAt,e.readAt)
    private fun fromDto(d:MessageDto)=fromEntity(toEntity(d))
    private fun parseTime(v:String)=v.toLongOrNull()?:runCatching{val f=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX",Locale.US);f.timeZone=TimeZone.getTimeZone("UTC");f.parse(v)?.time?.div(1000)}.getOrNull()?:System.currentTimeMillis()/1000
    private fun message(t:Throwable)=when(t){is HttpException->"Server error ${t.code()}";else->t.message?:"Network error"}
}
