package io.github.xiaoxia.focusblock.domain

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.xiaoxia.focusblock.data.Settings

/**
 * 一条权限的状态。
 * manual = 厂商 ROM 那几条系统读不到真实状态，只能让用户自己确认。
 */
data class PermEntry(
    val key: String,
    val name: String,
    val desc: String,
    val granted: Boolean,
    val manual: Boolean = false,
    val open: (Context) -> Unit,
)

object PermissionAudit {

    fun notificationGranted(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun needsRuntimeNotification(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun entries(context: Context, settings: Settings): List<PermEntry> {
        val list = mutableListOf<PermEntry>()

        // 1. 通知
        list += PermEntry(
            key = "notification",
            name = "通知权限",
            desc = "需要允许发通知，不开启的话到点不会提醒你",
            granted = notificationGranted(context),
            open = { ctx ->
                runCatching {
                    ctx.startActivity(
                        Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
        )

        // 2. 精确闹钟：只有 31、32 需要用户点
        if (Build.VERSION.SDK_INT in 31..32) {
            val am = context.getSystemService(AlarmManager::class.java)
            val granted = am?.canScheduleExactAlarms() ?: true
            list += PermEntry(
                key = "exact",
                name = "精确闹钟",
                desc = "需要允许精确闹钟，不开启的话提醒可能晚几分钟甚至不响",
                granted = granted,
                open = { ctx ->
                    runCatching {
                        ctx.startActivity(
                            Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }

        // 3. 全屏通知：34+
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val granted = nm?.canUseFullScreenIntent() ?: false
            list += PermEntry(
                key = "fullscreen",
                name = "全屏通知",
                desc = "需要允许全屏通知，不开启的话锁屏时只会普通通知，不会亮屏弹出来",
                granted = granted,
                open = { ctx ->
                    runCatching {
                        ctx.startActivity(
                            Intent(SysSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }

        // 4. 电池优化
        val pm = context.getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        list += PermEntry(
            key = "battery",
            name = "电池优化白名单",
            desc = "需要把本应用加入白名单，不开启的话后台可能被系统清掉、漏响",
            granted = ignoring,
            open = { ctx ->
                val ok = runCatching {
                    ctx.startActivity(
                        Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.isSuccess
                if (!ok) openAppDetails(ctx)
            },
        )

        // 5-7. 厂商 ROM：状态读不到，只能手动确认
        val vendor = Vendor.entriesFor(Build.MANUFACTURER)
        vendor.forEach { v ->
            list += PermEntry(
                key = v.key,
                name = v.name,
                desc = v.desc,
                granted = settings.manualConfirmed.contains(v.key),
                manual = true,
                open = { ctx -> openVendor(ctx, v.components) },
            )
        }

        // 8. 勿扰豁免（可选）
        val nm = context.getSystemService(NotificationManager::class.java)
        val dnd = nm?.isNotificationPolicyAccessGranted ?: false
        list += PermEntry(
            key = "dnd",
            name = "勿扰豁免（可选）",
            desc = "开了勿扰还想被提醒，就需要给本应用豁免通知策略；不开就按勿扰走",
            granted = dnd,
            open = { ctx ->
                runCatching {
                    ctx.startActivity(
                        Intent(SysSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
        )

        return list
    }

    private fun openAppDetails(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openVendor(ctx: Context, components: List<ComponentName>) {
        for (c in components) {
            val ok = runCatching {
                ctx.startActivity(Intent().apply {
                    this.component = c
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.isSuccess
            if (ok) return
        }
        openAppDetails(ctx)
    }
}

/** 厂商路径常量表，找不到就给通用页面兜底 */
private object Vendor {

    data class Item(
        val key: String,
        val name: String,
        val desc: String,
        val components: List<ComponentName>,
    )

    private fun c(pkg: String, cls: String) = ComponentName(pkg, cls)

    fun entriesFor(manufacturer: String): List<Item> {
        val m = manufacturer.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "需要在安全中心允许自启动，不开启的话重启后闹钟不会恢复",
                    listOf(
                        c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                        c("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                    )
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "需要允许后台弹出界面，不开启的话锁屏强提醒弹不出来",
                    listOf(c("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"))
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "需要允许锁屏显示，不开启的话锁屏上看不到提醒",
                    listOf(c("com.miui.securitycenter", "com.miui.notification.settings.NotificationSettingsActivity"))
                ),
            )

            m.contains("huawei") || m.contains("honor") -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "需要在手机管家里允许自启动，不开启的话重启后闹钟不会恢复",
                    listOf(
                        c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        c("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                    )
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "需要允许后台弹出界面，不开启的话锁屏强提醒弹不出来",
                    listOf(c("com.huawei.systemmanager", "com.huawei.systemmanager.notificationmanager.LocalNotificationSettingActivity"))
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "需要允许锁屏通知显示，不开启的话锁屏上看不到提醒",
                    listOf(c("com.huawei.systemmanager", "com.huawei.systemmanager.notificationmanager.NotificationCenterActivity"))
                ),
            )

            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "需要在权限管理里允许自启动，不开启的话重启后闹钟不会恢复",
                    listOf(
                        c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        c("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                    )
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "需要允许后台弹出界面，不开启的话锁屏强提醒弹不出来",
                    listOf(c("com.coloros.safecenter", "com.coloros.safecenter.sysfloatwindow.FloatWindowListActivity"))
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "需要允许锁屏通知显示，不开启的话锁屏上看不到提醒",
                    listOf(c("com.coloros.notificationmanager", "com.coloros.notificationmanager.AppNotificationActivity"))
                ),
            )

            m.contains("vivo") || m.contains("iqoo") -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "需要在权限管理里允许自启动，不开启的话重启后闹钟不会恢复",
                    listOf(
                        c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    )
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "需要允许后台弹出界面，不开启的话锁屏强提醒弹不出来",
                    listOf(c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"))
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "需要允许锁屏通知显示，不开启的话锁屏上看不到提醒",
                    listOf(c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"))
                ),
            )

            m.contains("meizu") -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "需要在手机管家里允许自启动，不开启的话重启后闹钟不会恢复",
                    listOf(c("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"))
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "需要允许后台弹出界面，不开启的话锁屏强提醒弹不出来",
                    listOf(c("com.meizu.safe", "com.meizu.safe.permission.PermissionMainActivity"))
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "需要允许锁屏通知显示，不开启的话锁屏上看不到提醒",
                    listOf(c("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"))
                ),
            )

            m.contains("samsung") -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "需要在电池设置里把这个应用设为「不受限制」，不设置的话后台容易被清掉",
                    listOf(c("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "需要允许后台弹出界面，不开启的话锁屏强提醒弹不出来",
                    listOf(c("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "需要允许锁屏通知显示，不开启的话锁屏上看不到提醒",
                    listOf(c("com.samsung.android.settings", "com.samsung.android.settings.notification.AppNotificationSettings"))
                ),
            )

            else -> listOf(
                Item(
                    "vendor_autostart", "自启动",
                    "你的机型没有已知路径，请到系统设置的权限管理里手动允许自启动",
                    emptyList()
                ),
                Item(
                    "vendor_popup", "后台弹出界面",
                    "你的机型没有已知路径，请到系统设置里手动允许后台弹出界面",
                    emptyList()
                ),
                Item(
                    "vendor_lockscreen", "锁屏显示",
                    "你的机型没有已知路径，请到系统设置里手动允许锁屏显示",
                    emptyList()
                ),
            )
        }
    }
}

/** 供 Activity 判断是否可以直接发起运行时申请 */
fun Activity.canRequestNotification(): Boolean = Build.VERSION.SDK_INT >= 33
