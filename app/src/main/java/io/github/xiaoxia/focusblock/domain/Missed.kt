package io.github.xiaoxia.focusblock.domain

import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.Session
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.sameLocalDay

/**
 * 「超时」的唯一口径，闹钟、补账、界面都走这里：
 *
 * - 到了设定时刻，**5 分钟以内**（不含 5 分钟整）点了「开始」/「完成」→ 按时；
 *   满 5 分钟还没动作 → 挂「超时」。
 * - 超时只是加个标签：卡片仍然常亮，仍然可以补点开始 / 结束。
 * - 一旦超时，这一天的日历就不再打勾（哪怕后来补做完了）。
 */
object Missed {

    /**
     * 到点后给的宽限：5 分钟。
     * 与 [Block.MIN_SPAN_MIN] 同源——时间段最短就是 5 分钟，
     * 所以「挂超时」和「这一段走完」永远同时发生，不会一个先一个后。
     */
    const val GRACE_MS = Block.MIN_SPAN_MIN * 60_000L

    fun deadline(occurrenceStart: Long): Long = occurrenceStart + GRACE_MS

    /**
     * 这一场的时间窗口什么时候走完：
     * 时间段看到结束时刻，时间点到设定那一刻就算走完。
     */
    fun windowEndOf(startMs: Long, block: Block): Long =
        if (block.point) startMs else startMs + block.durationMin * 60_000L

    /** 这一场的时间是不是已经走完了（跟打没打卡无关） */
    fun windowOver(startMs: Long, block: Block, now: Long): Boolean =
        now >= windowEndOf(startMs, block)

    /**
     * 这一场是「过完了之后才建的块」：新建时窗口已经结束，
     * 那就不该算他超时（那会儿这块根本还不存在），只挂「已结束」。
     */
    fun builtAfterWindow(block: Block, occurrenceStart: Long): Boolean =
        block.createdAt > 0L && block.createdAt >= windowEndOf(occurrenceStart, block)

    /**
     * 此刻对应的是哪一场。
     *
     * 普通块就是今天这场；跨天块在凌晨那一段（昨晚那场还没到结束时刻）算昨晚那场，
     * 否则算今天这场。全天下的「打卡、判状态、归天」都用它，别各算各的。
     */
    fun currentOccurrence(block: Block, now: Long): Long? {
        val today = Scheduler.occurrenceStart(now, block, 0)
        if (block.crossDay) {
            val prev = Scheduler.occurrenceStart(now, block, -1)
            // 昨晚那场还在跑（凌晨 00:00–结束时刻）：这一场归它
            if (prev != null && now < windowEndOf(prev, block)) return prev
        }
        return today
    }

    /**
     * 这一场是不是已经过了判定时刻：到点整整 5 分钟就算过了。
     * 4 分 59 秒都还算宽限内，5 分 00 秒开始算超时。
     */
    fun due(occurrenceStart: Long, now: Long): Boolean = now >= deadline(occurrenceStart)

    /**
     * 这一场走到设定的结束时刻了没有（不管打卡与否、完成与否）。
     * 卡片上的「已结束」标签就看这个：
     *
     * - 时间段：到**设定的**结束时刻就挂，不按实际开始时间顺延（18:00–19:00 的块，
     *   哪怕 18:50 才点开始，19:00 一样挂「已结束」）。
     * - 时间点：到设定那一刻就挂。
     * - 完成的、超时的、没动的都一样挂。
     *
     * 只看今天这一场：跨天块昨天那场早结束了，不能拿它给今天这场挂「已结束」
     * （否则 14:49 看一个 14:55 才开始的块，也会写着「已结束」）。
     */
    fun windowClosed(block: Block, now: Long): Boolean {
        if (!block.enabled) return false
        val occ = currentOccurrence(block, now) ?: return false
        return windowOver(occ, block, now)
    }

    /**
     * 这条记录是不是属于这一场（按绑定时刻判断，跨天块也能对上）。
     *
     * 上限放到「下一场开始之前」而不是「这一场结束」：窗口过了才补做的那些，
     * 记录时刻会落在结束时刻之后，按旧口径就归不到这一场，历史里会凭空少一行。
     * 下限仍是开场前一分钟，昨天的记录不会被今天这场认领。
     */
    fun belongsTo(s: Session, block: Block, occurrenceStart: Long): Boolean =
        s.blockId == block.id &&
            s.startTs >= occurrenceStart - 60_000L &&
            s.startTs < occurrenceStart + 24 * 3600_000L

    /** 这一场已经有结论了（完成 / 跳过 / 已经记过超时），不用再判 */
    fun hasOutcome(d: AppData, block: Block, occurrenceStart: Long): Boolean =
        d.sessions.any {
            belongsTo(it, block, occurrenceStart) && it.state != SessionState.POSTPONED
        }

    /**
     * 宽限时间内点了「开始」。
     * 用严格小于：到点满 5 分钟那一刻点下去，跟「已判超时」是同一个时刻，
     * 算迟到，不算按时——跟 [due] 的 `>=` 正好互补，不会两头都算。
     */
    fun startedOnTime(d: AppData, block: Block, occurrenceStart: Long): Boolean =
        d.starts.any {
            it.blockId == block.id &&
                it.startTs >= occurrenceStart - 3_600_000L &&
                it.startTs < deadline(occurrenceStart)
        }

    /** 现在该不该给这一场记「超时」 */
    fun isDue(d: AppData, block: Block, occurrenceStart: Long, now: Long): Boolean =
        block.enabled &&
            due(occurrenceStart, now) &&
            !builtAfterWindow(block, occurrenceStart) &&
            !hasOutcome(d, block, occurrenceStart) &&
            !startedOnTime(d, block, occurrenceStart)

    /** 今天这一块是不是挂着「超时」标签 */
    fun taggedToday(d: AppData, block: Block, now: Long): Boolean {
        val occ = currentOccurrence(block, now) ?: return false
        return d.sessions.any {
            it.state == SessionState.MISSED && belongsTo(it, block, occ)
        }
    }
}
