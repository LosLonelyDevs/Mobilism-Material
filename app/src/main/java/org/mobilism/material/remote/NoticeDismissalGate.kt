package org.mobilism.material.remote

internal class NoticeDismissalGate(private val now: () -> Long) {
    private var renderedAt: Long? = null
    fun rendered() { if (renderedAt == null) renderedAt = now() }
    fun pause() { if (remainingSeconds > 0) renderedAt = null }
    val remainingSeconds: Int get() {
        val start = renderedAt ?: return 3
        return ((3000 - (now() - start).coerceAtLeast(0)).coerceAtLeast(0) + 999).div(1000).toInt()
    }
    fun canDismiss(required: Boolean) = !required && remainingSeconds == 0
}
