package io.github.xiaoxia.focusblock.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.xiaoxia.focusblock.MainActivity
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.BlockStart
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.notify.AlarmReceiver
import io.github.xiaoxia.focusblock.notify.DailyReceiver
import io.github.xiaoxia.focusblock.notify.MissedReceiver
import io.github.xiaoxia.focusblock.notify.TimerEndReceiver
import java.util.Calendar

/**
 * 闹钟排布。范围未来 48 小时，开机 / 每天 00:05 / 改表后各重排一次。
 * 一个块一条闹钟：同一时刻有两个块就弹两条通知，各自的按钮只管自己那一块。
 */
object Scheduler {

    const val EXTRA_BLOCK_IDS = "block_ids"
    const val EXTRA_TRIGGER_AT = "trigger_at"
    const val EXTRA_KIND = "kind"

    /** 到开始时间：该点「开始」了 */
    const val KIND_START = "start"

    /** 到结束时间：该点「结束」了 */
    const val KIND_END = "end"

    const val ACTION_DAILY = "io.github.xiaoxia.focusblock.action.DAILY"

    /** 「超时」判定宽限：到点满 5 分钟还没点开始 / 完成才算（口径见 [Missed]） */
    const val MISSED_GRACE_MS = Missed.GRACE_MS

    private const val WINDOW_MS = 48L * 3600_000L
    private const val PREFS = "fb_alarm_registry"
    private const val KEY_CODES = "codes"

    private const val DAILY_CODE = 0x0F0CB10C

    /** 番茄钟到点闹钟固定用这一个 requestCode，排新的会覆盖旧的 */
    private const val TIMER_CODE = 0x0F0C71E1

    /** 番茄钟闹钟比实际到点晚这么久，给 App 内的收尾留出先手 */
    private const val TIMER_SLACK_MS = 1_500L

    // ---------------------------------------------------------------- 排布

    fun rescheduleAll(context: Context, data: AppData, now: Long = System.currentTimeMillis()) {
        cancelAll(context)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(am)) return

        val codes = mutableSetOf<Int>()

        // 一个块一条闹钟：同一时刻有两个块，就是两条闹钟、两条通知，
        // 通知上的按钮各管各的，不会只作用到第一块
        for (plan in planAlarms(data, now)) {
            val code = alarmCode("${plan.kind}:${plan.blockId}", plan.triggerAt)
            val pi = blockAlarmIntent(context, code, plan.blockId, plan.triggerAt, plan.kind)
            runCatching {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(plan.triggerAt, showAlarmIntent(context)), pi)
            }
            codes += code
        }

        // 到点 5 分钟还没动作 → 记「超时」。每场只排一条
        for ((startMs, block) in missedSlots(data, now)) {
            val missCode = missedCode(block.id, startMs)
            runCatching {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    Missed.deadline(startMs),
                    missedIntent(context, missCode, block.id, startMs)
                )
            }
            codes += missCode
        }

        // 番茄钟（自由专注 / 休息）跑着的话，也排一条到点闹钟，后台照样响
        data.timer?.let { timer ->
            if (timer.isRunning) {
                val remain = timer.remainingMs(now)
                if (remain > 0L) scheduleTimerEnd(context, now + remain)
            }
        }

        // 每天 00:05 重排，保证滚动窗口一直有效
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDailyAt(now), dailyIntent(context))
        }
        codes += DAILY_CODE

        saveCodes(context, codes)
    }

    /**
     * 未来 48 小时要响的每一条提醒。纯计算，不碰系统服务，单测直接跑。
     * 同一时刻的块不合并：一块一条，各自一条通知。
     */
    fun planAlarms(data: AppData, now: Long): List<PlannedAlarm> {
        val out = mutableListOf<PlannedAlarm>()
        for (block in data.blocks) {
            if (!block.enabled) continue
            // -1 起算：窗口前一天开的场次也可能落在里面
            for (offset in -1..2) {
                val startMs = occurrenceStart(now, block, offset) ?: continue
                // 到点即提醒：没有提前量，闹钟就落在设定时刻
                if (startMs > now && startMs <= now + WINDOW_MS) {
                    out += PlannedAlarm(startMs, KIND_START, block.id)
                }
                // 时间点没有结束时刻，只有时间段才排「该结束」
                val endMs = startMs + block.durationMin * 60_000L
                if (!block.point && endMs > now && endMs <= now + WINDOW_MS) {
                    out += PlannedAlarm(endMs, KIND_END, block.id)
                }
            }
        }
        return out
    }

    /** 未来 48 小时要补「超时」的场次（开场时刻 + 块） */
    private fun missedSlots(data: AppData, now: Long): List<Pair<Long, Block>> {
        val out = mutableListOf<Pair<Long, Block>>()
        for (block in data.blocks) {
            if (!block.enabled) continue
            for (offset in -1..2) {
                val startMs = occurrenceStart(now, block, offset) ?: continue
                val missAt = Missed.deadline(startMs)
                if (missAt > now && missAt <= now + WINDOW_MS) out += startMs to block
            }
        }
        return out
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (code in loadCodes(context)) {
            // 判定的 PendingIntent 与提醒的不是同一个类/action，两个都试一次
            cancelPi(context, am, code, AlarmReceiver::class.java, AlarmReceiver.ACTION_FIRE)
            cancelPi(context, am, code, MissedReceiver::class.java, MissedReceiver.ACTION_CHECK)
        }
        cancelTimerEnd(context)
        runCatching { am.cancel(dailyIntent(context)) }
        clearCodes(context)
    }

    // ---------------------------------------------------------------- 番茄钟

    /**
     * 番茄钟（含休息）到点闹钟。跑着才排，暂停 / 重置 / 收尾就撤。
     *
     * 比计算出来的时刻晚一点点：App 开着的时候计时页会先自己收尾（弹完成提示、
     * 顺手把这条闹钟撤掉），闹钟只在 App 不在前台时兜底响，两边不会抢着记账。
     */
    fun scheduleTimerEnd(context: Context, at: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(am)) return
        runCatching {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(at + TIMER_SLACK_MS, showAlarmIntent(context)),
                timerIntent(context)
            )
        }
    }

    fun cancelTimerEnd(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(context, TimerEndReceiver::class.java).apply { action = TimerEndReceiver.ACTION_DONE }
        val pi = PendingIntent.getBroadcast(
            context, TIMER_CODE, i,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let {
            runCatching { am.cancel(it) }
            it.cancel()
        }
    }

    /** 用同一个 action 才能让 FLAG_NO_CREATE 匹配到已经排出去的那条闹钟 */
    private fun cancelPi(
        context: Context,
        am: AlarmManager,
        code: Int,
        cls: Class<*>,
        action: String,
    ) {
        val i = Intent(context, cls).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            context, code, i,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let {
            runCatching { am.cancel(it) }
            it.cancel()
        }
    }

    // ---------------------------------------------------------------- 计算

    fun canScheduleExact(am: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

    /**
     * 取「今天 + offset 天」这一天的该块开始时刻（毫秒）。
     * 该天不在重复规则里返回 null。
     */
    fun occurrenceStart(now: Long, block: Block, offsetDays: Int): Long? {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, offsetDays)
        }
        val weekIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (block.days.getOrElse(weekIndex) { false }.not()) return null
        cal.add(Calendar.MINUTE, block.startMin)
        return cal.timeInMillis
    }

    /** 「今天」的周索引，0 = 周一 */
    fun todayIndex(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    fun nextDailyAt(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    fun alarmCode(key: String, triggerAt: Long): Int =
        key.hashCode() * 31 + (triggerAt / 60_000L).toInt()

    fun missedCode(blockId: String, occurrenceStart: Long): Int =
        blockId.hashCode() * 131 + (occurrenceStart / 60_000L).toInt() + 7

    // ---------------------------------------------------------------- PendingIntent

    private fun showAlarmIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 9001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun blockAlarmIntent(
        context: Context,
        code: Int,
        blockId: String,
        triggerAt: Long,
        kind: String,
    ): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(EXTRA_BLOCK_IDS, blockId)
            putExtra(EXTRA_TRIGGER_AT, triggerAt)
            putExtra(EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun timerIntent(context: Context): PendingIntent {
        val i = Intent(context, TimerEndReceiver::class.java).apply {
            action = TimerEndReceiver.ACTION_DONE
        }
        return PendingIntent.getBroadcast(
            context, TIMER_CODE, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun missedIntent(
        context: Context,
        code: Int,
        blockId: String,
        occurrenceStart: Long,
    ): PendingIntent {
        val i = Intent(context, MissedReceiver::class.java).apply {
            action = MissedReceiver.ACTION_CHECK
            putExtra(EXTRA_BLOCK_IDS, blockId)
            putExtra(EXTRA_TRIGGER_AT, occurrenceStart)
        }
        return PendingIntent.getBroadcast(
            context, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dailyIntent(context: Context): PendingIntent {
        val i = Intent(context, DailyReceiver::class.java).apply { action = ACTION_DAILY }
        return PendingIntent.getBroadcast(
            context, DAILY_CODE, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------------------------------------------------------------- 登记表

    private fun loadCodes(context: Context): Set<Int> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_CODES, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    private fun saveCodes(context: Context, codes: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    private fun clearCodes(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CODES).apply()
    }

    // ---------------------------------------------------------------- 超时补账

    /**
     * 补扫：到点 5 分钟还没点开始 / 完成的场次，补一条「超时」。
     * 修的是「进程被杀 / 关机期间闹钟没响」这个漏账。
     *
     * 只看当天的场次（跨天块额外看昨天开场、今天还没结束的那一场），
     * 不往回扫两天：那会给昨天补出凭空捏造的「超时」，历史里全是噪音。
     */
    fun sweepMissed(data: AppData, now: Long = System.currentTimeMillis()): SweepResult {
        val out = mutableListOf<Session>()

        val aliveIds = data.blocks.filter { it.enabled }.map { it.id }.toSet()
        // 停用 / 删除的块，进行中标记一并不留
        val starts = data.starts.filter { it.blockId in aliveIds }

        for (block in data.blocks) {
            if (!block.enabled) continue

            val candidates = mutableListOf<Long>()
            occurrenceStart(now, block, 0)?.let { candidates += it }
            if (block.crossDay) {
                // 昨天 22:30 开场、今天 06:30 结束的那一场
                val prev = occurrenceStart(now, block, -1)
                if (prev != null && now < prev + block.durationMin * 60_000L) candidates += prev
            }

            for (startMs in candidates) {
                if (!Missed.isDue(data, block, startMs, now)) continue
                out += BlockFlow.missedSession(block, startMs)
            }
        }
        return SweepResult(out, starts)
    }
}

/** 补账结果：要新增的「超时」记录，以及清理后的进行中标记 */
data class SweepResult(
    val sessions: List<Session>,
    val starts: List<BlockStart>,
)

/** 一条待排的闹钟：什么时候、提醒哪一块、是开始还是结束 */
data class PlannedAlarm(
    val triggerAt: Long,
    val kind: String,
    val blockId: String,
)
