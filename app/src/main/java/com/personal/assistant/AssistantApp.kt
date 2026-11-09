package com.personal.assistant

import android.app.Application

class AssistantApp : Application() {

    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
    }
}