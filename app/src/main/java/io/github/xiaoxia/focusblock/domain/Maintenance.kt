package io.github.xiaoxia.focusblock.domain

import android.content.Context
import io.github.xiaoxia.focusblock.data.Store
import io.github.xiaoxia.focusblock.notify.Notifier

/**
 * 开机 / 每天 00:05 / 改表之后的统一维护：先补账，再重排闹钟。
 */
object Maintenance {

    suspend fun run(context: Context) {
        val store = Store.of(context)
        store.update { cur ->
            val sweep = Scheduler.sweepMissed(cur)
            cur.copy(sessions = cur.sessions + sweep.sessions, starts = sweep.starts)
        }
        val data = store.current()
        Notifier.ensureChannel(context, data.settings)
        Scheduler.rescheduleAll(context, data)
    }
}
