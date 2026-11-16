package io.github.xiaoxia.focusblock.data

import kotlinx.serialization.Serializable

/** 一周七天，索引 0 = 周一 … 6 = 周日 */
@Serializable
data class Block(
    val id: String,
    val title: String,
    /** 0..1439，当天 0 点起的分钟数 */
    val startMin: Int,
    val endMin: Int,
    val days: List<Boolean> = List(7) { true },
    val fullscreen: Boolean = false,
    val enabled: Boolean = true,
    val iconKey: String = "tomato",
    /**
     * true = 时间点（只在某个时刻提醒一次，不倒计时），比如 8:00 起床。
     * 这类块 endMin 与 startMin 相同，durationMin 恒为 0。
     */
    val point: Boolean = false,
    /**
     * 这块是什么时候建出来的（毫秒）。0 = 老版本数据，没有这个信息。
     * 用来区分「这一场本来就过去了才建的」和「该做没做」：
     * 前一种只挂「已结束」，不算超时。
     */
    val createdAt: Long = 0L,
) {
    /** 结束时间早于开始时间即视为跨天（22:30 → 06:30） */
    val crossDay: Boolean get() = !point && endMin < startMin

    val durationMin: Int
        get() = when {
            point -> 0
            crossDay -> endMin + MINUTES_PER_DAY - startMin
            else -> (endMin - startMin).coerceAtLeast(0)
        }

    val timeLabel: String
        get() = if (point) {
            "%02d:%02d".format(startMin / 60, startMin % 60)
        } else {
            "%02d:%02d-%02d:%02d".format(startMin / 60, startMin % 60, endMin / 60, endMin % 60)
        }

    /** 只有开始时刻，提示语里用（「还没到 13:30」） */
    val startLabel: String
        get() = "%02d:%02d".format(startMin / 60, startMin % 60)

    /** 卡片副标题里的时长文案 */
    val spanLabel: String
        get() = if (point) "到点提醒" else "共 $durationMin 分钟"

    /** 「每天」「周一 周三 周五」 */
    val repeatLabel: String
        get() {
            if (days.all { it }) return "每天"
            if (days.none { it }) return "不重复"
            return WEEK_LABELS.filterIndexed { i, _ -> days.getOrElse(i) { false } }.joinToString(" ")
        }

    companion object {
        const val MINUTES_PER_DAY = 1440
        val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val ICON_KEYS = listOf("tomato", "apple", "pear", "lemon", "grape")

        /**
         * 时间段最短时长，跟「超时」宽限是同一个数（见 `Missed.GRACE_MS`）。
         *
         * 这样两件事永远不会打架：一段至少 5 分钟，所以「到点 5 分钟没动作 → 挂超时」
         * 落下来的那一刻，这一段的结束时刻也已经到了，不会出现「先已结束、过一会儿又补超时」
         * 的两段式。
         */
        const val MIN_SPAN_MIN = 5

        /**
         * 时间段的两端必须落在同一天里、且不少于 [MIN_SPAN_MIN] 分钟。
         * 不支持跨天（22:30 → 06:30），要跨午夜请拆成两块。
         */
        fun spanOk(startMin: Int, endMin: Int): Boolean =
            endMin - startMin >= MIN_SPAN_MIN
    }
}

@Serializable
enum class SessionState {
    /** 计时自然结束或点「完成」 */
    DONE,

    /** 计时中或提醒弹窗上点「跳过」 */
    SKIPPED,

    /** 提醒弹窗点「延后 5 分钟」 */
    POSTPONED,

    /** 到点提醒后 30 分钟无任何操作 */
    MISSED,
}

@Serializable
data class Session(
    val id: String,
    /** null = 自由专注 */
    val blockId: String? = null,
    /** 开始时的任务名快照，块改名/删除后历史仍显示原名 */
    val titleSnapshot: String,
    val startTs: Long,
    val endTs: Long,
    val state: SessionState,
    /** 自由专注 1..4；块驱动固定 0 */
    val roundIndex: Int = 0,
) {
    val durationMs: Long get() = (endTs - startTs).coerceAtLeast(0)

    val stateLabel: String
        get() = when (state) {
            SessionState.DONE -> "完成"
            SessionState.SKIPPED -> "跳过"
            SessionState.POSTPONED -> "延后"
            SessionState.MISSED -> "超时"
        }
}

/**
 * 「这一场已经点了开始、还没点结束」的进行中标记。
 * 作息块不走倒计时，只靠这个标记撑住「进行中」状态，
 * 点「结束」时才落一条 DONE 进 sessions。
 */
@Serializable
data class BlockStart(
    val blockId: String,
    val startTs: Long,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val focusLen: Int = 25,
    val shortBreakLen: Int = 5,
    val longBreakLen: Int = 15,
    val vibrate: Boolean = true,
    /** true = 遵守勿扰模式（渠道不申请豁免） */
    val respectDnd: Boolean = false,
    val ringtoneUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastCheckTs: Long = 0L,
    /** 厂商 ROM 权限只能手动确认，记录已确认的 key */
    val manualConfirmed: List<String> = emptyList(),
    /** 休息提示词轮到第几句，9 句循环 */
    val restTipIndex: Int = 0,
)

@Serializable
enum class TimerPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

/**
 * 计时器状态。全程只存「计划时长 + 已消耗 + 开始运行时刻」，
 * 剩余时间靠 now 现算，进程被杀也不影响准确度。
 */
@Serializable
data class TimerState(
    /** null = 自由专注 */
    val blockId: String? = null,
    val title: String = "",
    val phase: TimerPhase = TimerPhase.FOCUS,
    val plannedMs: Long = 0L,
    val consumedMs: Long = 0L,
    /** null = 暂停中或尚未开始 */
    val runningSince: Long? = null,
    /** 自由专注 1..4；块驱动 0 */
    val roundIndex: Int = 0,
    /** 块驱动模式的固定终点（块结束时刻） */
    val blockEndAt: Long? = null,
    /** 本轮计时开始的时间戳，用于落 Session */
    val startTs: Long = 0L,
) {
    fun elapsedMs(now: Long): Long =
        (consumedMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0L))

    fun remainingMs(now: Long): Long = (plannedMs - elapsedMs(now)).coerceAtLeast(0L)

    val isRunning: Boolean get() = runningSince != null

    /** consumedMs == 0 且没在跑 = 还没点开始 */
    val notStarted: Boolean get() = runningSince == null && consumedMs <= 0L

    fun progress(now: Long): Float =
        if (plannedMs <= 0L) 0f else (elapsedMs(now).toFloat() / plannedMs).coerceIn(0f, 1f)
}

/**
 * 时间块的展示顺序：按开始时间升序。
 * 同一时刻的「时间点」和「时间段」并列时，时间点排前面（那一刻就发生）；
 * 最后用 id 兜底，保证列表顺序稳定，不会因为排序抖动而跳位。
 */
val BlockTimeOrder: Comparator<Block> = compareBy<Block> { it.startMin }
    .thenByDescending { it.point }
    .thenBy { it.id }

/** 按时间先后排好序的副本 */
fun List<Block>.sortedByTime(): List<Block> =
    if (size < 2) this else sortedWith(BlockTimeOrder)

@Serializable
data class AppData(
    val blocks: List<Block> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val settings: Settings = Settings(),
    val timer: TimerState? = null,
    /** 已点开始、还没点结束的作息块 */
    val starts: List<BlockStart> = emptyList(),
) {
    /** 读写都走这个口子，页面拿到的 blocks 永远是时间顺序 */
    fun ordered(): AppData = copy(blocks = blocks.sortedByTime())
}

/** 主页/卡片上的状态：已结束 = 这一场的时间走完了，但还没点结束 */
enum class BlockStatus { IDLE, RUNNING, ENDED, DONE, MISSED, SKIPPED }

fun formatDurationMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

fun formatDurationHhMm(ms: Long): String {
    val totalMin = (ms / 60000L)
    return "%d 分钟".format(totalMin)
}

fun formatClock(ts: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

/** 是不是同一个自然日（本机时区） */
fun sameLocalDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}
