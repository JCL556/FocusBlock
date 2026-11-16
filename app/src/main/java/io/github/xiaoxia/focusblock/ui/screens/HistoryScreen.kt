package io.github.xiaoxia.focusblock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.formatClock
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.Tab
import io.github.xiaoxia.focusblock.ui.components.BottomTabs
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.StatusTag
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones
import java.util.Calendar

/**
 * 日历页：按月看。某天所有计划块都做完了，日期下面才打勾，其余留白。
 * 点某一天在下面展开当天明细，免得日历只剩个勾、看不到具体做了什么。
 */
@Composable
fun HistoryScreen(vm: AppVm, data: AppData) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val now = rememberNow(5000L)

    val today = dayStart(now)
    var monthAnchor by remember { mutableStateOf(monthStart(now)) }
    var selected by remember { mutableStateOf(today) }

    val cells = remember(monthAnchor) { buildMonthCells(monthAnchor) }
    val doneByDay = remember(data.sessions) { doneBlocksByDay(data.sessions) }
    val missedByDay = remember(data.sessions) { missedBlocksByDay(data.sessions) }
    val stats = remember(cells, data.blocks, doneByDay, missedByDay) {
        cells.map { ts -> if (ts == null) null else dayStat(ts, data.blocks, doneByDay, missedByDay) }
    }

    // 本月已过去（含今天）且有安排的天数里，有几天是全做完的
    val pastPlanned = cells.indices.filter { i ->
        val ts = cells[i]
        ts != null && ts <= today && (stats[i]?.planned ?: 0) > 0
    }
    val allDoneDays = pastPlanned.count { stats[it]?.allDone == true }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        monthTitle(monthAnchor, now),
                        style = MaterialTheme.typography.titleLarge,
                        color = extra.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (pastPlanned.isEmpty()) "本月还没有安排"
                        else "本月 $allDoneDays/${pastPlanned.size} 天全部完成（有超时的不打勾）",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                }
                MonthArrow(Icons.Filled.KeyboardArrowLeft, "上个月") {
                    monthAnchor = shiftMonth(monthAnchor, -1)
                }
                Spacer(Modifier.width(4.dp))
                MonthArrow(Icons.Filled.KeyboardArrowRight, "下个月") {
                    monthAnchor = shiftMonth(monthAnchor, 1)
                }
            }

            Spacer(Modifier.height(14.dp))

            FbCard(corner = 24, padding = 12) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Block.WEEK_LABELS.forEach { label ->
                            Text(
                                // 周头只留「一 二 三 …」
                                label.removePrefix("周"),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.textAux,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    cells.chunked(7).forEachIndexed { wi, week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEachIndexed { di, ts ->
                                val st = stats.getOrNull(wi * 7 + di)
                                Box(Modifier.weight(1f)) {
                                    if (ts == null) {
                                        Spacer(Modifier.height(52.dp).fillMaxWidth())
                                    } else {
                                        DayCell(
                                            dayTs = ts,
                                            stat = st,
                                            isToday = ts == today,
                                            isSelected = ts == selected,
                                            isFuture = ts > today,
                                            onClick = { selected = ts },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            DayDetail(
                dayTs = selected,
                sessions = data.sessions,
                blocks = data.blocks,
                doneByDay = doneByDay,
                missedByDay = missedByDay,
                now = now,
            )
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().background(extra.card.copy(alpha = 0.98f))) {
            BottomTabs(current = Tab.HISTORY, onSelect = { vm.selectTab(it) })
        }
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val extra = LocalExtra.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(extra.soft, CircleShape)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = extra.textAux, modifier = Modifier.size(20.dp))
    }
}

/** 单个日期格：数字 + 下面一个勾（全部完成才有） */
@Composable
private fun DayCell(
    dayTs: Long,
    stat: DayStat?,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val c = Calendar.getInstance().apply { timeInMillis = dayTs }
    val day = c.get(Calendar.DAY_OF_MONTH)

    val numberBg = when {
        isToday -> tones.focus
        isSelected -> extra.soft
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val numberColor = when {
        isToday -> tones.onTone
        isFuture -> extra.outline
        else -> extra.text
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(numberBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = numberColor,
            )
        }
        Spacer(Modifier.height(1.dp))
        Box(Modifier.height(13.dp), contentAlignment = Alignment.Center) {
            if (stat?.allDone == true) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "全部完成",
                    tint = tones.mark,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** 选中那天的明细 */
@Composable
private fun DayDetail(
    dayTs: Long,
    sessions: List<Session>,
    blocks: List<Block>,
    doneByDay: Map<Long, Set<String>>,
    missedByDay: Map<Long, Set<String>>,
    now: Long,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current

    val list = remember(sessions, dayTs) { detailRows(sessions, dayTs) }
    val st = remember(dayTs, blocks, doneByDay, missedByDay) {
        dayStat(dayTs, blocks, doneByDay, missedByDay)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            dayTitle(dayTs, now),
            style = MaterialTheme.typography.titleMedium,
            color = extra.text,
        )
        Spacer(Modifier.weight(1f))
        val summary = when {
            st.planned <= 0 -> "这天没有安排"
            st.missed > 0 -> "${st.done}/${st.planned} 完成 · ${st.missed} 个超时"
            else -> "${st.done}/${st.planned} 完成"
        }
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (st.allDone) tones.mark else extra.textAux,
            fontWeight = if (st.allDone) FontWeight.Medium else FontWeight.Normal,
        )
    }

    Spacer(Modifier.height(10.dp))

    if (list.isEmpty()) {
        FbCard(corner = 20, padding = 16) {
            Text(
                if (st.missed > 0) "这天没有完成的任务，有 ${st.missed} 个超时了" else "这天还没有完成的任务",
                style = MaterialTheme.typography.bodySmall,
                color = extra.textAux,
            )
        }
        return
    }

    FbCard(corner = 20, padding = 4) {
        Column {
            list.forEachIndexed { i, row ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(1.dp)
                            .background(extra.soft)
                    )
                }
                SessionRow(row.session, row.missedAlso)
            }
        }
    }
}

internal data class DetailRow(val session: Session, val missedAlso: Boolean)

@Composable
private fun SessionRow(s: Session, missedAlso: Boolean = false) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val tagColor = when (s.state) {
        SessionState.DONE -> tones.success
        SessionState.SKIPPED -> tones.warn
        SessionState.POSTPONED -> tones.longBreak
        SessionState.MISSED -> tones.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatClock(s.startTs),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = extra.textAux,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            s.titleSnapshot.ifBlank { "自由专注" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = extra.text,
        )
        if (missedAlso) {
            Spacer(Modifier.width(6.dp))
            StatusTag("超时", tones.error)
        }
        Spacer(Modifier.width(8.dp))
        StatusTag(s.stateLabel, tagColor)
    }
}

// ---------- 计算部分 ----------

internal data class DayStat(val planned: Int, val done: Int, val missed: Int = 0) {
    /** 全部点完，而且一个「超时」都没有，这天日历才打勾 */
    val allDone: Boolean get() = planned > 0 && done >= planned && missed == 0
}

/** 0 = 周一 … 6 = 周日 */
internal fun weekdayIndex(ts: Long): Int =
    (Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK) + 5) % 7

internal fun dayStart(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun monthStart(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart(ts)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

internal fun shiftMonth(anchor: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = anchor
    set(Calendar.DAY_OF_MONTH, 1)
    add(Calendar.MONTH, delta)
}.timeInMillis

/**
 * 整月格子。开头补齐到周一，结尾补齐到周日；不足 5 行补到 5 行，
 * 避免切月份时卡片高度跳动。null 表示空位，不画上月/下月的日期。
 */
internal fun buildMonthCells(anchor: Long): List<Long?> {
    val c = Calendar.getInstance().apply { timeInMillis = anchor }
    val firstOffset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)

    val out = mutableListOf<Long?>()
    repeat(firstOffset) { out += null }
    for (d in 1..daysInMonth) {
        out += Calendar.getInstance().apply {
            timeInMillis = anchor
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    while (out.size % 7 != 0) out += null
    while (out.size < 35) out += null
    return out
}

/**
 * 某一天的明细行：完成的和超时的一起列，按时间先后排。
 * 迟到补做的块同时有 DONE 和 MISSED 两条记录，合成一行（完成 + 超时），不重复列。
 */
internal fun detailRows(sessions: List<Session>, dayTs: Long): List<DetailRow> {
    val ofDay = sessions.filter {
        dayStart(it.startTs) == dayTs &&
            (it.state == SessionState.DONE || it.state == SessionState.MISSED)
    }
    val done = ofDay.filter { it.state == SessionState.DONE }
    val doneBlocks = done.mapNotNull { it.blockId }.toSet()
    val doneRows = done.map { s ->
        DetailRow(s, missedAlso = ofDay.any { m -> m.state == SessionState.MISSED && m.blockId == s.blockId })
    }
    val missedRows = ofDay
        .filter { it.state == SessionState.MISSED && (it.blockId == null || it.blockId !in doneBlocks) }
        .map { DetailRow(it, missedAlso = false) }
    return (doneRows + missedRows).sortedBy { it.session.startTs }
}

/** 每天已完成的块 id 集合（按 session 开始那天归天） */
internal fun doneBlocksByDay(sessions: List<Session>): Map<Long, Set<String>> =
    sessions.asSequence()
        .filter { it.state == SessionState.DONE && it.blockId != null }
        .groupBy({ dayStart(it.startTs) }, { it.blockId!! })
        .mapValues { it.value.toSet() }

/** 每天挂了「超时」标签的块 id 集合 */
internal fun missedBlocksByDay(sessions: List<Session>): Map<Long, Set<String>> =
    sessions.asSequence()
        .filter { it.state == SessionState.MISSED && it.blockId != null }
        .groupBy({ dayStart(it.startTs) }, { it.blockId!! })
        .mapValues { it.value.toSet() }

internal fun dayStat(
    dayTs: Long,
    blocks: List<Block>,
    doneByDay: Map<Long, Set<String>>,
    missedByDay: Map<Long, Set<String>> = emptyMap(),
): DayStat {
    val idx = weekdayIndex(dayTs)
    val planned = blocks.filter { it.enabled && it.days.getOrElse(idx) { false } }
    if (planned.isEmpty()) return DayStat(0, 0, 0)
    val ids = planned.map { it.id }.toSet()
    val done = (doneByDay[dayTs] ?: emptySet()).filter { it in ids }
    val missed = (missedByDay[dayTs] ?: emptySet()).filter { it in ids }
    return DayStat(planned.size, done.size, missed.size)
}

private fun monthTitle(anchor: Long, now: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = anchor }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val m = c.get(Calendar.MONTH) + 1
    return if (c.get(Calendar.YEAR) == n.get(Calendar.YEAR)) "${m}月"
    else "${c.get(Calendar.YEAR)}年${m}月"
}

private fun dayTitle(dayTs: Long, now: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = dayTs }
    val today = dayStart(now)
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis
    val prefix = when (dayTs) {
        today -> "今天 · "
        yesterday -> "昨天 · "
        else -> ""
    }
    return "$prefix${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 ${Block.WEEK_LABELS[weekdayIndex(dayTs)]}"
}
