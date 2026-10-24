package org.mobilism.material.settings

import java.io.File
import java.time.Instant
import org.mobilism.material.data.DiagnosticEvent
import org.mobilism.material.data.DiagnosticLogger

class DiagnosticLogStore(private val directory: File, private val isEnabled: () -> Boolean,
    private val maxFileBytes: Long = 256 * 1024) : DiagnosticLogger {
    private val current get() = File(directory, "current.jsonl")
    private val previous get() = File(directory, "previous.jsonl")

    @Synchronized
    override fun record(event: DiagnosticEvent) {
        if (!isEnabled()) return
        runCatching {
            directory.mkdirs()
            val line = buildString {
                append("{\"time\":\"").append(Instant.now()).append("\"")
                append(",\"source\":\"").append(event.source.name).append("\"")
                append(",\"operation\":\"").append(event.operation.name).append("\"")
                append(",\"phase\":\"").append(event.phase.name).append("\"")
                event.requestId?.let { append(",\"requestId\":").append(it) }
                event.httpStatus?.let { append(",\"httpStatus\":").append(it) }
                event.elapsedMillis?.let { append(",\"elapsedMillis\":").append(it) }
                event.failure?.let { append(",\"failure\":\"").append(it.name).append("\"") }
                append("}\n")
            }
            if (current.length() + line.toByteArray(Charsets.UTF_8).size > maxFileBytes) {
                previous.delete()
                if (!current.renameTo(previous)) current.delete()
            }
            current.appendText(line, Charsets.UTF_8)
        }
    }

    @Synchronized
    fun snapshot(): String = listOf(previous, current).filter { it.isFile }
        .joinToString("") { it.readText(Charsets.UTF_8) }
}
