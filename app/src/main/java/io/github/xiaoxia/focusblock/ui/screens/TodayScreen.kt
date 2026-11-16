package io.github.xiaoxia.focusblock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.data.BlockStatus
import io.github.xiaoxia.focusblock.data.SessionState
import io.github.xiaoxia.focusblock.domain.BlockFlow
import io.github.xiaoxia.focusblock.domain.Missed
import io.github.xiaoxia.focusblock.domain.Scheduler
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.Tab
import io.github.xiaoxia.focusblock.ui.components.BlockIcon
import io.github.xiaoxia.focusblock.ui.components.BottomTabs
import io.github.xiaoxia.focusblock.ui.components.EmptyState
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.Mascot
import io.github.xiaoxia.focusblock.ui.components.MascotKind
import io.github.xiaoxia.focusblock.ui.components.SecondaryButton
import io.github.xiaoxia.focusblock.ui.components.StatusTag
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones
import java.util.Calendar
import java.util.Locale

@Composable
fun TodayScreen(
    vm: AppVm,
    data: AppData,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val now = rememberNow(5000L)

    val todayIdx = (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val todayBlocks = data.blocks.filter { it.days.getOrElse(todayIdx) { false } }
    val enabledCount = todayBlocks.count { it.enabled }
    val doneCount = todayBlocks.count { statusOf(it, data, now) == BlockStatus.DONE }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotKind.FOCUS, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting(now),
                        style = MaterialTheme.typography.titleMedium,
                        color = extra.text,
                    )
                    Text(
                        "${dateLabel(now)} · 今天 $doneCount/$enabledCount 个时间块",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (todayBlocks.isEmpty()) {
                EmptyState(
                    kind = MascotKind.DAZE,
                    title = "还没有作息计划，添加第一个时间块开始安排今天",
                    actionText = "新建时间块",
                    onAction = { vm.navTo(io.github.xiaoxia.focusblock.ui.Route.Editor(null)) },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    todayBlocks.forEach { block ->
                        BlockCard(
                            block = block,
                            data = data,
                            now = now,
                            onStart = { vm.startBlock(block) },
                            onFinish = { vm.endBlock(block) },
                            onCheck = { vm.markPointDone(block) },
                            onEdit = { vm.navTo(io.github.xiaoxia.focusblock.ui.Route.Editor(block.id)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "新增时间块",
                    onClick = { vm.navTo(io.github.xiaoxia.focusblock.ui.Route.Editor(null)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(extra.card.copy(alpha = 0.98f))
        ) {
            BottomTabs(current = Tab.TODAY, onSelect = { vm.selectTab(it) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockCard(
    block: Block,
    data: AppData,
    now: Long,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val status = statusOf(block, data, now)

    // 时间点还没到设定时刻：按钮压成灰色，点下去只会给个提示，不落记录
    val pointNotYet = block.enabled &&
        block.point &&
        status == BlockStatus.IDLE &&
        !BlockFlow.pointReady(block, now)

    // 时间段还没到开始时刻：跟时间点一条规矩，不许提前点开始
    val startNotYet = block.enabled &&
        !block.point &&
        status == BlockStatus.IDLE &&
        !BlockFlow.startReady(block, now)

    val locked = pointNotYet || startNotYet || !block.enabled

    // 只有「点完了」和「跳过」才压灰；超时只是标签，卡片照样常亮、照样能补点
    val cardAlpha = when {
        !block.enabled -> 0.5f
        status == BlockStatus.SKIPPED -> 0.6f
        status == BlockStatus.DONE -> 0.55f
        else -> 1f
    }
    // 挂着「超时」就永远带着这个小标签：没做的、迟到了补做的都算。
    // status 兜底判出来的超时可能还没落记录，所以两种来源都认。
    val missedTag = status == BlockStatus.MISSED || Missed.taggedToday(data, block, now)
    // 过了设定的结束时刻就挂「已结束」，完成的、超时的、进行中的都一样
    val endedTag = Missed.windowClosed(block, now)
    val borderColor = if (status == BlockStatus.RUNNING) tones.focus else null
    // 已完成的主文案也降一档，光靠整体透明度不够灰
    val titleColor = if (status == BlockStatus.DONE) extra.textAux else extra.text

    FbCard(
        modifier = Modifier.alpha(cardAlpha),
        borderColor = borderColor,
        onClick = onEdit,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                // 右下角有 40dp 按钮、右上角有状态标，内容太矮两者会叠在一起，给个下限
                modifier = Modifier
                    .padding(end = 56.dp)
                    .heightIn(min = 68.dp),
                verticalAlignment = Alignment.Top,
            ) {
                BlockIcon(block.iconKey, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // 标题后面可能挂好几个小标（时间点 / 已结束 / 超时），放不下就换行
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            block.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                        )
                        if (block.point) CardChip("时间点", extra.textAux)
                        if (endedTag) CardChip("已结束", extra.textAux)
                        if (missedTag) CardChip("超时", tones.error)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        block.timeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (status == BlockStatus.RUNNING) extra.text else extra.textAux,
                        fontWeight = if (status == BlockStatus.RUNNING) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        (if (block.fullscreen) "${block.repeatLabel} · 锁屏强提醒" else block.repeatLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                }
            }

            // 右上：只放「进行中 / 勾 / 跳过」。「已结束」「超时」都挪到标题后面了
            Box(Modifier.align(Alignment.TopEnd)) {
                when (status) {
                    BlockStatus.RUNNING -> StatusTag("进行中", tones.focus)
                    BlockStatus.DONE -> Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(tones.success, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "已完成", tint = tones.onTone, modifier = Modifier.size(15.dp))
                    }
                    BlockStatus.SKIPPED -> StatusTag("跳过", tones.warn)
                    BlockStatus.ENDED, BlockStatus.MISSED, BlockStatus.IDLE -> Unit
                }
            }

            // 右下：40dp 按钮。时间点 + 进行中 + 已结束都是「点一下完成任务」，待办是「开始」
            if (status != BlockStatus.DONE) {
                val finished = block.point || status == BlockStatus.RUNNING || status == BlockStatus.ENDED
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(40.dp)
                        .background(
                            when {
                                locked -> extra.outline
                                finished -> tones.success
                                else -> tones.focus
                            },
                            CircleShape
                        )
                        .clickableNoRipple {
                            if (!block.enabled) return@clickableNoRipple
                            when {
                                block.point -> onCheck()
                                status == BlockStatus.RUNNING || status == BlockStatus.ENDED -> onFinish()
                                else -> onStart()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        // 还没到点的块 / 停用的块：挂把锁，一眼就知道这会儿点不了
                        when {
                            locked -> Icons.Filled.Lock
                            finished -> Icons.Filled.Check
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = when {
                            pointNotYet -> "还没到 ${block.startLabel}"
                            startNotYet -> "还没到 ${block.startLabel}"
                            !block.enabled -> "这个块停用了"
                            block.point -> "打卡完成"
                            finished -> "结束这一场"
                            else -> "开始"
                        },
                        tint = if (locked) extra.textAux else tones.onTone,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 标题后面那种小圆角标：时间点 / 已结束 / 超时 */
@Composable
private fun CardChip(text: String, color: Color) {
    val extra = LocalExtra.current
    Box(
        Modifier
            .background(extra.soft, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

fun statusOf(block: Block, data: AppData, now: Long): BlockStatus {
    // 按「这一场」取记录：跨天块凌晨拿的是昨晚那场，普通块就是今天这场
    val occ = Missed.currentOccurrence(block, now) ?: Scheduler.occurrenceStart(now, block, 0)
    val records = if (occ == null) {
        emptyList()
    } else {
        data.sessions.filter { Missed.belongsTo(it, block, occ) }
    }

    // 点过「结束 / 完成」就是灰的，别的都不用再看了
    if (records.any { it.state == SessionState.DONE }) return BlockStatus.DONE

    // 点过「开始」：到设定结束时刻之前算进行中，到点就不再挂「进行中」，改叫「已结束」。
    // 按设定时刻算，不按实际开始时间顺延——18:00–19:00 的块，18:50 才点开始，19:00 一样结束。
    // 必须排在「超时」前面——迟到补点开始的那一场，超时记录已经落过了，
    // 先判超时的话状态会一直卡在 MISSED，按钮永远是播放键，点了看着没反应
    val running = BlockFlow.activeStart(data, block, now)
    if (running != null) {
        val from = occ ?: running.startTs
        return if (Missed.windowOver(from, block, now)) BlockStatus.ENDED else BlockStatus.RUNNING
    }

    // 已经挂了「超时」标签：卡片照常亮，随时能补点
    if (records.any { it.state == SessionState.MISSED }) return BlockStatus.MISSED
    if (records.any { it.state == SessionState.SKIPPED }) return BlockStatus.SKIPPED

    // 一次没点：到设定时刻先挂「已结束」（常亮），到点满 5 分钟才算超时
    val start = Scheduler.occurrenceStart(now, block, 0)
    if (block.enabled && start != null) {
        // 新建时就已过完的场次（比如晚上才建一个上午的块）：直接算已结束，不扣「超时」
        if (Missed.builtAfterWindow(block, start)) return BlockStatus.ENDED
        if (Missed.due(start, now)) return BlockStatus.MISSED
        if (Missed.windowOver(start, block, now)) return BlockStatus.ENDED
    }
    return BlockStatus.IDLE
}

fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun greeting(now: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}

fun dateLabel(now: Long): String =
    java.text.SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(java.util.Date(now))

/** 定时刷新的 now，倒计时用 1 秒，主页/历史用 5 秒就够 */
@Composable
fun rememberNow(intervalMs: Long = 1000L): Long {
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(intervalMs) {
        while (true) {
            state.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(intervalMs)
        }
    }
    return state.value
}
