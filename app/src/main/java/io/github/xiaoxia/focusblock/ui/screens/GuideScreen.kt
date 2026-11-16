package io.github.xiaoxia.focusblock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.Mascot
import io.github.xiaoxia.focusblock.ui.components.MascotKind
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones

/**
 * 使用说明。给第一次用的人看：怎么建块、怎么打卡、标签什么意思。
 * 一屏看完，配几张迷你示意图，不写长文。
 */
@Composable
fun GuideScreen(vm: AppVm) {
    val extra = LocalExtra.current
    val tones = LocalTones.current

    Column(Modifier.fillMaxSize()) {
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
                "使用说明",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = extra.text,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotKind.FOCUS, 64.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "FocusBlock 管两件事：定好每天的作息，到点提醒你去做什么；番茄钟管临时想专注的那一段。\n" +
                        "下面按顺序看一遍，就会用了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.textAux,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Section(
                "一、建一个时间块",
                "打开「今日」页，滑到最下面点「新增时间块」。\n" +
                    "填个名字（比如 学习软件），选「时间段」或「时间点」，挑好重复的日子，点右上角保存。\n\n" +
                    "时间段管一段：08:00–12:30。时间点只管一刻：07:20 起床。",
            ) {
                MiniCard("学习软件", "08:00 – 12:30", chip = "时间段")
                Spacer(Modifier.height(8.dp))
                MiniCard("起床", "07:20", chip = "时间点")
            }

            Spacer(Modifier.height(12.dp))

            Section(
                "二、到点怎么打卡",
                "时间段：到开始时间点一下红按钮，卡片变红框、标题后写「进行中」；到结束时间再点一下，这一场就算完成。\n\n" +
                    "忙忘了也没事：到了设定的结束时刻，卡片会自己挂上「已结束」，回头补点一下勾，照样记成完成。\n\n" +
                    "时间点：到点点一下「完成」，一步到底。\n\n" +
                    "日程不跑倒计时，只记「点过没点过」。",
            ) {
                MiniCard("学习软件", "13:30 – 17:30", tag = "进行中" to tones.focus, button = MiniButton.CHECK, highlight = true)
            }

            Spacer(Modifier.height(12.dp))

            Section(
                "三、没到时间点不了",
                "没到设定时间，按钮是一把灰锁，点了只会提示「还没到」，防止手滑提前打卡。\n\n" +
                    "到点，锁自动换成按钮。时间段的锁看开始时刻，时间点看设定那一刻。",
            ) {
                MiniCard("学习软件", "18:30 – 20:30", button = MiniButton.LOCKED)
                Spacer(Modifier.height(8.dp))
                MiniCard("选型学习", "21:00", chip = "时间点", button = MiniButton.LOCKED)
            }

            Spacer(Modifier.height(12.dp))

            Section(
                "四、设时间的三条规矩",
                "① 结束时间必须晚于开始时间，一块不跨天。\n" +
                    "要跨过午夜，拆成两块：22:30–23:59 放一头，00:00–06:30 放另一头。\n\n" +
                    "② 时间段最短 5 分钟。更短的一闪而过，提醒还没响完就过去了。\n\n" +
                    "③ 只想记一个时刻（起床、吃药、开个会），选「时间点」，别用时间段。",
            )

            Spacer(Modifier.height(12.dp))

            Section(
                "五、卡片上的小标",
                "标题后面按情况挂小标：\n" +
                    "· 已结束 —— 到了设定的结束时刻就挂（时间点看设定那一刻），做没做都挂\n" +
                    "· 超时 —— 到点满 5 分钟还没点，才补这一条\n" +
                    "· 时间点 —— 这一块是时间点，不是时间段\n\n" +
                    "「已结束」和「超时」可以同时在，卡片照样亮着，随时能补做。",
            ) {
                MiniCard("学习软件", "08:00 – 12:30", chip = "已结束", extraChip = "超时" to tones.error, button = MiniButton.CHECK)
            }

            Spacer(Modifier.height(12.dp))

            Section(
                "六、什么时候变灰",
                "只有真点完（完成 / 结束）才变灰。\n\n" +
                    "超时、已结束都不会变灰——事情还没做，就亮着提醒你补。",
            )

            Spacer(Modifier.height(12.dp))

            Section(
                "七、日历怎么打勾",
                "一天里所有的时间块都完成了，而且一个「超时」都没有，那天才打勾；少一个都不打。\n\n" +
                    "点某一天，下面会列出那天完成的和超时的，按时间排。",
            ) {
                WeekDemo()
            }

            Spacer(Modifier.height(12.dp))

            Section(
                "八、番茄钟",
                "底部第二个 Tab「自由专注」。点「开始专注 25 分钟」进计时页，再点「开始」就倒计时；中途能暂停、重置。\n\n" +
                    "一轮做完会问要不要接着休息，短休 5 分钟、长休 15 分钟。\n\n" +
                    "到点会响铃提醒，切后台、锁屏、把 App 划掉都不影响。\n\n" +
                    "专注、短休、长休的时长在设置里改。",
            )

            Spacer(Modifier.height(12.dp))

            Section(
                "九、提醒和权限",
                "到点即响，就是设定那一刻响一次。\n\n" +
                    "铃声、震动、勿扰都在设置页统一改，所有块共用一套；开了「锁屏强提醒」的块，到点会全屏亮起来。\n\n" +
                    "通知上直接能操作：时间段给「开始」「结束」，时间点给「完成」，不用进 App。\n\n" +
                    "要是到了点却不响，去设置 →「权限自检」看一眼，缺哪个补哪个。",
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "看完就可以回去建第一个时间块了。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = extra.textAux,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------------------------------------------ 小组件 */

@Composable
private fun Section(
    title: String,
    body: String,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val extra = LocalExtra.current
    FbCard {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = extra.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = extra.textAux,
                lineHeight = 19.sp,
            )
            if (content != null) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

private enum class MiniButton { PLAY, CHECK, LOCKED }

@Composable
private fun MiniCard(
    title: String,
    time: String,
    chip: String? = null,
    extraChip: Pair<String, Color>? = null,
    tag: Pair<String, Color>? = null,
    button: MiniButton = MiniButton.PLAY,
    highlight: Boolean = false,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extra.card, RoundedCornerShape(14.dp))
            .border(
                width = if (highlight) 1.5.dp else 1.dp,
                color = if (highlight) tones.focus else extra.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(tones.focus.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(tones.focus, CircleShape)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = extra.text)
                if (chip != null) {
                    Spacer(Modifier.width(5.dp))
                    MiniChip(chip, extra.textAux)
                }
                if (extraChip != null) {
                    Spacer(Modifier.width(5.dp))
                    MiniChip(extraChip.first, extraChip.second)
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(time, style = MaterialTheme.typography.bodySmall, color = extra.textAux)
        }
        if (tag != null) {
            Box(
                Modifier
                    .background(tag.second.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(tag.first, style = MaterialTheme.typography.labelSmall, color = tag.second)
            }
            Spacer(Modifier.width(8.dp))
        }
        if (button != MiniButton.LOCKED) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (button == MiniButton.PLAY) tones.focus else tones.success,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (button == MiniButton.PLAY) Icons.Filled.PlayArrow else Icons.Filled.Check,
                    contentDescription = null,
                    tint = tones.onTone,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(extra.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = extra.textAux,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    val extra = LocalExtra.current
    Box(
        Modifier
            .background(extra.soft, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** 一周小日历：勾三个、空两个，示意打勾规则 */
@Composable
private fun WeekDemo() {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val days = listOf("9" to true, "10" to true, "11" to false, "12" to true, "13" to false, "14" to true, "15" to false)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extra.card, RoundedCornerShape(14.dp))
            .border(1.dp, extra.outline, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        days.forEach { (day, done) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day, style = MaterialTheme.typography.bodySmall, color = extra.text)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = tones.mark,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
