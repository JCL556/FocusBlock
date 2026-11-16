package io.github.xiaoxia.focusblock.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xiaoxia.focusblock.BuildConfig
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.data.ThemeMode
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.Route
import io.github.xiaoxia.focusblock.ui.Tab
import io.github.xiaoxia.focusblock.ui.components.BottomTabs
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.GroupTitle
import io.github.xiaoxia.focusblock.ui.components.SettingRow
import io.github.xiaoxia.focusblock.ui.components.Stepper
import io.github.xiaoxia.focusblock.ui.components.SwitchRow
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones

@Composable
fun SettingsScreen(vm: AppVm, data: AppData) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val context = LocalContext.current
    val settings = data.settings

    val ringtoneName = remember(settings.ringtoneUri) {
        settings.ringtoneUri?.let { uri ->
            runCatching { RingtoneManager.getRingtone(context, android.net.Uri.parse(uri))?.getTitle(context) }.getOrNull()
        } ?: "默认提示音"
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            vm.updateSettings { it.copy(ringtoneUri = uri?.toString()) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("设置", style = MaterialTheme.typography.titleLarge, color = extra.text)
            Spacer(Modifier.height(16.dp))

            // 1 番茄钟
            FbCard {
                Column {
                    GroupTitle("番茄钟")
                    SettingRow("专注时长", trailing = {
                        Stepper(settings.focusLen, { v ->
                            vm.updateSettings { s -> s.copy(focusLen = v) }
                        }, 5..90)
                    })
                    SettingRow("短休息", trailing = {
                        Stepper(settings.shortBreakLen, { v ->
                            vm.updateSettings { s -> s.copy(shortBreakLen = v) }
                        }, 1..30)
                    })
                    SettingRow("长休息", trailing = {
                        Stepper(settings.longBreakLen, { v ->
                            vm.updateSettings { s -> s.copy(longBreakLen = v) }
                        }, 5..60)
                    })
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2 提醒
            FbCard {
                Column {
                    GroupTitle("提醒")
                    SwitchRow("震动", settings.vibrate, { vm.updateSettings { s -> s.copy(vibrate = it) } })
                    SwitchRow("遵守勿扰模式", settings.respectDnd, { vm.updateSettings { s -> s.copy(respectDnd = it) } })
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickableNoRipple {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        settings.ringtoneUri?.let { android.net.Uri.parse(it) }
                                    )
                                }
                                ringtoneLauncher.launch(intent)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("通知铃声", color = extra.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(ringtoneName, color = extra.textAux, style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = extra.textAux)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3 外观
            FbCard {
                Column {
                    GroupTitle("外观")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.values().forEach { mode ->
                            val selected = settings.themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .background(
                                        if (selected) tones.focus else extra.soft,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickableNoRipple {
                                        vm.updateSettings { it.copy(themeMode = mode) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    when (mode) {
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                        ThemeMode.SYSTEM -> "跟随系统"
                                    },
                                    color = if (selected) tones.onTone else extra.textAux,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4 更多
            FbCard {
                Column {
                    GroupTitle("更多")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickableNoRipple { vm.navTo(Route.Permissions) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("权限自检", color = extra.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text("检查弹窗能不能响", color = extra.textAux, style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = extra.textAux)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickableNoRipple { vm.navTo(Route.Guide) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("使用说明", color = extra.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text("软件怎么用", color = extra.textAux, style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = extra.textAux)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "FocusBlock v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = extra.textAux,
            )
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().background(extra.card.copy(alpha = 0.98f))) {
            BottomTabs(current = Tab.SETTINGS, onSelect = { vm.selectTab(it) })
        }
    }
}
