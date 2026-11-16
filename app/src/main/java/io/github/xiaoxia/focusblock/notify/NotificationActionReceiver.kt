package io.github.xiaoxia.focusblock.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.data.Store
import io.github.xiaoxia.focusblock.domain.BlockFlow
import io.github.xiaoxia.focusblock.domain.RestTips
import io.github.xiaoxia.focusblock.domain.Scheduler

/**
 * 通知上的按钮：
 * - 开始 → 记一条进行中标记（作息不跑倒计时）
 * - 结束 → 落一条 DONE，同时弹一句「去休息」的提示
 * - 跳过 / 延后 → 已从通知上撤掉，仅留兜底分支处理系统里还没清掉的旧通知
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val blockId = intent.getStringExtra(Scheduler.EXTRA_BLOCK_IDS) ?: return
        val triggerAt = intent.getLongExtra(Scheduler.EXTRA_TRIGGER_AT, 0L)
        if (triggerAt <= 0L) return

        runAsync {
            val store = Store.of(context)
            val data = store.current()
            val block = data.blocks.find { it.id == blockId }
            val title = block?.title ?: intent.getStringExtra(EXTRA_TITLE) ?: "已删除的作息"
            val now = System.currentTimeMillis()

            when (action) {
                Notifier.ACT_START -> {
                    if (block != null) store.update { cur -> BlockFlow.markStarted(cur, block, now) }
                    Notifier.cancel(context, blockId)
                }

                Notifier.ACT_END -> {
                    if (block != null && !hasResult(data, blockId, triggerAt)) {
                        val idx = data.settings.restTipIndex
                        store.update { cur ->
                            val finished = BlockFlow.markFinished(cur, block, now)
                            finished.copy(settings = finished.settings.copy(restTipIndex = RestTips.next(idx)))
                        }
                            Notifier.cancel(context, blockId)
                        Notifier.showRestTip(context, RestTips.of(idx), data.settings)
                    } else {
                            Notifier.cancel(context, blockId)
                    }
                }

                Notifier.ACT_SKIP -> {
                    if (block != null) store.update { cur -> BlockFlow.markSkipped(cur, block, now) }
                    Notifier.cancel(context, blockId)
                }

                // 「延后 5 分钟」已下线，只在旧通知残留时兜底：关掉通知，不落记录
                Notifier.ACT_POSTPONE -> Notifier.cancel(context, blockId)
            }
        }
    }

    /** 这一场已经有结论（完成 / 跳过）就不再重复落库 */
    private fun hasResult(
        data: io.github.xiaoxia.focusblock.data.AppData,
        blockId: String,
        triggerAt: Long,
    ): Boolean = data.sessions.any {
        it.blockId == blockId &&
            it.startTs >= triggerAt - 6 * 3600_000L &&
            it.startTs <= triggerAt + 6 * 3600_000L &&
            (it.state == SessionState.DONE || it.state == SessionState.SKIPPED)
    }

    companion object {
        const val EXTRA_ACTION = "nb_action"
        const val EXTRA_TITLE = "nb_title"
    }
}
