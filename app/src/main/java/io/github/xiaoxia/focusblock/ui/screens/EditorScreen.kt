package io.github.xiaoxia.focusblock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.Block
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.components.BlockIcon
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.GroupTitle
import io.github.xiaoxia.focusblock.ui.components.PrimaryButton
import io.github.xiaoxia.focusblock.ui.components.SwitchRow
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones
import java.util.UUID

@Composable
fun EditorScreen(vm: AppVm, data: AppData, blockId: String?) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val existing = remember(blockId, data.blocks) { data.blocks.find { it.id == blockId } }

    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var startMin by remember(existing?.id) { mutableIntStateOf(existing?.startMin ?: 8 * 60) }
    var endMin by remember(existing?.id) { mutableIntStateOf(existing?.endMin ?: 10 * 60) }
    var point by remember(existing?.id) { mutableStateOf(existing?.point ?: false) }
    var days by remember(existing?.id) { mutableStateOf(existing?.days ?: List(7) { true }) }
    var fullscreen by remember(existing?.id) { mutableStateOf(existing?.fullscreen ?: false) }
    var enabled by remember(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var iconKey by remember(existing?.id) {
        mutableStateOf(existing?.iconKey ?: Block.ICON_KEYS.random())
    }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 时间段必须落在同一天里：结束晚于开始，而且至少 5 分钟（跟「超时」宽限同长）。
    // 结束 == 开始 请用「时间点」，结束 < 开始 就是跨天，都不支持
    val sameTime = !point && endMin == startMin
    val crossDay = !point && endMin < startMin
    val tooShort = !point && !sameTime && !crossDay && (endMin - startMin) < Block.MIN_SPAN_MIN
    val valid = title.isNotBlank() && !sameTime && !crossDay && !tooShort && days.any { it }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickableNoRipple { vm.back() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = extra.text)
            }
            Text(
                if (existing == null) "新建作息" else "编辑作息",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = extra.text,
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .padding(horizontal = 8.dp)
                    .clickableNoRipple {
                        if (!valid) return@clickableNoRipple
                        vm.saveBlock(
                            Block(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                title = title.trim(),
                                startMin = startMin,
                                // 时间点不占时长，结束时间跟着开始时间
                                endMin = if (point) startMin else endMin,
                                days = days,
                                fullscreen = fullscreen,
                                enabled = enabled,
                                iconKey = iconKey,
                                point = point,
                                // 老块保留原来的创建时间，新块记下这一刻
                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "保存",
                    color = if (valid) extra.text else extra.textAux.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 任务名称
            FbCard {
                Column {
                    GroupTitle("任务名称")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(extra.soft, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        if (title.isEmpty()) {
                            Text("比如 晚自习 / 背单词", color = extra.textAux, style = MaterialTheme.typography.bodyLarge)
                        }
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it.take(24) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = extra.text),
                            cursorBrush = SolidColor(tones.focus),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }

            // 2. 时间：时间段 或 时间点
            FbCard {
                Column {
                    GroupTitle("时间")
                    // 二选一：一段连续时间，还是某个时刻提醒一次
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip("时间段", !point, Modifier.weight(1f)) {
                            point = false
                            // 从「时间点」切过来时结束时间还跟开始时间一样，
                            // 顺手补一段一小时，别让保存键一进来就是灰的
                            if (endMin - startMin < Block.MIN_SPAN_MIN) {
                                endMin = (startMin + 60).coerceAtMost(Block.MINUTES_PER_DAY - 1)
                            }
                        }
                        ModeChip("时间点", point, Modifier.weight(1f)) { point = true }
                    }
                    Spacer(Modifier.height(14.dp))

                    if (point) {
                        TimeField(
                            label = "时间点",
                            minutes = startMin,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { pickStart = true },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "到点提醒一次，不走进度环，主页上点勾即可打卡",
                            color = extra.textAux,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeField(
                                label = "开始时间",
                                minutes = startMin,
                                modifier = Modifier.weight(1f),
                                onClick = { pickStart = true },
                            )
                            TimeField(
                                label = "结束时间",
                                minutes = endMin,
                                modifier = Modifier.weight(1f),
                                onClick = { pickEnd = true },
                            )
                        }
                        if (sameTime) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "结束时间不能和开始时间一样，想设成某个时刻请切到「时间点」",
                                color = tones.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (crossDay) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "结束时间要晚于开始时间，不支持跨天。\n要跨过午夜请拆成两块，比如 22:30–23:59 和 00:00–06:30",
                                color = tones.error,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 17.sp,
                            )
                        } else if (tooShort) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "时间段至少 ${Block.MIN_SPAN_MIN} 分钟，现在只有 ${endMin - startMin} 分钟。\n只想记一个时刻请切到「时间点」",
                                color = tones.error,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 17.sp,
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "共 ${endMin - startMin} 分钟",
                                color = extra.textAux,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // 3. 重复
            FbCard {
                Column {
                    GroupTitle("重复")
                    // 4 + 3 折行，周一→周日顺序，不重不漏
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0..3) DayChip(i, days, Modifier.weight(1f)) { idx, value ->
                            days = days.toMutableList().also { it[idx] = value }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 4..6) DayChip(i, days, Modifier.weight(1f)) { idx, value ->
                            days = days.toMutableList().also { it[idx] = value }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // 4. 提醒
            FbCard {
                Column {
                    GroupTitle("提醒")
                    Text(
                        "到点即响",
                        color = extra.textAux,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))

                    SwitchRow(
                        label = "锁屏强提醒",
                        checked = fullscreen,
                        onChange = { fullscreen = it },
                    )
                }
            }

            // 5. 启用 + 图标
            FbCard {
                Column {
                    SwitchRow(label = "启用这个时间块", checked = enabled, onChange = { enabled = it })
                    Spacer(Modifier.height(8.dp))
                    GroupTitle("图标")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Block.ICON_KEYS.forEach { key ->
                            val selected = iconKey == key
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        if (selected) tones.focus.copy(alpha = 0.18f) else extra.soft,
                                        CircleShape
                                    )
                                    .then(
                                        if (selected) Modifier.border(2.dp, tones.focus, CircleShape) else Modifier
                                    )
                                    .clickableNoRipple { iconKey = key },
                                contentAlignment = Alignment.Center,
                            ) {
                                BlockIcon(key, 36.dp)
                            }
                        }
                    }
                }
            }

            if (existing != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickableNoRipple { confirmDelete = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = tones.error)
                    Spacer(Modifier.width(6.dp))
                    Text("删除这个时间块", color = tones.error, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickStart) {
        TimePickDialog(startMin, onDismiss = { pickStart = false }) {
            startMin = it
            // 挪动开始时间后结束时间跟不上了：往后顺推一小时（最多到 23:59）。
            // 推完还不足最短时长的（开始时间落在 23:55 之后），交给红字提示，不偷偷改刚选的时间
            if (!point && endMin - startMin < Block.MIN_SPAN_MIN) {
                endMin = (startMin + 60).coerceAtMost(Block.MINUTES_PER_DAY - 1)
            }
            pickStart = false
        }
    }
    if (pickEnd) {
        TimePickDialog(endMin, onDismiss = { pickEnd = false }) {
            endMin = it
            pickEnd = false
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${existing.title}」？") },
            text = { Text("删掉之后这个时间块的提醒会一起取消，历史记录会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteBlock(existing.id)
                }) { Text("删除", color = tones.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 时间段 / 时间点 二选一 */
@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (selected) tones.focus else extra.soft, RoundedCornerShape(20.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) tones.onTone else extra.textAux,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DayChip(
    index: Int,
    days: List<Boolean>,
    modifier: Modifier = Modifier,
    onToggle: (Int, Boolean) -> Unit,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val selected = days.getOrElse(index) { false }
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                if (selected) tones.focus else extra.soft,
                RoundedCornerShape(12.dp)
            )
            .clickableNoRipple { onToggle(index, !selected) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Block.WEEK_LABELS[index],
            color = if (selected) tones.onTone else extra.textAux,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val extra = LocalExtra.current
    Column(modifier = modifier) {
        Text(label, color = extra.textAux, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(extra.soft, RoundedCornerShape(14.dp))
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "%02d:%02d".format(minutes / 60, minutes % 60),
                color = extra.text,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
    )
}
