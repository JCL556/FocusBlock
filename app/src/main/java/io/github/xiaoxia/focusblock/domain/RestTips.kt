package io.github.xiaoxia.focusblock.domain

/**
 * 完成一个时间块之后的「去休息一下」提示。
 * 九句轮着播，不固定同一句，免得看多了当噪音。
 */
object RestTips {

    val ALL = listOf(
        "站起来倒杯水，顺便让眼睛离开屏幕一会儿",
        "伸个懒腰，肩膀前后各转两圈",
        "走到窗边看看远处，让眼睛歇一歇",
        "深呼吸三次，慢慢吐气，别憋着",
        "去走廊溜达两分钟，别一直坐着",
        "吃口东西垫一垫，别空着肚子硬撑",
        "揉揉脖子后面那块，别老低着头",
        "闭上眼睛发一分钟呆，什么都不用想",
        "活动下手腕和手指，它们也累了",
    )

    /** 第 index 句（超出范围自动绕回来） */
    fun of(index: Int): String = ALL[Math.floorMod(index, ALL.size)]

    /** 下一句的游标 */
    fun next(index: Int): Int = Math.floorMod(index + 1, ALL.size)
}
