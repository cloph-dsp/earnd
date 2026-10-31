package com.earnd.app

import android.app.Application

class EarndApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
