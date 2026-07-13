package com.flowtrans

import android.app.Application
import com.github.kr328.clash.common.Global

class FlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The vendored mihomo bridge reads Global.application (for its home dir,
        // versionName, content resolver). Must be set before Bridge is touched.
        Global.init(this)
    }
}
