package io.github.xiaoxia.focusblock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xiaoxia.focusblock.ui.theme.LocalExtra
import io.github.xiaoxia.focusblock.ui.theme.LocalTones

/** 卡片：24dp 圆角、16dp 内边距 */
@Composable
fun FbCard(
    modifier: Modifier = Modifier,
    corner: Int = 24,
    padding: Int = 16,
    borderColor: Color? = null,
    borderWidth: Int = 2,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val extra = LocalExtra.current
    var m = modifier
        .fillMaxWidth()
        .background(extra.card, RoundedCornerShape(corner.dp))
    if (borderColor != null) {
        m = m.border(BorderStroke(borderWidth.dp, borderColor), RoundedCornerShape(corner.dp))
    }
    if (onClick != null) {
        m = m.clickable(onClick = onClick)
    }
    Box(modifier = m.padding(padding.dp)) {
        content()
    }
}

/** 主按钮：满宽、56dp、胶囊 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    val tones = LocalTones.current
    val bg = if (enabled) tones.primaryButton else tones.primaryButton.copy(alpha = 0.30f)
    val fg = if (enabled) tones.onPrimaryButton else tones.onPrimaryButton.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(bg, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 次要按钮：48dp、1.5dp 描边 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    Box(
        modifier = modifier
            .height(48.dp)
            .border(BorderStroke(1.5.dp, extra.outline), RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) extra.text else extra.textAux.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 分组卡片的标题 */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = extra.textAux,
        fontWeight = FontWeight.Medium,
    )
}

/** 一行「左边标签 / 右边控件」 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val extra = LocalExtra.current
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = extra.text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            if (sub != null) {
                Text(sub, color = extra.textAux, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        trailing?.invoke()
    }
}

/** 开关行 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = LocalTones.current
    val extra = LocalExtra.current
    SettingRow(label = label, modifier = modifier) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = tones.focus,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = extra.outline,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** 加减器 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    suffix: String = "分钟",
    step: Int = 1,
) {
    val extra = LocalExtra.current
    val tones = LocalTones.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled = value - step >= range.first) { onChange((value - step).coerceIn(range)) }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(
                "$value $suffix",
                color = extra.text,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        StepButton("+", enabled = value + step <= range.last) { onChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(extra.soft, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) extra.text else extra.textAux.copy(alpha = 0.5f),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
    }
}

/** 状态标签 */
@Composable
fun StatusTag(text: String, color: Color, modifier: Modifier = Modifier) {
    val tones = LocalTones.current
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = tones.onTone, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

/** 空状态 */
@Composable
fun EmptyState(
    kind: MascotKind,
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val extra = LocalExtra.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(kind, 120.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = extra.textAux,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.width(200.dp)) {
                PrimaryButton(actionText, onAction)
            }
        }
    }
}

/** 全局内容左右边距 */
val PagePadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

/** 无涟漪点击，卡片里的小按钮用 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.composed {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
