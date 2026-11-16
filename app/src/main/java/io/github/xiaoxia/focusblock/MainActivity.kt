package io.github.xiaoxia.focusblock

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.xiaoxia.focusblock.data.ThemeMode
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.FocusBlockApp

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TIMER = "open_timer"
        const val PREFS = "fb_ui"
        const val KEY_THEME = "theme_mode"
        private const val KEY_NOTIF_ASKED = "notif_asked"
    }

    private val vm: AppVm by viewModels()
    private var openTimerSignal by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTimerSignal = intent?.getBooleanExtra(EXTRA_OPEN_TIMER, false) == true

        askNotificationOnce()

        setContent {
            FocusBlockApp(
                vm = vm,
                initialTheme = cachedTheme(),
                openTimer = openTimerSignal,
                onTimerOpened = { openTimerSignal = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TIMER, false)) {
            openTimerSignal = true
        }
    }

    /** 开屏那一帧用的主题：DataStore 还没读完时先照这个铺底色 */
    private fun cachedTheme(): ThemeMode = runCatching {
        val name = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_THEME, null)
        ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    /** 13+ 首次进入时申请一次通知权限 */
    private fun askNotificationOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (sp.getBoolean(KEY_NOTIF_ASKED, false)) return
        sp.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}
