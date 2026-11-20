package org.mobilism.material.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
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

internal class ForumQuoteSpan(density: Float, private val color: Int, insetDp: Int = 0) : LeadingMarginSpan {
    private val inset = insetDp * density
    private val margin = ((insetDp + 16) * density).roundToInt()
    private val stripe = 3 * density
    override fun getLeadingMargin(first: Boolean): Int = margin
    override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        val previousColor = paint.color
        val style = paint.style
        paint.color = color
        paint.style = Paint.Style.FILL
        val startX = x + dir * inset
        val endX = startX + dir * stripe
        canvas.drawRect(minOf(startX, endX), top.toFloat(), maxOf(startX, endX), bottom.toFloat(), paint)
        paint.color = previousColor
        paint.style = style
    }
}

internal fun SpannableStringBuilder.styleMessageQuotes(density: Float, guide: Int, color: Int, sizePx: Int) {
    getSpans(0, length, QuoteSpan::class.java).forEach { span ->
        val start = getSpanStart(span)
        var end = getSpanEnd(span)
        val flags = getSpanFlags(span)
        removeSpan(span)
        while (end > start && this[end - 1] == '\n') end--
        if (end <= start) return@forEach
        setSpan(ForumQuoteSpan(density, guide, insetDp = 6), start, if (end < length && this[end] == '\n') end + 1 else end, flags)
        setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(AbsoluteSizeSpan(sizePx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
