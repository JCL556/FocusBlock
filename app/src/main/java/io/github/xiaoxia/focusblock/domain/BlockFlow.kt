package io.github.xiaoxia.focusblock.domain

import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.BlockStart
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.sameLocalDay
import java.util.UUID

/**
 * 作息块的「开始 / 结束」流程。作息只记两个点，不跑倒计时：
 *
 * 到开始时间 → 提醒 → 点「开始」落一条进行中标记；
 * 到结束时间 → 提醒 → 点「结束」落一条 DONE，这一场就算完成。
 *
 * 纯函数，App 内点击和通知按钮都走这里，免得两套逻辑跑偏。
 */
object BlockFlow {

    /**
     * 这一块现在是不是「进行中」：今天这一场，或者跨天块昨晚那场还没结束。
     *
     * 上限放宽到「下一场开始之前」，而不是「这一场结束之前」：
     * 18:00–19:00 的块 19:10 才点开始，也得认成这一场点过了，
     * 否则卡片会一直卡在待办 / 超时，按钮点下去看着没反应。
     */
    fun activeStart(d: AppData, block: Block, now: Long): BlockStart? {
        val mine = d.starts.filter { it.blockId == block.id }
        if (mine.isEmpty()) return null
        val windows = buildList {
            Scheduler.occurrenceStart(now, block, 0)?.let { add(it) }
            if (block.crossDay) Scheduler.occurrenceStart(now, block, -1)?.let { add(it) }
        }
        if (windows.isEmpty()) return null
        return mine.firstOrNull { s ->
            windows.any { start ->
                s.startTs >= start - 3600_000L && s.startTs < start + 24 * 3600_000L
            }
        }
    }

    /**
     * 时间点块到没到可以打卡的时刻。
     * 没到设定时间不给提前点完成，到点（含过了）才放行。
     */
    fun pointReady(block: Block, now: Long): Boolean =
        if (!block.point) true else reachedStart(block, now)

    /**
     * 时间段块到没到可以点「开始」的时刻。跟时间点一条规矩：
     * 没到设定开始时间不给点，到点（含过了）才放行。
     */
    fun startReady(block: Block, now: Long): Boolean =
        if (block.point) true else reachedStart(block, now)

    /**
     * 今天这一场到没到开始时刻。
     *
     * 只看「今天这一场」：昨天那场早过了，不能拿来顶今天的
     * （否则每天重复的块，凌晨想打晚上 7 点的卡都能打）。
     * 跨天块例外——昨晚那场还在跑，得让它能点；
     * 今天本来没这一场（不在重复日里）时，也允许补昨天那次。
     */
    private fun reachedStart(block: Block, now: Long): Boolean {
        val today = Scheduler.occurrenceStart(now, block, 0)
        if (today != null && now >= today) return true
        val prev = Scheduler.occurrenceStart(now, block, -1)
        if (prev != null && now >= prev) {
            // 跨天块的凌晨那一段：昨晚这场还没结束，算「到点了」，可以补点开始
            if (block.crossDay && !Missed.windowOver(prev, block, now)) return true
            // 今天本来没这一场：允许补昨天那次
            if (today == null) return true
        }
        return today == null && prev == null
    }

    /** 点「开始」：只加一个进行中标记 */
    fun markStarted(d: AppData, block: Block, now: Long): AppData {
        if (activeStart(d, block, now) != null) return d
        return d.copy(starts = d.starts + BlockStart(block.id, now))
    }

    /**
     * 点「结束 / 完成」：落一条 DONE，卡片随即变灰。
     *
     * 迟到的（过了 5 分钟宽限才点）同时补一条 MISSED，只用来留个「超时」标签、
     * 让这一天在日历里不打勾——卡片该怎么显示还是看 DONE。
     */
    fun markFinished(d: AppData, block: Block, now: Long): AppData {
        val cleaned = d.copy(starts = d.starts.filterNot { it.blockId == block.id })
        // 跨天块凌晨点结束，要归到昨晚那场去（不然记录会挂到还没开始的今天那场）
        val occurrence = Missed.currentOccurrence(block, now)
            ?: Scheduler.occurrenceStart(now, block, -1)
            ?: now
        val startedAt = activeStart(d, block, now)?.startTs
        // 跟 Missed.due() / startedOnTime() 用同一个边界：满 5 分钟整就算迟到，
        // 别出现「补账判了超时、点结束判准时」两种结论
        val late = (startedAt ?: now) >= Missed.deadline(occurrence)

        val added = mutableListOf<Session>()
        if (late && !Missed.hasOutcome(d, block, occurrence)) {
            added += missedSession(block, occurrence)
        }
        val doneToday = d.sessions.any {
            it.state == SessionState.DONE && Missed.belongsTo(it, block, occurrence)
        }
        if (!doneToday) {
            val from = startedAt ?: occurrence.coerceAtMost(now)
            added += Session(
                id = UUID.randomUUID().toString(),
                blockId = block.id,
                titleSnapshot = block.title,
                startTs = from,
                endTs = now.coerceAtLeast(from),
                state = SessionState.DONE,
                roundIndex = 0,
            )
        }
        if (added.isEmpty()) return cleaned
        return cleaned.copy(sessions = cleaned.sessions + added)
    }

    /** 一条「超时」标记：时刻挂在设定时刻上，历史归到那一场所在的那天 */
    fun missedSession(block: Block, occurrenceStart: Long): Session = Session(
        id = UUID.randomUUID().toString(),
        blockId = block.id,
        titleSnapshot = block.title,
        startTs = occurrenceStart,
        endTs = occurrenceStart,
        state = SessionState.MISSED,
        roundIndex = 0,
    )

    /** 跳过：清掉进行中标记，落一条 SKIPPED */
    fun markSkipped(d: AppData, block: Block, now: Long): AppData {
        val cleaned = d.copy(starts = d.starts.filterNot { it.blockId == block.id })
        val already = d.sessions.any {
            it.blockId == block.id && it.state == SessionState.SKIPPED && sameLocalDay(it.startTs, now)
        }
        if (already) return cleaned
        val occurrence = Missed.currentOccurrence(block, now) ?: now
        return cleaned.copy(
            sessions = cleaned.sessions + Session(
                id = UUID.randomUUID().toString(),
                blockId = block.id,
                titleSnapshot = block.title,
                startTs = occurrence.coerceAtMost(now),
                endTs = now,
                state = SessionState.SKIPPED,
                roundIndex = 0,
            )
        )
    }

    /**
     * 这一块的「时间设定」有没有被改过。
     * 改名、换图标、改提醒方式都不算；动了开始/结束/时间点/星期才算。
     */
    fun timingChanged(old: Block?, new: Block): Boolean {
        if (old == null) return false
        return old.startMin != new.startMin ||
            old.endMin != new.endMin ||
            old.point != new.point ||
            old.days != new.days
    }

    /**
     * 时间改了之后，今天剩下的记录全是按旧时间打出来的，留着就会让改到未来的块
     * 一直显示成「已完成 / 已超时」。这里把今天这一块的记录清掉（历史的日子不动），
     * 让它回到新时间该有的状态。
     */
    fun clearTodayRecords(d: AppData, block: Block, now: Long): AppData {
        // 按天清（改完时间后旧记录的时刻跟新时间对不上，只能按天兜）。
        // 跨天块凌晨改时间时，昨晚那场的记录也算「今天这一场」，一并清掉。
        val sessions = d.sessions.filterNot {
            it.blockId == block.id && (
                sameLocalDay(it.startTs, now) ||
                    (block.crossDay && sameLocalDay(it.startTs, now - 24 * 3600_000L))
                )
        }
        val starts = d.starts.filterNot { it.blockId == block.id }
        if (sessions.size == d.sessions.size && starts.size == d.starts.size) return d
        return d.copy(sessions = sessions, starts = starts)
    }

    /** 取一句休息提示，同时把游标往后挪一位 */
    fun takeRestTip(d: AppData): Pair<String, AppData> {
        val idx = d.settings.restTipIndex
        return RestTips.of(idx) to d.copy(settings = d.settings.copy(restTipIndex = RestTips.next(idx)))
    }
}
