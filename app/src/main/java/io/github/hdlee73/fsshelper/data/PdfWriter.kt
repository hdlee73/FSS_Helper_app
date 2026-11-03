package io.github.hdlee73.fsshelper.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.hdlee73.everylaw.core.domain.HwpxParagraph
import java.io.OutputStream

/**
 * 한글 내보내기와 같은 문단·표 목록([HwpxParagraph])을 A4 PDF로 그린다(안드로이드 기본 PdfDocument).
 * 글꼴은 기기의 기본 한글 글꼴을 쓴다.
 */
object PdfWriter {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 50f

    fun write(title: String, paragraphs: List<HwpxParagraph>, out: OutputStream, footer: String = "") {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; color = Color.BLACK }
        val line = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.rgb(0xB0, 0xB0, 0xB0) }
        val fill = Paint().apply { style = Paint.Style.FILL; color = Color.rgb(0xE3, 0xE8, 0xF3) }
        fun width(s: String, size: Float): Float { paint.textSize = size; return paint.measureText(s) }
        fun wrap(text: String, size: Float, maxW: Float): List<String> {
            val res = mutableListOf<String>()
            text.split('\n').forEach { raw ->
                val l = raw.replace('\t', ' ')
                if (l.isEmpty()) { res += ""; return@forEach }
                var cur = StringBuilder()
                var lastSpace = -1
                for (ch in l) {
                    cur.append(ch)
                    if (ch == ' ') lastSpace = cur.length - 1
                    if (width(cur.toString(), size) > maxW && cur.length > 1) {
                        if (lastSpace > 0 && lastSpace > cur.length / 2) {
                            res += cur.substring(0, lastSpace)
                            cur = StringBuilder(cur.substring(lastSpace + 1))
                        } else {
                            res += cur.substring(0, cur.length - 1)
                            cur = StringBuilder(ch.toString())
                        }
                        lastSpace = -1
                    }
                }
                res += cur.toString()
            }
            return res
        }

        val contentW = W - MARGIN * 2
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var cv: Canvas = page.canvas
        var y = MARGIN
        fun textAt(s: String, x: Float, baseline: Float, size: Float, color: Int = Color.BLACK, bold: Boolean = false) {
            if (s.isEmpty()) return
            paint.textSize = size; paint.color = color; paint.isFakeBoldText = bold
            cv.drawText(s, x, baseline, paint)
            paint.isFakeBoldText = false
        }
        fun footerLine() { textAt("$footer   - $pageNo -".trim(), MARGIN, H - 28f, 8f, Color.GRAY) }
        fun newPage() {
            footerLine()
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            cv = page.canvas
            y = MARGIN
        }
        fun ensure(h: Float) { if (y + h > H - MARGIN - 10) newPage() }

        wrap(title, 16f, contentW).forEach { l -> ensure(24f); textAt(l, MARGIN, y + 16f, 16f, bold = true); y += 24f }
        y += 6f

        var i = 0
        while (i < paragraphs.size) {
            val p = paragraphs[i]
            if (p.cells != null) {
                val rows = mutableListOf<List<String>>()
                while (i < paragraphs.size && paragraphs[i].cells != null) { rows += paragraphs[i].cells!!; i++ }
                val cols = rows.maxOf { it.size }.coerceAtLeast(1)
                val size = 8.5f
                val lead = size * 1.35f
                val weights = (0 until cols).map { c -> rows.maxOf { r -> width(r.getOrNull(c).orEmpty(), size) }.coerceIn(30f, 220f) }
                val total = weights.sum()
                val widths = weights.map { it / total * contentW }
                rows.forEachIndexed { ri, r ->
                    val lines = (0 until cols).map { c -> wrap(r.getOrNull(c).orEmpty(), size, widths[c] - 6f) }
                    val h = lines.maxOf { it.size } * lead + 6f
                    ensure(h)
                    var x = MARGIN
                    (0 until cols).forEach { c ->
                        if (ri == 0) cv.drawRect(x, y, x + widths[c], y + h, fill)
                        cv.drawRect(x, y, x + widths[c], y + h, line)
                        lines[c].forEachIndexed { li, l -> textAt(l, x + 3f, y + 3f + size + li * lead, size) }
                        x += widths[c]
                    }
                    y += h
                }
                y += 6f
                continue
            }
            data class St(val size: Float, val color: Int, val gap: Float, val bold: Boolean = false)
            val st = when (p.style) {
                HwpxParagraph.Style.TITLE -> St(15f, Color.BLACK, 6f, true)
                HwpxParagraph.Style.SUBTITLE -> St(11f, Color.DKGRAY, 2f)
                HwpxParagraph.Style.SECTION -> St(12f, Color.rgb(0x1B, 0x2B, 0x4B), 8f, true)
                HwpxParagraph.Style.BOLD -> St(10.5f, Color.BLACK, 3f, true)
                HwpxParagraph.Style.META, HwpxParagraph.Style.GRAY -> St(8.5f, Color.GRAY, 0f)
                HwpxParagraph.Style.BODY -> St(10f, Color.BLACK, 0f)
            }
            val lead = st.size * 1.45f
            val indent = p.depth * 12f
            y += st.gap
            wrap(p.text, st.size, contentW - indent).forEach { l -> ensure(lead); textAt(l, MARGIN + indent, y + st.size, st.size, st.color, st.bold); y += lead }
            i++
        }
        footerLine()
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
