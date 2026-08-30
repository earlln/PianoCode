package com.earlln.pianocode.sheet

/**
 * The colours a converted chord can be written in.
 *
 * Each one is dark enough to read on white or cream paper and to survive a photocopy, and
 * far enough from the others that whichever the page's own ink resembles, another still
 * stands out. [SheetRenderer.pickMarkingColor] chooses one; the user can override it.
 */
enum class MarkingColor(val argb: Int, val koreanName: String) {
    VIOLET(0xFF5B3FD6.toInt(), "보라색"),
    CRIMSON(0xFFD32F2F.toInt(), "빨간색"),
    BLUE(0xFF1565C0.toInt(), "파란색"),
    GREEN(0xFF2E7D32.toInt(), "초록색"),
    ORANGE(0xFFE65100.toInt(), "주황색"),
}
