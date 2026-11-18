package org.mobilism.material.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.roundToInt

internal class ForumBulletSpan(density: Float) : LeadingMarginSpan {
    private val margin = (20 * density).roundToInt()
    private val radius = 2 * density
    override fun getLeadingMargin(first: Boolean): Int = margin
    override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        if ((text as? Spanned)?.getSpanStart(this) != start) return
        val style = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x + dir * radius, (top + bottom) / 2f, radius, paint)
        paint.style = style
    }
}

internal class ForumQuoteSpan(density: Float, private val color: Int) : LeadingMarginSpan {
    private val margin = (16 * density).roundToInt()
    private val stripe = 3 * density
    override fun getLeadingMargin(first: Boolean): Int = margin
    override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        val previousColor = paint.color
        val style = paint.style
        paint.color = color
        paint.style = Paint.Style.FILL
        val edge = x + dir * stripe
        canvas.drawRect(minOf(x.toFloat(), edge), top.toFloat(), maxOf(x.toFloat(), edge), bottom.toFloat(), paint)
        paint.color = previousColor
        paint.style = style
    }
}
