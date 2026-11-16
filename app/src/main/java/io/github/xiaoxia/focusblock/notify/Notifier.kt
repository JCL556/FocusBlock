package io.github.xiaoxia.focusblock.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.xiaoxia.focusblock.MainActivity
import io.github.xiaoxia.focusblock.R
import io.github.xiaoxia.focusblock.data.Settings
import io.github.xiaoxia.focusblock.data.TimerPhase
import io.github.xiaoxia.focusblock.domain.Scheduler

object Notifier {

    const val CHANNEL_ALARM = "focusblock_alarm"
    private const val GROUP = "focusblock_alarms"

    const val ACT_START = "start"
    const val ACT_END = "end"
    /** 老通知上的「延后 5 分钟」已下线，常量留着兜底，点了只关通知 */
    const val ACT_POSTPONE = "postpone"
    const val ACT_SKIP = "skip"

    /**
     * 建/取通知通道，返回该用的通道 id。
     *
     * 安卓的通道一旦建好，铃声就改不动了（同 id 再建只会沿用旧设置）。
     * 所以通道 id 带上「铃声 + 震动 + 勿扰」的指纹：设置改了就换一条新通道，
     * 旧的一次性清掉，免得系统设置里堆一串。
     *
     * 铃声只有全局一份（设置页那个），所有块共用，不再按块覆盖。
     */
    fun ensureChannel(context: Context, settings: Settings): String {
        val sound: Uri = settings.ringtoneUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelId = channelIdFor(sound, settings.vibrate, settings.respectDnd)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId
        val nm = context.getSystemService(NotificationManager::class.java) ?: return channelId

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            enableVibration(settings.vibrate)
            vibrationPattern = if (settings.vibrate) longArrayOf(0, 400, 200, 400) else null
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(true)
            // 勿扰豁免需要通知策略权限，没有就跳过（别崩）
            if (!settings.respectDnd && nm.isNotificationPolicyAccessGranted) {
                runCatching { setBypassDnd(true) }
            }
        }
        runCatching { nm.createNotificationChannel(channel) }
        // 只留当前这条：旧指纹的通道、以及老版本那条没有后缀的通道，一起清掉。
        // 但正在显示通知的通道不能删——删通道会连带把那条通知也抹掉。
        // （现在所有块共用一条通道，这条保护留着，改设置当场重排时就不会误删）
        val busy = runCatching {
            nm.activeNotifications.map { it.notification.channelId }.toSet()
        }.getOrDefault(emptySet())
        nm.notificationChannels
            .filter {
                it.id != channelId &&
                    it.id !in busy &&
                    (it.id == CHANNEL_ALARM || it.id.startsWith("${CHANNEL_ALARM}_"))
            }
            .forEach { runCatching { nm.deleteNotificationChannel(it.id) } }
        return channelId
    }

    /** 通道 id = 前缀 + 铃声/震动/勿扰指纹（重启也不会变） */
    private fun channelIdFor(sound: Uri, vibrate: Boolean, respectDnd: Boolean): String {
        val key = "$sound|${if (vibrate) 1 else 0}|${if (respectDnd) 1 else 0}"
        return "${CHANNEL_ALARM}_${key.hashCode() and 0x7fffffff}"
    }

    /**
     * 到点提醒，一条通知只对应一个块。
     * 同一时刻有两个块时，调用方会分别调两次，弹出两条通知，
     * 通知上的按钮也只作用于自己那一块，不会误伤别人。
     * kind = start 是「该开始了」，kind = end 是「该收尾了」。
     */
    fun showBlockReminder(
        context: Context,
        entry: Entry,
        settings: Settings,
        triggerAt: Long,
        kind: String,
    ) {
        val channelId = ensureChannel(context, settings)

        val isEnd = kind == Scheduler.KIND_END

        // 第一行只放软件名，第二行才是「该开始了 · 任务名」，第三行给时间和下一步
        val appName = context.getString(R.string.notif_app_name)
        val head = context.getString(if (isEnd) R.string.notif_end_head else R.string.notif_start_head)
        val hint = context.getString(if (isEnd) R.string.notif_end_hint else R.string.notif_start_hint)
        val line2 = "$head · ${entry.title}"
        val detail = "${entry.timeLabel}　$hint"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, if (isEnd) 7002 else 7001, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_tomato)
            .setColor(ACCENT)
            .setContentTitle(appName)
            .setContentText(line2)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$line2\n$detail")
                    .setSummaryText(entry.timeLabel)
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setWhen(triggerAt)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .setGroupSummary(false)

        // 全屏 intent：只有开了「锁屏强提醒」且系统允许才用
        val canFullscreen = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else true
        if (entry.fullscreen && canFullscreen) {
            builder.setFullScreenIntent(contentPi, true)
        }

        when {
            isEnd -> builder.addAction(
                0, context.getString(R.string.action_end),
                actionPi(context, entry, triggerAt, ACT_END, 1)
            )
            // 时间点一步到位：到点就打卡，不再让它先进「进行中」再点第二下
            entry.point -> builder.addAction(
                0, context.getString(R.string.action_done),
                actionPi(context, entry, triggerAt, ACT_END, 1)
            )
            else -> builder.addAction(
                0, context.getString(R.string.action_start),
                actionPi(context, entry, triggerAt, ACT_START, 1)
            )
        }

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching {
                NotificationManagerCompat.from(context).notify(idFor(entry.blockId, kind), builder.build())
            }
        }
    }

    /**
     * 番茄钟到点。App 在后台、锁屏、被划掉都会响，因为闹钟是系统排的。
     * 通知只报告结果，点一下回 App。
     */
    fun showTimerDone(context: Context, phase: TimerPhase, title: String, settings: Settings) {
        val channelId = ensureChannel(context, settings)
        val focusDone = phase == TimerPhase.FOCUS
        val line2 = context.getString(
            if (focusDone) R.string.notif_focus_end_head else R.string.notif_break_end_head
        )
        val body = context.getString(
            if (focusDone) R.string.notif_focus_end_body else R.string.notif_break_end_body,
            title.ifBlank { "自由专注" },
        )
        val openPi = PendingIntent.getActivity(
            context, 7010,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_tomato)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.notif_app_name))
            .setContentText("$line2 · $body")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line2\n$body"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(TIMER_ID, builder.build()) }
        }
    }

    /** 完成后单独提醒一句「去休息」，不带按钮 */
    fun showRestTip(context: Context, tip: String, settings: Settings) {
        val channelId = ensureChannel(context, settings)
        val line2 = "${context.getString(R.string.notif_rest_head)} · $tip"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_tomato)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.notif_app_name))
            .setContentText(line2)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line2))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(REST_ID, builder.build()) }
        }
    }

    fun cancel(context: Context, blockId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(idFor(blockId, Scheduler.KIND_START)) }
        runCatching { NotificationManagerCompat.from(context).cancel(idFor(blockId, Scheduler.KIND_END)) }
    }

    private const val REST_ID = 424242

    /** 番茄钟到点的通知 id，一次只有一条 */
    private const val TIMER_ID = 424243

    /** 通知里那点暖色，和 App 的主色对得上 */
    private const val ACCENT = 0xFFFF7A7A.toInt()

    private fun idFor(blockId: String, kind: String): Int =
        blockId.hashCode() * 2 + if (kind == Scheduler.KIND_END) 1 else 0

    private fun actionPi(
        context: Context,
        entry: Entry,
        triggerAt: Long,
        action: String,
        index: Int,
    ): PendingIntent {
        val i = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = "io.github.xiaoxia.focusblock.action.$action"
            putExtra(Scheduler.EXTRA_BLOCK_IDS, entry.blockId)
            putExtra(Scheduler.EXTRA_TRIGGER_AT, triggerAt)
            putExtra(NotificationActionReceiver.EXTRA_TITLE, entry.title)
            putExtra(NotificationActionReceiver.EXTRA_ACTION, action)
        }
        val base = Scheduler.alarmCode(entry.blockId, triggerAt)
        return PendingIntent.getBroadcast(
            context, base * 8 + index, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    data class Entry(
        val blockId: String,
        val title: String,
        val timeLabel: String,
        val fullscreen: Boolean,
        /** 时间点块：通知上直接给「完成」，不先进进行中 */
        val point: Boolean = false,
    )
}
