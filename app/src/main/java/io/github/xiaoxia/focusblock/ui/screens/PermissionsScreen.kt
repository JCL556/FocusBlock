package io.github.xiaoxia.focusblock.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xiaoxia.focusblock.data.AppData
import io.github.xiaoxia.focusblock.domain.PermEntry
import io.github.xiaoxia.focusblock.domain.PermissionAudit
import io.github.xiaoxia.focusblock.ui.AppVm
import io.github.xiaoxia.focusblock.ui.components.FbCard
import io.github.xiaoxia.focusblock.ui.components.Mascot
import io.github.xiaoxia.focusblock.ui.components.MascotKind
import io.github.xiaoxia.focusblock.ui.components.clickableNoRipple
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones

@Composable
fun PermissionsScreen(vm: AppVm, data: AppData) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    val context = LocalContext.current

    // 从系统设置返回后自动重查：onResume 里把 refresh 加一
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val entries: List<PermEntry> = remember(refresh, data.settings) {
        PermissionAudit.entries(context, data.settings)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }

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
                "权限检查",
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
                Mascot(MascotKind.CONFUSED, 72.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "这些权限决定到点能不能弹出提醒，息屏也会亮。改完回来会自动刷新，不用退出去重进。",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.textAux,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.forEach { entry ->
                    PermCard(
                        entry = entry,
                        onOpen = {
                            if (entry.key == "notification" && PermissionAudit.needsRuntimeNotification(context)) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                entry.open(context)
                            }
                        },
                        onConfirm = {
                            vm.updateSettings { s ->
                                if (s.manualConfirmed.contains(entry.key)) s
                                else s.copy(manualConfirmed = s.manualConfirmed + entry.key)
                            }
                            refresh++
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "手动强行停止应用，到点就不会再提醒，这是系统限制。",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = extra.textAux,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermCard(
    entry: PermEntry,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current

    FbCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态：形状 + 颜色双通道
            if (entry.granted) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(tones.success, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "已开启", tint = tones.onTone, modifier = Modifier.size(17.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(2.dp, tones.warn, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", color = tones.warn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = extra.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.textAux,
                )
                if (entry.manual && !entry.granted) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(extra.soft, RoundedCornerShape(50))
                            .clickableNoRipple(onConfirm)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("我确认已开启", style = MaterialTheme.typography.labelSmall, color = extra.text)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = extra.textAux)
        }
    }
}
