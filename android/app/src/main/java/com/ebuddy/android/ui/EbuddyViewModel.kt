package com.ebuddy.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebuddy.android.data.repository.EbuddyRepository
import com.ebuddy.android.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(val loading:Boolean=false,val session:Session?=null,val contacts:List<Contact> = emptyList(),val selected:Contact?=null,val error:String?=null,val syncing:Boolean=false)
class EbuddyViewModel(private val repo:EbuddyRepository):ViewModel(){
    private val _state=MutableStateFlow(UiState());val state:StateFlow<UiState> = _state.asStateFlow()
    private val selected=MutableStateFlow<Contact?>(null)
    val messages:StateFlow<List<Message>> = combine(repo.session,selected){s,c->s to c}.flatMapLatest{(s,c)->if(s!=null&&c!=null)repo.observeConversation(c.id,s.user.id) else flowOf(emptyList())}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init{viewModelScope.launch{repo.session.collect{session->_state.update{old->old.copy(session=session)}}};viewModelScope.launch{repo.errors.collect{e->_state.update{old->old.copy(error=e,loading=false,syncing=false)}}};viewModelScope.launch{repo.restore()}}
    fun login(user:String,password:String){if(user.isBlank()||password.isBlank()){_state.update{it.copy(error="Enter username and password")};return};viewModelScope.launch{_state.update{it.copy(loading=true,error=null)};repo.login(user.trim(),password).onSuccess{loadContacts()}.onFailure{error->_state.update{it.copy(loading=false,error=error.message?:"Login failed")}}}}
    fun register(user:String,password:String,displayName:String){if(user.isBlank()||password.length<8||displayName.isBlank()){_state.update{it.copy(error="Use a display name, username, and password of at least 8 characters")};return};viewModelScope.launch{_state.update{it.copy(loading=true,error=null)};repo.register(user.trim(),password,displayName.trim()).onSuccess{loadContacts()}.onFailure{error->_state.update{it.copy(loading=false,error=error.message?:"Registration failed")}}}}
    fun loadContacts(){viewModelScope.launch{repo.loadContacts().onSuccess{list->_state.update{it.copy(loading=false,contacts=list)}}.onFailure{e->_state.update{it.copy(loading=false,error=e.message?:"Could not load contacts")}}}}
    fun select(c:Contact?){selected.value=c;_state.update{it.copy(selected=c)}}
    fun send(text:String){val s=_state.value.session?:return;val c=_state.value.selected?:return;if(text.isBlank())return;viewModelScope.launch{repo.send(s.user.id,c.id,text).onFailure{e->_state.update{it.copy(error=e.message?:"Message not sent")}}}}
    fun sync(){viewModelScope.launch{_state.update{it.copy(syncing=true)};repo.sync(0);_state.update{it.copy(syncing=false)}}}
    fun clearError(){_state.update{it.copy(error=null)}}
    fun logout(){viewModelScope.launch{repo.logout();_state.value=UiState()}}
}
class EbuddyViewModelFactory(private val repo:EbuddyRepository):androidx.lifecycle.ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T=EbuddyViewModel(repo) as T}
