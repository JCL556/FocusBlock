package io.github.xiaoxia.focusblock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.ThemeMode
import io.github.xiaoxia.focusblock.ui.components.Mascot
import io.github.xiaoxia.focusblock.ui.components.MascotKind
import io.github.xiaoxia.focusblock.ui.components.PrimaryButton
import io.github.xiaoxia.focusblock.ui.components.SecondaryButton
import io.github.xiaoxia.focusblock.ui.screens.EditorScreen
import io.github.xiaoxia.focusblock.ui.screens.FreeFocusScreen
import io.github.xiaoxia.focusblock.ui.screens.GuideScreen
import io.github.xiaoxia.focusblock.ui.screens.HistoryScreen
import io.github.xiaoxia.focusblock.ui.screens.PermissionsScreen
import io.github.xiaoxia.focusblock.ui.screens.SettingsScreen
import io.github.xiaoxia.focusblock.ui.screens.TimerScreen
import io.github.xiaoxia.focusblock.ui.screens.TodayScreen
import io.github.xiaoxia.focusblock.ui.theme.FocusBlockTheme
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones
import kotlinx.coroutines.delay

@Composable
fun FocusBlockApp(
    vm: AppVm,
    /** 存档读出来之前用的主题，避免深色用户开屏先白一下 */
    initialTheme: ThemeMode = ThemeMode.SYSTEM,
    openTimer: Boolean = false,
    onTimerOpened: () -> Unit = {},
) {
    // null = 存档还没读出来。数据和「读没读完」是同一个值，不会错开一帧
    val snapshot = vm.loadedData.collectAsStateWithLifecycle().value

    // 冷启动读盘要几十到几百毫秒，这段空窗期只铺一层底色加个小番茄，
    // 以前这里是拿空数据直接画，于是先闪一下「还没有作息计划 / 0/0 个时间块」
    if (snapshot == null) {
        FocusBlockTheme(mode = initialTheme) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalExtra.current.pageBg),
                contentAlignment = Alignment.Center,
            ) {
                Mascot(MascotKind.FOCUS, 88.dp)
            }
        }
        return
    }
    val data: AppData = snapshot

    FocusBlockTheme(mode = data.settings.themeMode) {
        val extra = LocalExtra.current

        Box(
            Modifier
                .fillMaxSize()
                .background(extra.pageBg)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding()
            ) {
                when (val route = vm.route) {
                    is Route.Main -> when (route.tab) {
                        Tab.TODAY -> TodayScreen(vm, data)
                        Tab.FOCUS -> FreeFocusScreen(vm, data)
                        Tab.HISTORY -> HistoryScreen(vm, data)
                        Tab.SETTINGS -> SettingsScreen(vm, data)
                    }

                    Route.Timer -> TimerScreen(vm, data.timer)
                    Route.Permissions -> PermissionsScreen(vm, data)
                    Route.Guide -> GuideScreen(vm)
                    is Route.Editor -> EditorScreen(vm, data, route.blockId)
                }
            }

            // 轻提示
            val msg = vm.message
            if (msg != null) {
                LaunchedEffect(msg) {
                    delay(1800L)
                    vm.consumeMessage()
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = extra.text.copy(alpha = 0.9f),
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = extra.card,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        BackHandler(enabled = vm.route !is Route.Main) { vm.back() }

        vm.celebration?.let { c ->
            CelebrationDialog(
                title = if (c.blockId == null) "第 ${c.roundIndex.coerceIn(1, 4)} 个番茄完成" else "${c.title} 完成",
                tip = c.tip,
                onBreak = { vm.startBreakFromCelebration() },
                onDone = { vm.finishCelebration() },
            )
        }

        LaunchedEffect(openTimer) {
            if (openTimer) {
                vm.openActiveTimer()
                onTimerOpened()
            }
        }
    }
}

@Composable
private fun CelebrationDialog(
    title: String,
    tip: String?,
    onBreak: () -> Unit,
    onDone: () -> Unit,
) {
    val extra = LocalExtra.current
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(32.dp), color = extra.card) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Mascot(MascotKind.CELEBRATE, 104.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = extra.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                if (tip != null) {
                    // 作息块做完：只提醒去休息，不给「开始休息」（作息不跑番茄）
                    Text(
                        "去休息一下吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("知道了", onDone)
                } else {
                    Text(
                        "辛苦了，要不要休息一下",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.textAux,
                    )
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("开始休息", onBreak)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("完成", onDone, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
