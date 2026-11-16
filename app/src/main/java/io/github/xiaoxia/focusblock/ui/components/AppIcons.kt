package io.github.xiaoxia.focusblock.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 历史记录用的时钟图标。
 * material-icons-extended 光图标就有 40MB dex，用到的几个自己画，包体能小一大截。
 */
val HistoryClockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HistoryClock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M12,3 A9,9 0 1,1 12,21 A9,9 0 1,1 12,3").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = PathParser().parsePathString("M12,7 L12,12.2 L15.6,14").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

/** 底部「今日」用的摊开的书本：左页 + 右页 + 书脊，跟日历区分开 */
val BookTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BookTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 左页
        addPath(
            pathData = PathParser()
                .parsePathString("M12,7 C10.2,5.6 7.6,5 5,5.4 L5,17.6 C7.6,17.2 10.2,17.8 12,19.2")
                .toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        // 右页
        addPath(
            pathData = PathParser()
                .parsePathString("M12,7 C13.8,5.6 16.4,5 19,5.4 L19,17.6 C16.4,17.2 13.8,17.8 12,19.2")
                .toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        // 书脊
        addPath(
            pathData = PathParser().parsePathString("M12,6.6 L12,19.4").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

/** 底部「日历」用的月历图标：外框 + 挂环 + 顶栏 + 三个小格点 */
val CalendarTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CalendarTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 外框（圆角矩形）
        addPath(
            pathData = PathParser()
                .parsePathString(
                    "M6,5 H18 A2.4,2.4 0 0 1 20.4,7.4 V18.6 A2.4,2.4 0 0 1 18,21 H6 A2.4,2.4 0 0 1 3.6,18.6 V7.4 A2.4,2.4 0 0 1 6,5 Z"
                )
                .toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        // 顶栏分隔线
        addPath(
            pathData = PathParser().parsePathString("M3.8,9.8 H20.2").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
        )
        // 两个挂环
        addPath(
            pathData = PathParser().parsePathString("M8,2.8 V6.2 M16,2.8 V6.2").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
        )
        // 三个格点
        addPath(
            pathData = PathParser()
                .parsePathString(
                    "M7.6,13.4 A1.05,1.05 0 1,1 7.6,15.5 A1.05,1.05 0 1,1 7.6,13.4 Z" +
                        "M11,13.4 A1.05,1.05 0 1,1 11,15.5 A1.05,1.05 0 1,1 11,13.4 Z" +
                        "M14.4,13.4 A1.05,1.05 0 1,1 14.4,15.5 A1.05,1.05 0 1,1 14.4,13.4 Z"
                )
                .toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/** 底部「自由专注」用的小番茄，实心，跟其他 tab 图标同重量 */
val TomatoTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TomatoTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser()
                .parsePathString("M12,8.4 A6.4,6.4 0 1,0 12,21.2 A6.4,6.4 0 1,0 12,8.4")
                .toNodes(),
            fill = SolidColor(Color.Black),
        )
        addPath(
            pathData = PathParser()
                .parsePathString("M12,9.2 L10.4,5.4 L12,6.4 L13.3,4 L13.8,6.6 L16.2,5.6 L14,9.2 Z")
                .toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
