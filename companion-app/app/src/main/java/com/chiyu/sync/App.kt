package com.chiyu.sync

import android.app.Application
import com.chiyu.sync.logic.InterHandshake
class App: Application() {
    lateinit var conn : InterHandshake
}