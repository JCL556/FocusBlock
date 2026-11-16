package io.github.xiaoxia.focusblock

import android.app.Application
import io.github.xiaoxia.focusblock.data.Settings
import io.github.xiaoxia.focusblock.notify.Notifier

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 渠道先按默认设置建好，读到真实设置后会更新
        runCatching { Notifier.ensureChannel(this, Settings()) }
    }
}
