package com.v2ray.compose

import android.app.Application
import com.v2ray.compose.core.AndroidContext

class V2RayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContext.init(this)
    }
}
