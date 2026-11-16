package io.github.xiaoxia.focusblock

import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.BlockStart
import io.github.xiaoxia.focusblock.data.BlockStatus
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.Settings
import io.github.xiaoxia.focusblock.data.ThemeMode
import io.github.xiaoxia.focusblock.data.TimerPhase
import io.github.xiaoxia.focusblock.data.TimerState
import io.github.xiaoxia.focusblock.data.sortedByTime
import io.github.xiaoxia.focusblock.domain.BlockFlow
import io.github.xiaoxia.focusblock.domain.Missed
import io.github.xiaoxia.focusblock.domain.RestTips
import io.github.xiaoxia.focusblock.domain.Scheduler
import io.github.xiaoxia.focusblock.ui.screens.buildMonthCells
import io.github.xiaoxia.focusblock.ui.screens.dayStat
import io.github.xiaoxia.focusblock.ui.screens.detailRows
import io.github.xiaoxia.focusblock.ui.screens.dayStart
import io.github.xiaoxia.focusblock.ui.screens.doneBlocksByDay
import io.github.xiaoxia.focusblock.ui.screens.missedBlocksByDay
import io.github.xiaoxia.focusblock.ui.screens.monthStart
import io.github.xiaoxia.focusblock.ui.screens.shiftMonth
import io.github.xiaoxia.focusblock.ui.screens.statusOf
import io.github.xiaoxia.focusblock.ui.screens.weekdayIndex
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CoreLogicTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun at(hour: Int, minute: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, dayOffset)
        }.timeInMillis

    private val everyDay = List(7) { true }

    // ---------------------------------------------------------- 存档

    @Test
    fun `AppData 存档能原样读回来`() {
        val data = AppData(
            blocks = listOf(
                Block(
                    id = "b1", title = "晚自习", startMin = 8 * 60, endMin = 10 * 60,
                    days = listOf(true, true, false, true, true, false, false),
                    fullscreen = true,
                    enabled = true, iconKey = "apple",
                ),
                Block(id = "b2", title = "夜猫子", startMin = 22 * 60 + 30, endMin = 6 * 60 + 30)
            ),
            sessions = listOf(
                Session("s1", "b1", "晚自习", at(8, 0), at(10, 0), SessionState.DONE, 0),
                Session("s2", null, "自由专注", at(11, 0), at(11, 25), SessionState.DONE, 2),
            ),
            settings = Settings(focusLen = 30, themeMode = ThemeMode.DARK, manualConfirmed = listOf("vendor_autostart")),
            timer = TimerState(blockId = null, title = "自由专注", phase = TimerPhase.FOCUS, plannedMs = 1500000L),
        )

        val text = json.encodeToString(AppData.serializer(), data)
        val back = json.decodeFromString<AppData>(text)

        assertEquals(data, back)
        assertEquals(2, back.blocks.size)
        assertEquals(ThemeMode.DARK, back.settings.themeMode)
    }

    @Test
    fun `旧存档多出未知字段不会崩`() {
        val text = """{"blocks":[],"sessions":[],"settings":{"focusLen":25,"unknownField":1},"whatever":true}"""
        val back = json.decodeFromString<AppData>(text)
        assertEquals(25, back.settings.focusLen)
    }

    // ---------------------------------------------------------- 跨天

    @Test
    fun `结束早于开始视为跨天`() {
        val night = Block(id = "n", title = "夜班", startMin = 22 * 60 + 30, endMin = 6 * 60 + 30)
        assertTrue(night.crossDay)
        assertEquals(8 * 60, night.durationMin)

        val normal = Block(id = "d", title = "白天", startMin = 8 * 60, endMin = 10 * 60)
        assertFalse(normal.crossDay)
        assertEquals(120, normal.durationMin)
    }

    @Test
    fun `跨天块在当天能算出场次开始时刻`() {
        val night = Block(id = "n", title = "夜班", startMin = 22 * 60 + 30, endMin = 6 * 60 + 30, days = everyDay)
        val start = Scheduler.occurrenceStart(at(12, 0), night, 0)
        assertNotNull(start)
        assertEquals(at(22, 30), start)
    }

    @Test
    fun `不重复的日子不排场次`() {
        val onlyMonday = Block(
            id = "m", title = "周一例会", startMin = 9 * 60, endMin = 10 * 60,
            days = listOf(true, false, false, false, false, false, false),
        )
        // 从今天往前推 7 天，总有一天的周索引不是周一
        val someDay = (1..7).first { offset ->
            Scheduler.occurrenceStart(at(12, 0), onlyMonday, offset) == null
        }
        assertEquals(null, Scheduler.occurrenceStart(at(12, 0), onlyMonday, someDay))
    }

    // ---------------------------------------------------------- 超时补账

    @Test
    fun `过期的块会自动补一条超时`() {
        val block = Block(id = "b1", title = "早读", startMin = 7 * 60, endMin = 8 * 60, days = everyDay)
        val now = at(12, 0)
        val data = AppData(blocks = listOf(block))

        val sweep = Scheduler.sweepMissed(data, now)
        assertEquals(1, sweep.sessions.size)
        assertEquals(SessionState.MISSED, sweep.sessions[0].state)
        assertEquals("早读", sweep.sessions[0].titleSnapshot)
    }

    @Test
    fun `已经有会话的场次不会重复补账`() {
        val block = Block(id = "b1", title = "早读", startMin = 7 * 60, endMin = 8 * 60, days = everyDay)
        val now = at(12, 0)
        val done = Session("s1", "b1", "早读", at(7, 0), at(8, 0), SessionState.DONE, 0)
        val data = AppData(blocks = listOf(block), sessions = listOf(done))

        assertTrue(Scheduler.sweepMissed(data, now).sessions.isEmpty())
    }

    @Test
    fun `点过开始还没到结束的块不会被判成超时`() {
        val block = Block(id = "b1", title = "晚自习", startMin = 8 * 60, endMin = 10 * 60, days = everyDay)
        val now = at(9, 0)
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart("b1", at(8, 0))))

        val sweep = Scheduler.sweepMissed(data, now)
        assertTrue(sweep.sessions.isEmpty())
        assertEquals(1, sweep.starts.size)
    }

    @Test
    fun `按时点了开始一直没点结束也不算超时`() {
        val block = Block(id = "b1", title = "晚自习", startMin = 8 * 60, endMin = 10 * 60, days = everyDay)
        // 只要宽限内点了开始，后面拖到 11:00 没点结束也只是「进行中」，不罚
        val now = at(11, 0)
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart("b1", at(8, 0))))

        val sweep = Scheduler.sweepMissed(data, now)
        assertTrue(sweep.sessions.isEmpty())
        assertEquals("进行中标记要留着", 1, sweep.starts.size)
    }

    @Test
    fun `迟到了才点开始照样算超时`() {
        val block = Block(id = "b1", title = "晚自习", startMin = 8 * 60, endMin = 10 * 60, days = everyDay)
        val now = at(8, 12)
        // 08:07 才点开始，早过了 5 分钟宽限
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart("b1", at(8, 7))))

        val sweep = Scheduler.sweepMissed(data, now)
        assertEquals(1, sweep.sessions.size)
        assertEquals(SessionState.MISSED, sweep.sessions[0].state)
    }

    @Test
    fun `到点还不到五分钟不补账`() {
        val block = Block(id = "b1", title = "晚自习", startMin = 8 * 60, endMin = 10 * 60, days = everyDay)
        val idle = AppData(blocks = listOf(block))
        // 08:00:30、08:04:59，宽限都还没走完
        assertTrue(Scheduler.sweepMissed(idle, at(8, 0) + 30_000L).sessions.isEmpty())
        assertTrue(Scheduler.sweepMissed(idle, at(8, 0) + 5 * 60_000L - 1_000L).sessions.isEmpty())
        assertEquals("满 5 分钟才补", 1, Scheduler.sweepMissed(idle, at(8, 0) + 5 * 60_000L).sessions.size)
    }

    @Test
    fun `到点五分钟后没点开始就算超时`() {
        val block = Block(id = "b1", title = "晚自习", startMin = 8 * 60, endMin = 10 * 60, days = everyDay)
        // 09:00 还在块里，但一直没点开始 —— 新规则下这就是超时，只是卡片不变灰
        val sweep = Scheduler.sweepMissed(AppData(blocks = listOf(block)), at(9, 0))
        assertEquals(1, sweep.sessions.size)
        assertEquals(SessionState.MISSED, sweep.sessions[0].state)
    }

    // ---------------------------------------------------------- 计时

    @Test
    fun `剩余时间按运行时刻现算`() {
        val start = at(8, 0)
        val timer = TimerState(
            blockId = "b1", title = "晚自习", phase = TimerPhase.FOCUS,
            plannedMs = 120 * 60_000L, consumedMs = 0L, runningSince = start,
        )
        assertEquals(120 * 60_000L, timer.remainingMs(start))
        assertEquals(80 * 60_000L, timer.remainingMs(start + 40 * 60_000L))
        assertEquals(0L, timer.remainingMs(start + 200 * 60_000L))
    }

    @Test
    fun `暂停后剩余时间不再减少`() {
        val start = at(8, 0)
        val paused = TimerState(
            blockId = null, title = "自由专注", phase = TimerPhase.FOCUS,
            plannedMs = 25 * 60_000L, consumedMs = 5 * 60_000L, runningSince = null,
        )
        assertEquals(20 * 60_000L, paused.remainingMs(start))
        assertEquals(20 * 60_000L, paused.remainingMs(start + 3600_000L))
    }

    // ---------------------------------------------------------- 日历

    private fun fixed(year: Int, month0: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month0, day, 0, 0, 0)
        }.timeInMillis

    @Test
    fun `日历格子从周一起排且天数正确`() {
        val nov1 = fixed(2026, 10, 1) // 2026-11-01 是周日
        assertEquals(6, weekdayIndex(nov1))

        val cells = buildMonthCells(monthStart(nov1))
        assertEquals("第一格应落在周日那列", 6, cells.indexOfFirst { it != null })
        assertEquals(nov1, cells[6])
        assertEquals("11 月 30 天", 30, cells.count { it != null })
        assertEquals("必须整周", 0, cells.size % 7)

        // 2026-02 只有 28 天，且 2/1 是周日
        val feb = buildMonthCells(monthStart(fixed(2026, 1, 1)))
        assertEquals(28, feb.count { it != null })
        assertEquals(6, feb.indexOfFirst { it != null })
    }

    @Test
    fun `切月份不会串到别的月`() {
        val nov = monthStart(fixed(2026, 10, 14))
        val oct = shiftMonth(nov, -1)
        val dec = shiftMonth(nov, 1)

        assertEquals(10, Calendar.getInstance().apply { timeInMillis = oct }.get(Calendar.MONTH) + 1)
        assertEquals(12, Calendar.getInstance().apply { timeInMillis = dec }.get(Calendar.MONTH) + 1)
        assertEquals(31, buildMonthCells(oct).count { it != null })
        assertEquals(31, buildMonthCells(dec).count { it != null })
    }

    @Test
    fun `全部计划完成那天才打勾`() {
        val day = fixed(2026, 10, 14)
        val idx = weekdayIndex(day)
        val blocks = listOf(
            Block(id = "b1", title = "早读", startMin = 7 * 60, endMin = 8 * 60, days = List(7) { it == idx }),
            Block(id = "b2", title = "晚自习", startMin = 20 * 60, endMin = 22 * 60, days = List(7) { it == idx }),
        )

        val oneDone = listOf(
            Session("s1", "b1", "早读", day + 7 * 3600_000L, day + 8 * 3600_000L, SessionState.DONE)
        )
        val st1 = dayStat(day, blocks, doneBlocksByDay(oneDone))
        assertEquals(2, st1.planned)
        assertEquals(1, st1.done)
        assertFalse("只完成一半不该打勾", st1.allDone)

        val both = oneDone + Session(
            "s2", "b2", "晚自习", day + 20 * 3600_000L, day + 22 * 3600_000L, SessionState.DONE
        )
        assertTrue(dayStat(day, blocks, doneBlocksByDay(both)).allDone)
    }

    @Test
    fun `跳过和超时都算不上完成`() {
        val day = fixed(2026, 10, 14)
        val idx = weekdayIndex(day)
        val block = Block(id = "b1", title = "早读", startMin = 7 * 60, endMin = 8 * 60, days = List(7) { it == idx })
        val skipped = listOf(
            Session("s1", "b1", "早读", day + 7 * 3600_000L, day + 7 * 3600_000L, SessionState.SKIPPED)
        )
        val st = dayStat(day, listOf(block), doneBlocksByDay(skipped))
        assertEquals(0, st.done)
        assertFalse(st.allDone)
    }

    @Test
    fun `当天没安排的日子不打勾`() {
        val day = fixed(2026, 10, 14)
        val st = dayStat(day, emptyList(), emptyMap())
        assertEquals(0, st.planned)
        assertFalse("没安排就没有完成一说", st.allDone)
    }

    @Test
    fun `别的日子的完成记录不会算到今天`() {
        val day = fixed(2026, 10, 14)
        val idx = weekdayIndex(day)
        val block = Block(id = "b1", title = "早读", startMin = 7 * 60, endMin = 8 * 60, days = List(7) { it == idx })
        // 前一天完成过，但今天没有
        val otherDay = day - 86_400_000L
        val sessions = listOf(
            Session("s1", "b1", "早读", otherDay + 7 * 3600_000L, otherDay + 8 * 3600_000L, SessionState.DONE)
        )
        assertFalse(dayStat(day, listOf(block), doneBlocksByDay(sessions)).allDone)
    }

    @Test
    fun `时间块按开始时间先后排序而不是按创建顺序`() {
        // 先建 20:00，后建 7:00，再建 12:30 —— 顺序应当被时间掰回来
        val late = Block(id = "c", title = "晚自习", startMin = 20 * 60, endMin = 22 * 60)
        val early = Block(id = "a", title = "早读", startMin = 7 * 60, endMin = 8 * 60)
        val noon = Block(id = "b", title = "午休", startMin = 12 * 60 + 30, endMin = 13 * 60)

        val ordered = AppData(blocks = listOf(late, early, noon)).ordered().blocks
        assertEquals(listOf("早读", "午休", "晚自习"), ordered.map { it.title })
    }

    @Test
    fun `同一时刻的时间点排在时间段前面`() {
        val range = Block(id = "r", title = "晨跑", startMin = 8 * 60, endMin = 9 * 60)
        val point = Block(id = "p", title = "起床", startMin = 8 * 60, endMin = 8 * 60, point = true)

        val ordered = listOf(range, point).sortedByTime()
        assertEquals("起床", ordered.first().title)
        assertEquals("晨跑", ordered.last().title)
    }

    @Test
    fun `跨天块按开始时间排在当天最后`() {
        val night = Block(id = "n", title = "睡觉", startMin = 22 * 60 + 30, endMin = 6 * 60 + 30)
        val evening = Block(id = "e", title = "复盘", startMin = 21 * 60, endMin = 22 * 60)
        val morning = Block(id = "m", title = "早读", startMin = 7 * 60, endMin = 8 * 60)

        val ordered = listOf(night, evening, morning).sortedByTime()
        assertEquals(listOf("早读", "复盘", "睡觉"), ordered.map { it.title })
    }

    @Test
    fun `排序不改变块的数量和内容`() {
        val a = Block(id = "a", title = "A", startMin = 9 * 60, endMin = 10 * 60)
        val b = Block(id = "b", title = "B", startMin = 8 * 60, endMin = 9 * 60)
        val source = listOf(a, b)

        val ordered = source.sortedByTime()
        assertEquals(2, ordered.size)
        assertEquals(source.toSet(), ordered.toSet())
    }

    // ---------------------------------------------------------- 开始 / 结束

    private fun studyBlock(startH: Int = 8, endH: Int = 10): Block =
        Block(id = "b1", title = "学习软件", startMin = startH * 60, endMin = endH * 60, days = everyDay)

    @Test
    fun `点开始只是记个进行中标记`() {
        val block = studyBlock()
        val at8 = at(8, 0)
        val after = BlockFlow.markStarted(AppData(blocks = listOf(block)), block, at8)

        assertEquals(1, after.starts.size)
        assertEquals("b1", after.starts[0].blockId)
        assertTrue("作息不跑倒计时，别生成 timer", after.timer == null)
        assertTrue("还没点结束，先不落记录", after.sessions.isEmpty())
        assertNotNull(BlockFlow.activeStart(after, block, at(8, 5)))
    }

    @Test
    fun `点结束后落一条完成并清掉标记`() {
        val block = studyBlock()
        val started = BlockFlow.markStarted(AppData(blocks = listOf(block)), block, at(8, 0))
        val finished = BlockFlow.markFinished(started, block, at(10, 0))

        assertTrue(finished.starts.isEmpty())
        assertEquals(1, finished.sessions.size)
        assertEquals(SessionState.DONE, finished.sessions[0].state)
        assertEquals("学习软件", finished.sessions[0].titleSnapshot)
        assertEquals("开始时刻要留着", at(8, 0), finished.sessions[0].startTs)
        assertEquals(at(10, 0), finished.sessions[0].endTs)
    }

    @Test
    fun `没点开始直接点结束也能收尾`() {
        val block = studyBlock()
        val finished = BlockFlow.markFinished(AppData(blocks = listOf(block)), block, at(10, 5))

        val done = finished.sessions.filter { it.state == SessionState.DONE }
        assertEquals(1, done.size)
        assertEquals("没点开始就按块的开场时间补", at(8, 0), done[0].startTs)
        assertTrue("没点开始就是迟到了，再留个超时标签", finished.sessions.any { it.state == SessionState.MISSED })
    }

    @Test
    fun `同一天重复点结束不会落两条完成`() {
        val block = studyBlock()
        val once = BlockFlow.markFinished(AppData(blocks = listOf(block)), block, at(10, 0))
        val twice = BlockFlow.markFinished(once, block, at(10, 20))
        assertEquals(1, twice.sessions.count { it.state == SessionState.DONE })
        assertEquals(1, twice.sessions.count { it.state == SessionState.MISSED })
    }

    @Test
    fun `跳过会清掉进行中标记并落一条跳过`() {
        val block = studyBlock()
        val started = BlockFlow.markStarted(AppData(blocks = listOf(block)), block, at(8, 0))
        val skipped = BlockFlow.markSkipped(started, block, at(8, 30))

        assertTrue(skipped.starts.isEmpty())
        assertEquals(SessionState.SKIPPED, skipped.sessions[0].state)
    }

    @Test
    fun `休息提示九句轮着来`() {
        assertEquals(9, RestTips.ALL.size)
        assertEquals(RestTips.ALL[0], RestTips.of(0))
        assertEquals(RestTips.ALL[8], RestTips.of(8))
        assertEquals("转一圈要回到第一句", RestTips.ALL[0], RestTips.of(9))
        assertEquals(1, RestTips.next(0))
        assertEquals(0, RestTips.next(8))
    }

    // ---------------------------------------------------------- 过点即算超时

    private fun atDate(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, 10, 14, hour, minute, 0)
        }.timeInMillis

    /** 前一天（2026-11-13）的某个时刻，跨天块用 */
    private fun atPrev(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, 10, 13, hour, minute, 0)
        }.timeInMillis

    private fun blockOnThatDay(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        point: Boolean = false,
        enabled: Boolean = true,
    ): Block {
        val idx = weekdayIndex(fixed(2026, 10, 14))
        return Block(
            id = "b-$startHour-$startMinute",
            title = "学习软件",
            startMin = startHour * 60 + startMinute,
            endMin = endHour * 60 + endMinute,
            days = List(7) { it == idx },
            enabled = enabled,
            point = point,
        )
    }

    @Test
    fun `新建的块时间已经过了就显示成已超时`() {
        // 20:33 才建一个 13:30-17:30 的块：当天不会再有它的闹钟，只能靠算
        val now = atDate(20, 33)
        val block = blockOnThatDay(13, 30, 17, 30)
        assertEquals(BlockStatus.MISSED, statusOf(block, AppData(blocks = listOf(block)), now))
    }

    @Test
    fun `还没到点的块保持待办`() {
        val now = atDate(20, 33)
        val block = blockOnThatDay(21, 30, 22, 30)
        assertEquals(BlockStatus.IDLE, statusOf(block, AppData(blocks = listOf(block)), now))
    }

    @Test
    fun `没点开始的块到点一分钟后就挂超时标签`() {
        val now = atDate(20, 33)
        val block = blockOnThatDay(18, 30, 21, 30)
        assertEquals(BlockStatus.MISSED, statusOf(block, AppData(blocks = listOf(block)), now))
    }

    @Test
    fun `时间点过了也算超时`() {
        val now = atDate(20, 33)
        val past = blockOnThatDay(12, 31, 12, 31, point = true)
        assertEquals(BlockStatus.MISSED, statusOf(past, AppData(blocks = listOf(past)), now))

        val later = blockOnThatDay(21, 0, 21, 0, point = true)
        assertEquals(BlockStatus.IDLE, statusOf(later, AppData(blocks = listOf(later)), now))
    }

    @Test
    fun `停用的块过点了也不标超时`() {
        val now = atDate(20, 33)
        val block = blockOnThatDay(13, 30, 17, 30, enabled = false)
        assertEquals(BlockStatus.IDLE, statusOf(block, AppData(blocks = listOf(block)), now))
    }

    @Test
    fun `已经打卡的块仍然是完成`() {
        val now = atDate(20, 33)
        val block = blockOnThatDay(13, 30, 17, 30)
        val done = Session(
            "s1", block.id, block.title,
            atDate(13, 30), atDate(17, 30), SessionState.DONE,
        )
        assertEquals(BlockStatus.DONE, statusOf(block, AppData(blocks = listOf(block), sessions = listOf(done)), now))
    }

    @Test
    fun `改了时间以后今天的旧记录要清掉`() {
        val now = atDate(21, 54)
        val old = blockOnThatDay(18, 30, 20, 30)
        val done = Session("s1", old.id, old.title, atDate(18, 30), atDate(20, 30), SessionState.DONE)
        val data = AppData(
            blocks = listOf(old),
            sessions = listOf(done),
            starts = listOf(BlockStart(old.id, atDate(18, 30))),
        )
        // 把这一块挪到 21:55-22:00
        val moved = old.copy(startMin = 21 * 60 + 55, endMin = 22 * 60)

        assertTrue("时间变了要能认出来", BlockFlow.timingChanged(old, moved))
        val cleaned = BlockFlow.clearTodayRecords(data, moved, now)
        assertTrue("今天的完成记录要清掉", cleaned.sessions.isEmpty())
        assertTrue("进行中标记也要清掉", cleaned.starts.isEmpty())
        assertEquals(
            "清完就该回到待办",
            BlockStatus.IDLE,
            statusOf(moved, cleaned.copy(blocks = listOf(moved)), now),
        )
    }

    @Test
    fun `只改名字不算改时间`() {
        val old = blockOnThatDay(18, 30, 20, 30)
        assertFalse(BlockFlow.timingChanged(old, old.copy(title = "换个名字")))
        assertFalse("改锁屏提醒 / 启用状态不动今天记录", BlockFlow.timingChanged(old, old.copy(fullscreen = !old.fullscreen, enabled = !old.enabled)))
        assertTrue(BlockFlow.timingChanged(null, old).not())
    }

    @Test
    fun `清记录只清今天不动以前的日子`() {
        val now = atDate(21, 54)
        val block = blockOnThatDay(18, 30, 20, 30)
        val yesterday = Session(
            "s0", block.id, block.title,
            atDate(18, 30) - 86_400_000L, atDate(20, 30) - 86_400_000L, SessionState.DONE,
        )
        val today = Session("s1", block.id, block.title, atDate(18, 30), atDate(20, 30), SessionState.DONE)
        val cleaned = BlockFlow.clearTodayRecords(
            AppData(blocks = listOf(block), sessions = listOf(yesterday, today)), block, now,
        )
        assertEquals(1, cleaned.sessions.size)
        assertEquals("s0", cleaned.sessions[0].id)
    }

    // ---------------------------------------------------------- 新规则：超时只是标签

    @Test
    fun `准点完成不会留超时标签`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val after = BlockFlow.markFinished(AppData(blocks = listOf(block)), block, atDate(8, 0) + 30_000L)
        assertEquals(1, after.sessions.size)
        assertEquals(SessionState.DONE, after.sessions[0].state)
    }

    @Test
    fun `迟到完成既落完成也留一个超时`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val after = BlockFlow.markFinished(AppData(blocks = listOf(block)), block, atDate(10, 0))
        assertEquals(2, after.sessions.size)
        assertTrue(after.sessions.any { it.state == SessionState.DONE })
        assertTrue(after.sessions.any { it.state == SessionState.MISSED })
    }

    @Test
    fun `迟到完成以后卡片照样变灰`() {
        val now = atDate(10, 30)
        val block = blockOnThatDay(8, 0, 10, 0)
        val after = BlockFlow.markFinished(AppData(blocks = listOf(block)), block, atDate(10, 0))
        assertEquals(BlockStatus.DONE, statusOf(block, after, now))
        assertTrue("超时标签要留着，日历那天不打勾", Missed.taggedToday(after, block, now))
    }

    @Test
    fun `时间点过了五分钟宽限才算超时`() {
        val block = blockOnThatDay(8, 0, 8, 0, point = true)
        val idle = AppData(blocks = listOf(block))
        // 到点那一刻还没打卡：先挂「已结束」，过了 5 分钟宽限才算超时
        assertEquals(BlockStatus.ENDED, statusOf(block, idle, atDate(8, 0) + 30_000L))
        assertEquals(BlockStatus.ENDED, statusOf(block, idle, atDate(8, 4) + 30_000L))
        assertEquals(BlockStatus.MISSED, statusOf(block, idle, atDate(8, 6)))
        val onTime = BlockFlow.markFinished(idle, block, atDate(8, 4))
        assertTrue(onTime.sessions.none { it.state == SessionState.MISSED })
    }

    @Test
    fun `停用的块不判超时`() {
        val block = blockOnThatDay(8, 0, 10, 0, enabled = false)
        assertFalse(Missed.isDue(AppData(blocks = listOf(block)), block, atDate(8, 0), atDate(9, 0)))
    }

    @Test
    fun `点过跳过的场次不再判超时`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val skipped = BlockFlow.markSkipped(AppData(blocks = listOf(block)), block, atDate(8, 0))
        assertTrue(skipped.sessions.any { it.state == SessionState.SKIPPED })
        assertFalse(Missed.isDue(skipped, block, atDate(8, 0), atDate(9, 0)))
    }

    @Test
    fun `有超时的那天日历不打勾`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val done = Session("s1", block.id, block.title, atDate(8, 0), atDate(10, 0), SessionState.DONE)
        val missed = Session("s2", block.id, block.title, atDate(8, 0), atDate(8, 0), SessionState.MISSED)
        val sessions = listOf(done, missed)
        val st = dayStat(
            dayStart(atDate(12, 0)), listOf(block),
            doneBlocksByDay(sessions), missedBlocksByDay(sessions),
        )
        assertEquals(1, st.planned)
        assertEquals(1, st.done)
        assertEquals(1, st.missed)
        assertFalse("有超时就不打勾", st.allDone)

        val clean = dayStat(
            dayStart(atDate(12, 0)), listOf(block),
            doneBlocksByDay(listOf(done)), missedBlocksByDay(listOf(done)),
        )
        assertTrue("没有超时、全做完就打勾", clean.allDone)
    }

    @Test
    fun `点了开始还没结束的块显示进行中`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart(block.id, atDate(8, 0))))
        assertEquals(BlockStatus.RUNNING, statusOf(block, data, atDate(9, 0)))
    }
    // ---------------------------------------------------------- 已结束 / 不许提前打卡

    @Test
    fun `时间段过了结束时间不再说进行中`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart(block.id, atDate(8, 0))))
        assertEquals(BlockStatus.RUNNING, statusOf(block, data, atDate(9, 59)))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(10, 1)))
        assertEquals("过了很久也还是已结束，不会变成超时", BlockStatus.ENDED, statusOf(block, data, atDate(13, 0)))
    }

    @Test
    fun `已结束的场次还能补点结束`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart(block.id, atDate(8, 0))))
        val after = BlockFlow.markFinished(data, block, atDate(13, 0))
        assertEquals(BlockStatus.DONE, statusOf(block, after, atDate(13, 30)))
    }

    @Test
    fun `时间点到了就挂已结束`() {
        val block = blockOnThatDay(8, 0, 8, 0, point = true)
        val data = AppData(blocks = listOf(block))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(8, 0)))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(8, 0) + 30_000L))
        assertEquals("五分钟宽限内还是已结束", BlockStatus.ENDED, statusOf(block, data, atDate(8, 4) + 30_000L))
        assertEquals(BlockStatus.MISSED, statusOf(block, data, atDate(8, 5) + 1_000L))
        assertEquals("没到点还是待办", BlockStatus.IDLE, statusOf(block, data, atDate(7, 30)))
    }

    @Test
    fun `时间点没到设定时间不许打卡`() {
        val point = blockOnThatDay(8, 0, 8, 0, point = true)
        assertFalse(BlockFlow.pointReady(point, atDate(7, 59)))
        assertTrue(BlockFlow.pointReady(point, atDate(8, 0)))
        assertTrue(BlockFlow.pointReady(point, atDate(8, 40)))
        // 时间段不受这条限制
        assertTrue(BlockFlow.pointReady(blockOnThatDay(8, 0, 10, 0), atDate(7, 0)))
    }

    @Test
    fun `日历明细里超时也有一条`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val missed = Session("m1", block.id, block.title, atDate(8, 0), atDate(8, 0), SessionState.MISSED)
        val rows = detailRows(listOf(missed), dayStart(atDate(12, 0)))
        assertEquals(1, rows.size)
        assertEquals(SessionState.MISSED, rows[0].session.state)
        assertFalse(rows[0].missedAlso)
    }

    @Test
    fun `迟到补做在明细里合成一行`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val missed = Session("m1", block.id, block.title, atDate(8, 0), atDate(8, 0), SessionState.MISSED)
        val done = Session("d1", block.id, block.title, atDate(10, 30), atDate(10, 30), SessionState.DONE)
        val rows = detailRows(listOf(missed, done), dayStart(atDate(12, 0)))
        assertEquals(1, rows.size)
        assertEquals(SessionState.DONE, rows[0].session.state)
        assertTrue("完成那一行要同时挂「超时」", rows[0].missedAlso)
    }

    @Test
    fun `明细按设定时间先后排`() {
        val a = blockOnThatDay(8, 0, 9, 0)
        val b = blockOnThatDay(20, 0, 21, 0)
        val first = Session("d1", a.id, a.title, atDate(8, 0), atDate(9, 0), SessionState.DONE)
        val second = Session("m1", b.id, b.title, atDate(20, 0), atDate(20, 0), SessionState.MISSED)
        val rows = detailRows(listOf(second, first), dayStart(atDate(23, 0)))
        assertEquals(2, rows.size)
        assertTrue(rows[0].session.startTs < rows[1].session.startTs)
        assertEquals(SessionState.MISSED, rows[1].session.state)
    }
    // ---------------------------------------------------------- 新建即过点的块

    @Test
    fun `新建时就已过完的块挂已结束不是超时`() {
        val block = blockOnThatDay(8, 0, 10, 0).copy(createdAt = atDate(20, 33))
        val data = AppData(blocks = listOf(block))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(20, 33)))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(22, 0)))
        assertFalse(
            "建的时候这一场早过完了，不该凭空多一个超时",
            Missed.isDue(data, block, atDate(8, 0), atDate(20, 33)),
        )
    }

    @Test
    fun `新建的已过时间点也挂已结束`() {
        val block = blockOnThatDay(8, 0, 8, 0, point = true).copy(createdAt = atDate(9, 0))
        val data = AppData(blocks = listOf(block))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(9, 0)))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(11, 0)))
        assertFalse(Missed.isDue(data, block, atDate(8, 0), atDate(9, 0)))
    }

    @Test
    fun `新建还没到时间的块照常是待办`() {
        val block = blockOnThatDay(21, 0, 22, 0).copy(createdAt = atDate(20, 0))
        val data = AppData(blocks = listOf(block))
        assertEquals(BlockStatus.IDLE, statusOf(block, data, atDate(20, 30)))
        // 建的时候这场还没开始，到点没做就该扣「超时」，不是「已结束」
        assertEquals(BlockStatus.MISSED, statusOf(block, data, atDate(22, 30)))
    }

    @Test
    fun `建的时候还在进行中的场次仍按超时算`() {
        val block = blockOnThatDay(8, 0, 10, 0).copy(createdAt = atDate(9, 0))
        val data = AppData(blocks = listOf(block))
        assertEquals(BlockStatus.MISSED, statusOf(block, data, atDate(10, 5)))
    }
    // ---------------------------------------------------------- 每天重复的时间点不能提前打卡

    @Test
    fun `每天重复的时间点凌晨不能提前打卡`() {
        // 老大真机场景：凌晨 1:34，每天 07:20 的「起床」照样能点
        val block = Block(
            id = "wake",
            title = "起床",
            startMin = 7 * 60 + 20,
            endMin = 7 * 60 + 20,
            point = true,
        )
        val dawn = Calendar.getInstance().apply { clear(); set(2026, 10, 15, 1, 34, 0) }.timeInMillis
        assertFalse("昨天 07:20 早过了，不能拿来顶今天这张卡", BlockFlow.pointReady(block, dawn))

        val atPoint = Calendar.getInstance().apply { clear(); set(2026, 10, 15, 7, 20, 0) }.timeInMillis
        assertTrue(BlockFlow.pointReady(block, atPoint))

        val after = Calendar.getInstance().apply { clear(); set(2026, 10, 15, 9, 0, 0) }.timeInMillis
        assertTrue(BlockFlow.pointReady(block, after))
    }

    @Test
    fun `每天重复的时间点凌晨也不算超时`() {
        val block = Block(
            id = "wake2",
            title = "起床",
            startMin = 7 * 60 + 20,
            endMin = 7 * 60 + 20,
            point = true,
        )
        val dawn = Calendar.getInstance().apply { clear(); set(2026, 10, 15, 1, 34, 0) }.timeInMillis
        assertEquals(BlockStatus.IDLE, statusOf(block, AppData(blocks = listOf(block)), dawn))
    }

    @Test
    fun `今天不是重复日才允许补昨天的卡`() {
        val onlySaturday = Block(
            id = "sat",
            title = "起床",
            startMin = 7 * 60 + 20,
            endMin = 7 * 60 + 20,
            days = List(7) { it == weekdayIndex(fixed(2026, 10, 14)) },
            point = true,
        )
        val sunday = Calendar.getInstance().apply { clear(); set(2026, 10, 15, 1, 34, 0) }.timeInMillis
        assertTrue("今天没这一场，补昨天那次是允许的", BlockFlow.pointReady(onlySaturday, sunday))
    }
    // ---------------------------------------------------------- 时间段开锁 / 已结束标签

    @Test
    fun `时间段没到开始时间不许点开始`() {
        val block = blockOnThatDay(13, 30, 17, 30)
        assertFalse("13:00 还没到 13:30", BlockFlow.startReady(block, atDate(13, 0)))
        assertTrue(BlockFlow.startReady(block, atDate(13, 30)))
        assertTrue("过点以后随时能补点开始", BlockFlow.startReady(block, atDate(15, 0)))
    }

    @Test
    fun `每天重复的时间段凌晨也不能点开始`() {
        val block = Block(id = "study", title = "学习软件", startMin = 13 * 60 + 30, endMin = 17 * 60 + 30)
        val dawn = Calendar.getInstance().apply { clear(); set(2026, 10, 15, 1, 34, 0) }.timeInMillis
        assertFalse("昨天 13:30 早过了，不能顶今天", BlockFlow.startReady(block, dawn))
    }

    @Test
    fun `时间点与时间段各管各的锁`() {
        val point = blockOnThatDay(8, 0, 8, 0, point = true)
        val span = blockOnThatDay(8, 0, 10, 0)
        assertTrue("时间段不受打卡锁影响", BlockFlow.pointReady(span, atDate(1, 0)))
        assertTrue("时间点不受开始锁影响", BlockFlow.startReady(point, atDate(1, 0)))
    }

    @Test
    fun `过了结束时刻的时间段挂已结束`() {
        val block = blockOnThatDay(8, 0, 12, 30)
        assertFalse(Missed.windowClosed(block, atDate(10, 0)))
        assertTrue(Missed.windowClosed(block, atDate(12, 30)))
        assertTrue("做完的也照样挂", Missed.windowClosed(block, atDate(15, 0)))
    }

    @Test
    fun `时间点到了才挂已结束`() {
        val block = blockOnThatDay(21, 0, 21, 0, point = true)
        assertFalse(Missed.windowClosed(block, atDate(20, 59)))
        assertTrue(Missed.windowClosed(block, atDate(21, 0)))
    }
    // ---------------------------------------------------------- 跨天块（14:55–次日 03:30）

    @Test
    fun `跨天块今天还没开始不挂已结束也不许点`() {
        val block = Block(id = "snack", title = "吃零食", startMin = 14 * 60 + 55, endMin = 3 * 60 + 30)
        val now = atDate(14, 49)
        assertFalse("今天这场 14:55 还没到，不能因为昨晚那场过了就写已结束", Missed.windowClosed(block, now))
        assertFalse("也不能提前点开始", BlockFlow.startReady(block, now))
    }

    @Test
    fun `跨天块凌晨还能补点开始`() {
        val block = Block(id = "snack2", title = "吃零食", startMin = 14 * 60 + 55, endMin = 3 * 60 + 30)
        val early = atDate(1, 0)
        assertTrue("昨晚那场还在跑，凌晨点开始算补", BlockFlow.startReady(block, early))
    }

    @Test
    fun `跨天块两场之间是空的`() {
        val block = Block(id = "snack3", title = "吃零食", startMin = 14 * 60 + 55, endMin = 3 * 60 + 30)
        val morning = atDate(6, 0)
        assertFalse("昨晚 03:30 就结束了，今天 14:55 还没到", BlockFlow.startReady(block, morning))
        assertFalse(Missed.windowClosed(block, morning))
        assertEquals(BlockStatus.IDLE, statusOf(block, AppData(blocks = listOf(block)), morning))
    }
    @Test
    fun `不到点那一刻都不解锁`() {
        val block = blockOnThatDay(14, 30, 15, 30)
        assertFalse("14:29:59 还是锁着", BlockFlow.startReady(block, atDate(14, 29) + 59_000L))
        assertTrue("14:30:00 就解锁", BlockFlow.startReady(block, atDate(14, 30)))
        val point = blockOnThatDay(7, 20, 7, 20, point = true)
        assertFalse(BlockFlow.pointReady(point, atDate(7, 19) + 59_000L))
        assertTrue(BlockFlow.pointReady(point, atDate(7, 20)))
    }

    @Test
    fun `进行中的场次到设定结束时刻就变已结束`() {
        val block = blockOnThatDay(13, 30, 17, 30)
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart(block.id, atDate(13, 30))))
        assertEquals(BlockStatus.RUNNING, statusOf(block, data, atDate(16, 0)))
        assertFalse(Missed.windowClosed(block, atDate(16, 0)))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(17, 31)))
        assertTrue(Missed.windowClosed(block, atDate(17, 31)))
    }

    @Test
    fun `迟到开始也按设定的结束时刻挂已结束`() {
        val block = blockOnThatDay(18, 0, 19, 0)
        // 18:50 才点开始，只做了十分钟，但这一段的结束时刻还是 19:00，不顺延
        val data = AppData(blocks = listOf(block), starts = listOf(BlockStart(block.id, atDate(18, 50))))
        assertEquals(BlockStatus.RUNNING, statusOf(block, data, atDate(18, 55)))
        assertEquals(BlockStatus.ENDED, statusOf(block, data, atDate(19, 0)))
        assertTrue(Missed.windowClosed(block, atDate(19, 0)))
    }

    @Test
    fun `窗口结束后补点开始也能收尾`() {
        val block = blockOnThatDay(18, 0, 19, 0)
        val idle = AppData(blocks = listOf(block))
        assertEquals("先挂超时", BlockStatus.MISSED, statusOf(block, idle, atDate(19, 10)))

        val late = BlockFlow.markStarted(idle, block, atDate(19, 10))
        assertEquals("点过开始就不能卡在待办 / 超时", BlockStatus.ENDED, statusOf(block, late, atDate(19, 10)))

        val done = BlockFlow.markFinished(late, block, atDate(19, 12))
        assertEquals(BlockStatus.DONE, statusOf(block, done, atDate(19, 12)))
    }

    // ---------------------------------------------------------- 跨天块的收尾与归天

    @Test
    fun `跨天块凌晨点结束能变灰`() {
        val block = Block(id = "snack", title = "吃零食", startMin = 14 * 60 + 55, endMin = 3 * 60 + 30)
        val data = AppData(
            blocks = listOf(block),
            starts = listOf(BlockStart(block.id, atPrev(15, 0))),
        )
        val late = atDate(1, 0)
        assertEquals("凌晨还在窗口里，算进行中", BlockStatus.RUNNING, statusOf(block, data, late))

        val after = BlockFlow.markFinished(data, block, late)
        assertEquals("点完就该变灰，不能又退回待办", BlockStatus.DONE, statusOf(block, after, late))
        assertEquals(1, after.sessions.count { it.state == SessionState.DONE })

        val again = BlockFlow.markFinished(after, block, late)
        assertEquals("重复点不会再多一条", 1, again.sessions.count { it.state == SessionState.DONE })
    }

    @Test
    fun `跨天块的完成记录归到昨晚那场`() {
        val block = Block(id = "snack2", title = "吃零食", startMin = 14 * 60 + 55, endMin = 3 * 60 + 30)
        val data = AppData(
            blocks = listOf(block),
            starts = listOf(BlockStart(block.id, atPrev(15, 0))),
        )
        val after = BlockFlow.markFinished(data, block, atDate(1, 0))
        val done = after.sessions.first { it.state == SessionState.DONE }
        assertEquals("开始时间就是昨晚点开始的那一刻", atPrev(15, 0), done.startTs)
    }

    @Test
    fun `昨天的完成不算今天完成`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val yesterday = Session("d0", block.id, block.title, atPrev(8, 0), atPrev(10, 0), SessionState.DONE)
        val data = AppData(blocks = listOf(block), sessions = listOf(yesterday))
        assertEquals(BlockStatus.IDLE, statusOf(block, data, atDate(7, 0)))
        assertEquals("今天照样要挂超时", BlockStatus.MISSED, statusOf(block, data, atDate(10, 5)))
    }

    // ---------------------------------------------------------- 迟到补点开始

    @Test
    fun `超时之后补点开始要能进入进行中`() {
        val block = blockOnThatDay(16, 20, 16, 30)
        val now = atDate(16, 26)
        // 16:20 到点，16:25 还没点开始 → 已经落了一条「超时」
        val missed = Session(
            "m1", block.id, block.title, atDate(16, 20), atDate(16, 20), SessionState.MISSED
        )
        val data = AppData(blocks = listOf(block), sessions = listOf(missed))
        assertEquals("先确认此刻是超时", BlockStatus.MISSED, statusOf(block, data, now))

        val started = BlockFlow.markStarted(data, block, now)

        assertEquals(1, started.starts.size)
        assertEquals(
            "超时记录不能把状态钉死在 MISSED，否则按钮永远是播放键",
            BlockStatus.RUNNING,
            statusOf(block, started, now),
        )
        assertTrue("超时标签照旧保留", Missed.taggedToday(started, block, now))
    }

    @Test
    fun `补点开始以后点结束照常记完成且不重复记超时`() {
        val block = blockOnThatDay(16, 20, 16, 30)
        val missed = Session(
            "m1", block.id, block.title, atDate(16, 20), atDate(16, 20), SessionState.MISSED
        )
        val data = AppData(blocks = listOf(block), sessions = listOf(missed))
        val started = BlockFlow.markStarted(data, block, atDate(16, 26))
        val finished = BlockFlow.markFinished(started, block, atDate(16, 30))

        assertEquals(BlockStatus.DONE, statusOf(block, finished, atDate(16, 30)))
        assertEquals(1, finished.sessions.count { it.state == SessionState.MISSED })
        assertEquals(1, finished.sessions.count { it.state == SessionState.DONE })
    }

    // ---------------------------------------------------------- 不再允许跨天 + 最短 5 分钟

    @Test
    fun `时间段必须落在同一天里且不少于五分钟`() {
        assertTrue(Block.spanOk(8 * 60, 10 * 60))
        assertTrue("正好 5 分钟是下限，可以", Block.spanOk(23 * 60 + 54, 23 * 60 + 59))
        assertFalse("4 分钟太短", Block.spanOk(23 * 60 + 55, 23 * 60 + 59))
        assertFalse("结束等于开始请用时间点", Block.spanOk(8 * 60, 8 * 60))
        assertFalse("结束早于开始就是跨天，不支持", Block.spanOk(22 * 60 + 30, 6 * 60 + 30))
    }

    @Test
    fun `最短时长和超时宽限是同一个数`() {
        // 段长下限 = 宽限：超时标签落下的那一刻，这一段也正好走完，
        // 不会出现「先挂已结束、过一会儿再补超时」的两段式
        assertEquals(Block.MIN_SPAN_MIN * 60_000L, Missed.GRACE_MS)
    }

    @Test
    fun `正好五分钟的段到点即已结束同时超时`() {
        val block = blockOnThatDay(16, 20, 16, 25)
        val idle = AppData(blocks = listOf(block))

        // 4 分 59 秒：还在宽限里，窗口没走完，卡片还是待办
        assertEquals(BlockStatus.IDLE, statusOf(block, idle, atDate(16, 20) + 4 * 60_000L + 59_000L))
        // 到 5 分钟整：窗口走完 + 宽限到期，两个标签一起成立
        assertEquals(BlockStatus.MISSED, statusOf(block, idle, atDate(16, 25)))
        assertTrue("同刻挂上已结束", Missed.windowClosed(block, atDate(16, 25)))
    }

    @Test
    fun `宽限到五分钟整才判超时`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val idle = AppData(blocks = listOf(block))

        // 0 到 5 分钟以内（不含 5 分钟）都算按时，不落超时记录
        assertEquals(BlockStatus.IDLE, statusOf(block, idle, atDate(8, 0)))
        assertEquals(BlockStatus.IDLE, statusOf(block, idle, atDate(8, 4) + 59_000L))
        // 正好 5 分钟：判超时
        assertEquals(BlockStatus.MISSED, statusOf(block, idle, atDate(8, 5)))
        // 4:59 补点开始：不算超时
        val onTime = BlockFlow.markStarted(idle, block, atDate(8, 4) + 59_000L)
        val late = BlockFlow.markStarted(idle, block, atDate(8, 5))
        assertTrue("4:59 点开始算按时", Missed.startedOnTime(onTime, block, atDate(8, 0)))
        assertFalse("正好 5:00 点开始算迟到", Missed.startedOnTime(late, block, atDate(8, 0)))
    }

    @Test
    fun `满五分钟点完成才算迟到`() {
        val block = blockOnThatDay(8, 0, 10, 0)
        val idle = AppData(blocks = listOf(block))

        val onTime = BlockFlow.markFinished(idle, block, atDate(8, 4) + 59_000L)
        val late = BlockFlow.markFinished(idle, block, atDate(8, 5))

        assertTrue("4:59 完成不罚", onTime.sessions.none { it.state == SessionState.MISSED })
        assertEquals("5:00 完成补一条超时", 1, late.sessions.count { it.state == SessionState.MISSED })
    }

    // ---------------------------------------------------------- 闹钟排布：一块一条

    @Test
    fun `同一时刻两个块各排一条闹钟`() {
        val now = atDate(7, 0)
        val span = blockOnThatDay(8, 0, 10, 0)
        val point = blockOnThatDay(8, 0, 8, 0, point = true).copy(id = "p-0800", title = "起床")

        val plans = Scheduler.planAlarms(AppData(blocks = listOf(span, point)), now)
        val atEight = plans.filter { it.triggerAt == atDate(8, 0) }

        assertEquals("同一时刻两条，不再合并", 2, atEight.size)
        assertEquals("各管各的块", setOf("b-8-0", "p-0800"), atEight.map { it.blockId }.toSet())
        assertTrue("两条都是开始提醒", atEight.all { it.kind == Scheduler.KIND_START })
    }

    @Test
    fun `时间段开始和结束各一条时间点只有开始`() {
        val now = atDate(7, 0)
        val span = blockOnThatDay(8, 0, 10, 0)
        val point = blockOnThatDay(9, 0, 9, 0, point = true).copy(id = "p-0900", title = "吃药")

        val plans = Scheduler.planAlarms(AppData(blocks = listOf(span, point)), now)

        assertEquals(1, plans.count { it.blockId == span.id && it.kind == Scheduler.KIND_START })
        assertEquals(1, plans.count { it.blockId == span.id && it.kind == Scheduler.KIND_END })
        assertEquals("时间点没有收尾提醒", 0, plans.count { it.blockId == point.id && it.kind == Scheduler.KIND_END })
        assertEquals(3, plans.size)
    }

    @Test
    fun `停用的块不排闹钟`() {
        val now = atDate(7, 0)
        val off = blockOnThatDay(8, 0, 10, 0, enabled = false)
        assertTrue(Scheduler.planAlarms(AppData(blocks = listOf(off)), now).isEmpty())
    }

    @Test
    fun `过了的场次不再往前排`() {
        val now = atDate(12, 0)
        val morning = blockOnThatDay(8, 0, 10, 0)
        assertTrue(Scheduler.planAlarms(AppData(blocks = listOf(morning)), now).isEmpty())
    }
}
