package io.github.xiaoxia.focusblock.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.xiaoxia.focusblock.data.TimerPhase
import io.github.xiaoxia.focusblock.data.TimerState
import io.github.xiaoxia.focusblock.data.formatClock
import io.github.xiaoxia.focusblock.data.formatDurationMmSs
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.Tab
import io.github.xiaoxia.focusblock.ui.components.AutoFitText
import io.github.xiaoxia.focusblock.ui.components.PrimaryButton
import io.github.xiaoxia.focusblock.ui.components.SecondaryButton
import io.github.xiaoxia.focusblock.ui.components.TimerRing
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones
import io.github.xiaoxia.focusblock.ui.theme.TimerTextStyle
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(vm: AppVm, timer: TimerState?, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val now = rememberNow()

    // 只在界面真正可见时才驱动收尾：退到后台交给系统闹钟，
    // 两边不抢着记账，也不会出现「锁着屏却把番茄悄悄结了」
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(500L)
                vm.onTick()
            }
        }
    }

    if (timer == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("没有进行中的计时", color = extra.textAux)
                Spacer(Modifier.height(16.dp))
                SecondaryButton("回主页", { vm.backToMain() }, Modifier.width(160.dp))
            }
        }
        return
    }

    val remaining = timer.remainingMs(now)
    val progress = timer.progress(now)

    val paused = !timer.isRunning && !timer.notStarted
    val ringColor = when {
        paused -> tones.warn
        timer.phase == TimerPhase.SHORT_BREAK -> tones.shortBreak
        timer.phase == TimerPhase.LONG_BREAK -> tones.longBreak
        else -> tones.focus
    }

    val statusText = when {
        paused -> "已暂停"
        timer.notStarted -> "准备开始"
        timer.phase == TimerPhase.SHORT_BREAK -> "休息中"
        timer.phase == TimerPhase.LONG_BREAK -> "长休息中"
        else -> "正在专注"
    }

    val buttonText = when {
        timer.isRunning -> "暂停"
        timer.notStarted -> "开始"
        else -> "继续"
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                    .clickableNoRipple { vm.backToMain() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = extra.text)
            }
            Column(Modifier.weight(1f)) {
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = ringColor, fontWeight = FontWeight.Medium)
                Text(
                    timer.title.ifBlank { "自由专注" },
                    style = MaterialTheme.typography.titleMedium,
                    color = extra.text,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickableNoRipple { vm.selectTab(Tab.SETTINGS) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = extra.textAux, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // 圆环
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimerRing(progress = progress, color = ringColor) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AutoFitText(
                        text = formatDurationMmSs(remaining),
                        style = TimerTextStyle,
                        color = extra.text,
                        modifier = Modifier.width(214.dp),
                        maxFontSize = 80,
                        minFontSize = 38,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subLine(timer, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 信息条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(extra.soft, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                infoLine(timer),
                style = MaterialTheme.typography.bodySmall,
                color = extra.textAux,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        // 按钮区
        Column(Modifier.padding(horizontal = 20.dp)) {
            PrimaryButton(buttonText, { vm.toggleRun() })
            Spacer(Modifier.height(12.dp))
            SecondaryButton("重置", { vm.resetTimer() }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(
                "到点会响铃提醒，切后台、锁屏都不影响",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = extra.textAux,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun subLine(timer: TimerState, now: Long): String {
    return when {
        timer.phase != TimerPhase.FOCUS -> "休息一下"
        timer.blockId == null -> "第 ${timer.roundIndex.coerceIn(1, 4)} / 4 个番茄"
        timer.blockEndAt != null -> "预计 ${formatClock(timer.blockEndAt)} 结束"
        else -> "专注中"
    }
}

private fun infoLine(timer: TimerState): String {
    return if (timer.blockId == null) {
        "自由专注"
    } else if (timer.blockEndAt != null) {
        "来自作息：${timer.title} · 预计 ${formatClock(timer.blockEndAt)} 结束"
    } else {
        "来自作息：${timer.title}"
    }
}
