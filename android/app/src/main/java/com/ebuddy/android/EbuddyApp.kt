package com.ebuddy.android

import android.app.Application
import androidx.room.Room
import com.ebuddy.android.data.local.EbuddyDatabase
import com.ebuddy.android.data.remote.*
import com.ebuddy.android.data.repository.EbuddyRepository
import okhttp3.OkHttpClient

class EbuddyApp: Application() {
    lateinit var repository:EbuddyRepository; private set
    override fun onCreate(){super.onCreate();val db=Room.databaseBuilder(this,EbuddyDatabase::class.java,"ebuddy.db").fallbackToDestructiveMigration().build();val (api,auth)=ApiFactory.create(this,BuildConfig.API_BASE_URL);val http=OkHttpClient.Builder().build();val store=SessionStore(this);val stomp=StompClient(http,BuildConfig.WS_URL){auth.token()};repository=EbuddyRepository(api,auth,store,db.messages(),stomp)}
}
