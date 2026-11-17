package com.bilibili.pure.ui.player

/** 单张缩略图在雪碧图中的裁切区域。 */
data class TileSlice(
    val sheetIndex: Int,
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int
)

/**
 * 根据时间点找到对应的缩略图帧号：取最后一个时间 <= [sec] 的帧。
 * 早于首帧取 0，超过末帧取最后一帧；空列表返回 0。
 */
fun frameIndexFor(frameTimesSec: List<Int>, sec: Int): Int {
    if (frameTimesSec.isEmpty()) return 0
    var result = 0
    for (i in frameTimesSec.indices) {
        if (frameTimesSec[i] <= sec) result = i else break
    }
    return result
}

/**
 * 计算帧号在雪碧图中的裁切区域。
 *
 * 帧号按行优先排列，跨图时 [TileSlice.sheetIndex] 递增；
 * [bitmapWidth]/[bitmapHeight] 为实际解码后的图尺寸（可能被降采样），
 * 裁切区域按比例缩放并夹在图的边界内。
 */
fun tileSliceFor(
    frameIndex: Int,
    cols: Int,
    rows: Int,
    tileWidth: Int,
    tileHeight: Int,
    bitmapWidth: Int,
    bitmapHeight: Int
): TileSlice {
    val safeCols = cols.coerceAtLeast(1)
    val safeRows = rows.coerceAtLeast(1)
    val perSheet = safeCols * safeRows
    val safeFrame = frameIndex.coerceAtLeast(0)
    val sheetIndex = safeFrame / perSheet
    val inSheet = safeFrame % perSheet
    val col = inSheet % safeCols
    val row = inSheet / safeCols

    val scaleX = bitmapWidth.toFloat() / (safeCols * tileWidth).coerceAtLeast(1)
    val scaleY = bitmapHeight.toFloat() / (safeRows * tileHeight).coerceAtLeast(1)

    val left = (col * tileWidth * scaleX).toInt().coerceIn(0, (bitmapWidth - 1).coerceAtLeast(0))
    val top = (row * tileHeight * scaleY).toInt().coerceIn(0, (bitmapHeight - 1).coerceAtLeast(0))
    val width = (tileWidth * scaleX).toInt().coerceIn(1, (bitmapWidth - left).coerceAtLeast(1))
    val height = (tileHeight * scaleY).toInt().coerceIn(1, (bitmapHeight - top).coerceAtLeast(1))

    return TileSlice(sheetIndex, left, top, width, height)
}

/**
 * 预览卡片中心 x：按目标时间映射到进度条上的位置，并夹在容器内，
 * 保证卡片左右不被裁掉。[containerWidthPx] 小于卡片宽度时返回半宽。
 */
fun previewCenterXPx(
    targetMs: Long,
    durationMs: Long,
    barLeftPx: Int,
    barWidthPx: Int,
    previewWidthPx: Int,
    containerWidthPx: Int
): Int {
    val ratio = if (durationMs <= 0L) 0f else (targetMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val raw = barLeftPx + (barWidthPx * ratio).toInt()
    val half = previewWidthPx / 2
    val max = (containerWidthPx - half).coerceAtLeast(half)
    return raw.coerceIn(half, max)
}
