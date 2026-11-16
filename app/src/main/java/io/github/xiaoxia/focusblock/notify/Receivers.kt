package io.github.xiaoxia.focusblock.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.Store
import io.github.xiaoxia.focusblock.data.TimerPhase
import io.github.xiaoxia.focusblock.data.sameLocalDay
import io.github.xiaoxia.focusblock.domain.BlockFlow
import io.github.xiaoxia.focusblock.domain.Maintenance
import io.github.xiaoxia.focusblock.domain.Missed
import io.github.xiaoxia.focusblock.domain.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/** 让 BroadcastReceiver 里的挂起任务跑完再结束 */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            runCatching { pending.finish() }
        }
    }
}

/**
 * 到点提醒。排布时一块一条闹钟，同刻有两个块就是两条通知；这里按 id 列表逐个弹。
 * kind = start 提醒「该开始了」，kind = end 提醒「该收尾了」。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val ids = intent.getStringExtra(Scheduler.EXTRA_BLOCK_IDS)?.split(",")?.filter { it.isNotBlank() }
            ?: return
        if (ids.isEmpty()) return
        val triggerAt = intent.getLongExtra(Scheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND) ?: Scheduler.KIND_START

        runAsync {
            val store = Store.of(context)
            val data = store.current()
            val now = System.currentTimeMillis()
            val entries = ids.mapNotNull { id ->
                val block = data.blocks.find { it.id == id } ?: return@mapNotNull null
                if (!block.enabled) return@mapNotNull null
                // 这一场已经点过完成 / 跳过就别再弹了。
                // 「超时」不挡：挂上标签以后还能补点，结束提醒和开始提醒照旧要响
                val handled = data.sessions.any {
                    it.blockId == id && sameLocalDay(it.startTs, now) &&
                        (it.state == SessionState.DONE || it.state == SessionState.SKIPPED)
                }
                if (handled) return@mapNotNull null
                Notifier.Entry(
                    blockId = block.id,
                    title = block.title,
                    timeLabel = block.timeLabel,
                    fullscreen = block.fullscreen,
                    point = block.point,
                )
            }
            if (entries.isEmpty()) return@runAsync
            // 一块一条通知：同一时刻有两块就弹两条，按钮各管各的
            entries.forEach { Notifier.showBlockReminder(context, it, data.settings, triggerAt, kind) }
        }
    }

    companion object {
        const val ACTION_FIRE = "io.github.xiaoxia.focusblock.action.FIRE"
    }
}

/**
 * 番茄钟到点。闹钟是系统排的，所以 App 在后台、锁屏、被划掉都照样响。
 * 这里顺手把这一轮的账结了：专注落一条完成，休息直接清掉，再弹通知。
 */
class TimerEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        runAsync {
            val store = Store.of(context)
            val now = System.currentTimeMillis()
            val timer = store.current().timer ?: return@runAsync
            // 暂停过、或者已经在 App 里收过尾了，就不再重复记账
            if (!timer.isRunning || timer.remainingMs(now) > 0L) return@runAsync

            if (timer.phase == TimerPhase.FOCUS) {
                store.update { cur ->
                    val ct = cur.timer ?: return@update cur
                    if (!ct.isRunning || ct.remainingMs(now) > 0L) return@update cur
                    cur.copy(
                        timer = null,
                        sessions = cur.sessions + Session(
                            id = UUID.randomUUID().toString(),
                            blockId = ct.blockId,
                            titleSnapshot = ct.title,
                            startTs = ct.startTs.coerceAtLeast(now - ct.plannedMs),
                            endTs = now,
                            state = SessionState.DONE,
                            roundIndex = ct.roundIndex,
                        ),
                    )
                }
            } else {
                store.update { cur -> cur.copy(timer = null) }
            }
            Notifier.showTimerDone(context, timer.phase, timer.title, store.current().settings)
        }
    }

    companion object {
        const val ACTION_DONE = "io.github.xiaoxia.focusblock.action.TIMER_DONE"
    }
}

/**
 * 到点 5 分钟还没点「开始 / 完成」的清算：补一条「超时」标签。
 * 只加标签，不动进行中标记、不改卡片亮暗——用户随时可以补点。
 */
class MissedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return
        val blockId = intent.getStringExtra(Scheduler.EXTRA_BLOCK_IDS) ?: return
        val startMs = intent.getLongExtra(Scheduler.EXTRA_TRIGGER_AT, 0L)
        if (startMs <= 0L) return

        runAsync {
            val store = Store.of(context)
            val now = System.currentTimeMillis()
            val block = store.current().blocks.find { it.id == blockId } ?: return@runAsync
            if (!Missed.isDue(store.current(), block, startMs, now)) return@runAsync

            store.update { cur ->
                val b = cur.blocks.find { it.id == blockId } ?: return@update cur
                if (!Missed.isDue(cur, b, startMs, now)) return@update cur
                cur.copy(sessions = cur.sessions + BlockFlow.missedSession(b, startMs))
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "io.github.xiaoxia.focusblock.action.MISSED_CHECK"
    }
}

/** 每天 00:05 重排，顺便补账 */
class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_DAILY) return
        runAsync { Maintenance.run(context) }
    }
}

/** 开机 / 应用更新 / 改时间 后重排 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 显式核对 action，避免被伪造的广播打到
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> runAsync { Maintenance.run(context) }

            else -> Unit
        }
    }
}
