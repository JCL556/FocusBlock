package io.github.xiaoxia.focusblock.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.xiaoxia.focusblock.MainActivity
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.Store
import io.github.xiaoxia.focusblock.data.Settings
import io.github.xiaoxia.focusblock.data.ThemeMode
import io.github.xiaoxia.focusblock.data.TimerPhase
import io.github.xiaoxia.focusblock.data.TimerState
import io.github.xiaoxia.focusblock.domain.BlockFlow
import io.github.xiaoxia.focusblock.domain.Maintenance
import io.github.xiaoxia.focusblock.domain.RestTips
import io.github.xiaoxia.focusblock.domain.Scheduler
import io.github.xiaoxia.focusblock.notify.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class Tab { TODAY, FOCUS, HISTORY, SETTINGS }

sealed interface Route {
    data class Main(val tab: Tab) : Route
    data object Timer : Route
    data object Permissions : Route
    data object Guide : Route
    data class Editor(val blockId: String?) : Route
}

/** 结束弹窗的内容。tip 不为空 = 作息块做完了，只提醒去休息，不给「开始休息」 */
data class Celebration(
    val title: String,
    val blockId: String?,
    val roundIndex: Int,
    val tip: String? = null,
)

class AppVm(private val app: Application) : AndroidViewModel(app) {

    private val store = Store.of(app)

    /** 内存里这一份，供各处直接取当前值；初值是空的 AppData */
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    /**
     * 同一份数据，多包一层「读出来了没有」：null = DataStore 还没读完。
     *
     * 界面只认这一个，别把「数据」和「loaded 标记」分成两个流——
     * 两个流先后到达会在中间留一帧「标记已到、数据还是空的」，
     * 那正是冷启动时闪一下空白作息的原因。
     */
    private val _loadedData = MutableStateFlow<AppData?>(null)
    val loadedData: StateFlow<AppData?> = _loadedData.asStateFlow()

    var route by mutableStateOf<Route>(Route.Main(Tab.TODAY))
        private set

    var celebration by mutableStateOf<Celebration?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private val stack = ArrayDeque<Route>()
    private var finishing = false

    init {
        // 先把存档接上：读完一帧就置 loaded，界面这才开始画内容
        viewModelScope.launch {
            store.data.collect { d ->
                // 第一次读出存档时把主题缓存下来，下次冷启动开屏就能直接用对
                if (_loadedData.value == null) cacheTheme(d.settings.themeMode)
                _data.value = d
                _loadedData.value = d
            }
        }
        viewModelScope.launch {
            // 开机之后第一次进 App：补账 + 重排闹钟 + 处理已超时的计时
            Maintenance.run(app)
            finalizeExpiredIfNeeded(announce = true)
            clearLegacyBlockTimer()
        }
    }

    /** 主题另存一份到 SharedPreferences：冷启动读 DataStore 要等，这一份开屏就能用 */
    private fun cacheTheme(mode: ThemeMode) {
        runCatching {
            app.getSharedPreferences(MainActivity.PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putString(MainActivity.KEY_THEME, mode.name).apply()
        }
    }

    // ------------------------------------------------------------ 导航

    fun navTo(target: Route) {
        stack.addLast(route)
        route = target
    }

    fun selectTab(tab: Tab) {
        stack.clear()
        route = Route.Main(tab)
    }

    fun back(): Boolean {
        val prev = stack.removeLastOrNull() ?: return false
        if (route is Route.Timer) dropGhostTimer()
        route = prev
        return true
    }

    fun backToMain(tab: Tab = Tab.TODAY) {
        if (route is Route.Timer) dropGhostTimer()
        stack.clear()
        route = Route.Main(tab)
    }

    fun consumeMessage() {
        message = null
    }

    fun dismissCelebration() {
        celebration = null
    }

    // ------------------------------------------------------------ 时间块

    fun saveBlock(block: Block) {
        // 兜底：编辑页已经拦了，这里再挡一次，免得老数据或别处调进来时漏过
        if (!block.point) {
            if (block.endMin < block.startMin) {
                message = "结束时间要晚于开始时间，不支持跨天"
                return
            }
            if (block.endMin - block.startMin < Block.MIN_SPAN_MIN) {
                message = "时间段至少 ${Block.MIN_SPAN_MIN} 分钟"
                return
            }
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            store.update { cur ->
                val old = cur.blocks.find { it.id == block.id }
                // 新块补上创建时间：用来区分「建的时候这场已经过了」和「该做没做」
                val saved = if (old == null && block.createdAt <= 0L) block.copy(createdAt = now) else block
                val blocks = if (old != null) {
                    cur.blocks.map { if (it.id == block.id) saved else it }
                } else {
                    cur.blocks + saved
                }
                // 改了时间以后，今天已有的记录是按旧时间打的，得清掉，否则卡片一直是灰的
                val next = cur.copy(blocks = blocks)
                if (BlockFlow.timingChanged(old, saved)) {
                    BlockFlow.clearTodayRecords(next, saved, now)
                } else {
                    next
                }
            }
            reschedule()
            back()
        }
    }

    fun deleteBlock(id: String) {
        viewModelScope.launch {
            store.update { cur -> cur.copy(blocks = cur.blocks.filterNot { it.id == id }) }
            reschedule()
            back()
        }
    }

    fun setBlockEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.update { cur ->
                cur.copy(blocks = cur.blocks.map { if (it.id == id) it.copy(enabled = enabled) else it })
            }
            reschedule()
        }
    }

    fun reschedule() {
        viewModelScope.launch {
            Scheduler.rescheduleAll(app, store.current())
        }
    }

    // ------------------------------------------------------------ 时间块：开始 / 结束

    /** 这一块现在是不是「进行中」 */
    fun isBlockRunning(block: Block, now: Long = System.currentTimeMillis()): Boolean =
        BlockFlow.activeStart(data.value, block, now) != null

    /** 点「开始」：只记一个进行中标记，作息不跑倒计时 */
    fun startBlock(block: Block) {
        val now = System.currentTimeMillis()
        if (!BlockFlow.startReady(block, now)) {
            message = "还没到 ${block.startLabel}，到点再开始"
            return
        }
        viewModelScope.launch {
            store.update { cur -> BlockFlow.markStarted(cur, block, now) }
            cancelBlockAlarms(block, now)
            message = "开始「${block.title}」，到结束时间会提醒你"
        }
    }

    /** 点「结束」：落一条 DONE，然后弹一句「去休息」 */
    fun endBlock(block: Block) {
        val now = System.currentTimeMillis()
        val tipIndex = data.value.settings.restTipIndex
        viewModelScope.launch {
            store.update { cur ->
                val finished = BlockFlow.markFinished(cur, block, now)
                finished.copy(settings = finished.settings.copy(restTipIndex = RestTips.next(tipIndex)))
            }
            cancelBlockAlarms(block, now)
            celebration = Celebration(block.title, block.id, 0, RestTips.of(tipIndex))
        }
    }

    /** 时间点块的「打卡」：一步到底，落一条 DONE。没到设定时刻不让提前点 */
    fun markPointDone(block: Block) {
        val now = System.currentTimeMillis()
        if (!BlockFlow.pointReady(block, now)) {
            message = "还没到 ${block.startLabel}，到点再打卡"
            return
        }
        viewModelScope.launch {
            store.update { cur -> BlockFlow.markFinished(cur, block, now) }
            cancelBlockAlarms(block, now)
            message = "已记录「${block.title}」"
        }
    }

    /** 这一场的开始 / 结束提醒都不用再响了 */
    private fun cancelBlockAlarms(block: Block, now: Long) {
        Notifier.cancel(app, block.id)
    }

    /**
     * 老版本用计时器跑作息，升级之后这类计时没有意义了，清掉。
     * 有进度的补一条「跳过」，别让历史凭空少一块。
     */
    private suspend fun clearLegacyBlockTimer() {
        val t = store.current().timer ?: return
        if (t.blockId == null) return
        val now = System.currentTimeMillis()
        val record = t.phase == TimerPhase.FOCUS && t.consumedMs >= 60_000L
        store.update { cur ->
            val ct = cur.timer ?: return@update cur
            if (ct.blockId == null) return@update cur
            cur.copy(
                timer = null,
                sessions = if (record) {
                    cur.sessions + Session(
                        id = UUID.randomUUID().toString(),
                        blockId = ct.blockId,
                        titleSnapshot = ct.title,
                        startTs = ct.startTs,
                        endTs = now.coerceAtLeast(ct.startTs),
                        state = SessionState.SKIPPED,
                        roundIndex = ct.roundIndex,
                    )
                } else {
                    cur.sessions
                },
            )
        }
    }

    /**
     * 打开计时页、一次没点开始就退出来，会在存档里留一个空计时。
     * 它没消耗过任何时间，留着只会把自由专注锁死，直接丢掉。
     */
    private fun dropGhostTimer() {
        if (data.value.timer?.notStarted != true) return
        viewModelScope.launch {
            store.update { cur -> if (cur.timer?.notStarted == true) cur.copy(timer = null) else cur }
        }
    }

    // ------------------------------------------------------------ 设置

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            store.update { cur -> cur.copy(settings = transform(cur.settings)) }
            val latest = store.current().settings
            cacheTheme(latest.themeMode)
            Notifier.ensureChannel(app, latest)
        }
    }

    // ------------------------------------------------------------ 自由专注（番茄）



    fun startFreeFocus() {
        val now = System.currentTimeMillis()
        val round = nextFreeRound(data.value, now)
        val planned = data.value.settings.focusLen.coerceAtLeast(1) * 60_000L
        viewModelScope.launch {
            store.update { cur ->
                cur.copy(
                    timer = TimerState(
                        blockId = null,
                        title = "自由专注",
                        phase = TimerPhase.FOCUS,
                        plannedMs = planned,
                        consumedMs = 0L,
                        runningSince = null,
                        roundIndex = round,
                        blockEndAt = null,
                        startTs = now,
                    )
                )
            }
            // 还没点开始，先别排到点闹钟
            Scheduler.cancelTimerEnd(app)
            navTo(Route.Timer)
        }
    }

    fun openActiveTimer() {
        if (data.value.timer != null) {
            stack.clear()
            route = Route.Timer
        }
    }

    /** 每秒由 UI 驱动一次。界面开着时到点就正常弹「这一个番茄完成」 */
    fun onTick() {
        finalizeExpiredIfNeeded(announce = true)
    }

    private fun finalizeExpiredIfNeeded(announce: Boolean) {
        val t = data.value.timer ?: return
        if (!t.isRunning) return
        val now = System.currentTimeMillis()
        if (t.remainingMs(now) > 0L) return
        finishPhase(now, silent = !announce)
    }

    fun toggleRun() {
        val t = data.value.timer ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            store.update { cur ->
                val ct = cur.timer ?: return@update cur
                if (ct.isRunning) {
                    cur.copy(timer = ct.copy(consumedMs = ct.elapsedMs(now), runningSince = null))
                } else {
                    cur.copy(timer = ct.copy(runningSince = now))
                }
            }
            syncTimerAlarm()
        }
    }

    fun resetTimer() {
        viewModelScope.launch {
            store.update { cur ->
                val ct = cur.timer ?: return@update cur
                cur.copy(timer = ct.copy(consumedMs = 0L, runningSince = null))
            }
            Scheduler.cancelTimerEnd(app)
        }
    }

    /**
     * 计时跑着就排一条到点闹钟，暂停 / 重置就撤掉。
     * 这样切到后台、锁屏、甚至把 App 划掉，番茄钟到点照样响。
     */
    private suspend fun syncTimerAlarm() {
        val t = store.current().timer
        val now = System.currentTimeMillis()
        val remain = t?.let { if (it.isRunning) it.remainingMs(now) else 0L } ?: 0L
        if (remain > 0L) {
            Scheduler.scheduleTimerEnd(app, now + remain)
        } else {
            Scheduler.cancelTimerEnd(app)
        }
    }

    /** 自然结束或被判定超时 */
    fun finishPhase(now: Long, silent: Boolean) {
        if (finishing) return
        val t = data.value.timer ?: return
        finishing = true
        viewModelScope.launch {
            // 已经在 App 里收尾了，后台那条到点闹钟就别再补一枪
            Scheduler.cancelTimerEnd(app)
            try {
                if (t.phase == TimerPhase.FOCUS) {
                    store.update { cur ->
                        val ct = cur.timer ?: return@update cur
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
                            )
                        )
                    }
                    if (silent) {
                        backToMain()
                        message = "上一轮专注已经结束"
                    } else {
                        celebration = Celebration(t.title, t.blockId, t.roundIndex)
                    }
                } else {
                    store.update { cur -> cur.copy(timer = null) }
                    backToMain()
                    message = "休息结束"
                }
            } finally {
                finishing = false
            }
        }
    }

    /** 结束弹窗点「开始休息」 */
    fun startBreakFromCelebration() {
        val c = celebration ?: return
        celebration = null
        val settings = data.value.settings
        val isLong = c.blockId == null && c.roundIndex >= 4
        val planned = (if (isLong) settings.longBreakLen else settings.shortBreakLen).coerceAtLeast(1) * 60_000L
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            store.update { cur ->
                cur.copy(
                    timer = TimerState(
                        blockId = c.blockId,
                        title = if (isLong) "长休息" else "休息",
                        phase = if (isLong) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK,
                        plannedMs = planned,
                        consumedMs = 0L,
                        runningSince = now,
                        roundIndex = c.roundIndex,
                        blockEndAt = null,
                        startTs = now,
                    )
                )
            }
            // 休息是直接就开跑的，顺手把到点闹钟排上
            syncTimerAlarm()
        }
    }

    fun finishCelebration() {
        celebration = null
        backToMain()
    }

    // ------------------------------------------------------------ 计算

    private fun nextFreeRound(d: AppData, now: Long): Int {
        val done = d.sessions.count {
            it.blockId == null && it.state == SessionState.DONE && sameDay(it.startTs, now)
        }
        return done % 4 + 1
    }

    /** 自由专注页要显示的「第 N / 4 个番茄」 */
    fun currentFreeRound(now: Long = System.currentTimeMillis()): Int = nextFreeRound(data.value, now)

    fun todayFreeDoneCount(now: Long = System.currentTimeMillis()): Int =
        data.value.sessions.count {
            it.blockId == null && it.state == SessionState.DONE && sameDay(it.startTs, now)
        }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
