package com.saubh.deskbuddy

import android.app.Application
import android.content.Context

class DeskBuddyApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.autoConnector.start(this)
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as DeskBuddyApplication).graph
