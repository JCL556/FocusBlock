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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.TimerPhase
import io.github.xiaoxia.focusblock.data.formatDurationMmSs
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.Tab
import io.github.xiaoxia.focusblock.ui.components.AutoFitText
import io.github.xiaoxia.focusblock.ui.components.BottomTabs
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.Mascot
import io.github.xiaoxia.focusblock.ui.components.MascotKind
import io.github.xiaoxia.focusblock.ui.components.PrimaryButton
import io.github.xiaoxia.focusblock.ui.components.SecondaryButton
import io.github.xiaoxia.focusblock.ui.components.TimerRing
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones
import io.github.xiaoxia.focusblock.ui.theme.TimerTextStyle

/**
 * 「自由专注」独立成一个 tab：不绑作息，按 25/5 的番茄节奏自己走。
 */
@Composable
fun FreeFocusScreen(vm: AppVm, data: AppData) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val now = rememberNow(5000L)
    val settings = data.settings

    val timer = data.timer
    val freeRunning = timer != null && timer.blockId == null
    val round = vm.currentFreeRound(now)
    val doneToday = vm.todayFreeDoneCount(now)
    val plannedMin = if (freeRunning) ((timer.plannedMs / 60_000L)).toInt() else settings.focusLen
    val ringProgress = if (timer != null && timer.blockId == null && timer.phase == TimerPhase.FOCUS) {
        timer.progress(now)
    } else {
        0f
    }

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
                    Text("自由专注", style = MaterialTheme.typography.titleLarge, color = extra.text)
                    Text(
                        "不绑作息，随时开始的一轮番茄",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                }
                Mascot(MascotKind.FOCUS, 44.dp)
            }

            Spacer(Modifier.height(20.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimerRing(progress = ringProgress, color = tones.focus, diameter = 232, strokeWidth = 14) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AutoFitText(
                            text = formatDurationMmSs(plannedMin * 60_000L),
                            style = TimerTextStyle,
                            color = extra.text,
                            modifier = Modifier.width(188.dp),
                            maxFontSize = 72,
                            minFontSize = 34,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (freeRunning) "进行中的一轮" else "第 ${round.coerceIn(1, 4)} / 4 个番茄",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.textAux,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 今天的四个番茄位，完成几个亮几个
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { i ->
                    val lit = i < (doneToday % 4).let { if (it == 0 && doneToday > 0) 4 else it }
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                            .background(if (lit) tones.focus else extra.outline, CircleShape)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (doneToday > 0) "今天已完成 $doneToday 个" else "今天还没开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.textAux,
                )
            }

            Spacer(Modifier.height(20.dp))

            FbCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoCell("专注", "${settings.focusLen} 分", Modifier.weight(1f))
                    InfoCell("短休", "${settings.shortBreakLen} 分", Modifier.weight(1f))
                    InfoCell("长休", "${settings.longBreakLen} 分", Modifier.weight(1f))
                    InfoCell("已完成", "$doneToday", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))

            if (freeRunning) {
                PrimaryButton("回到计时", { vm.openActiveTimer() })
            } else {
                // 作息块不再占计时位，这里只可能被自由专注自己的计时挡住
                PrimaryButton(
                    text = "开始专注 ${settings.focusLen} 分钟",
                    onClick = { vm.startFreeFocus() },
                    enabled = timer == null,
                )
            }

            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "去设置里调时长",
                onClick = { vm.selectTab(Tab.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().background(extra.card.copy(alpha = 0.98f))) {
            BottomTabs(current = Tab.FOCUS, onSelect = { vm.selectTab(it) })
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {    val extra = LocalExtra.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = extra.text, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = extra.textAux)
    }
}
