package org.mobilism.material.downloads

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException

internal class UnreadableArchive(val archiveName: String, cause: Throwable? = null) : IOException(cause)

internal object NativeArchive {
    fun extract(file: File, archiveName: String, checkActive: () -> Unit, save: (String, InputStream) -> Unit) {
        try {
            val reader = Archive.readNew()
            try {
                Archive.readSupportFormat7zip(reader)
                Archive.readSupportFormatRar(reader)
                Archive.readSupportFormatRar5(reader)
                Archive.readOpenFileName(reader, file.absolutePath.toByteArray(), 64L * 1024)
                var entries = 0
                var files = 0
                var remaining = ReleaseFiles.MAX_UNPACKED_BYTES
                while (true) {
                    checkActive()
                    val entry = Archive.readNextHeader(reader)
                    if (entry == 0L) break
                    if (++entries > ReleaseFiles.MAX_ENTRIES || ArchiveEntry.isEncrypted(entry))
                        throw UnreadableArchive(archiveName)
                    if (ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFDIR) continue
                    val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.toString(Charsets.UTF_8)
                    val size = ArchiveEntry.size(entry)
                    if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG || ArchiveEntry.hardlinkIsSet(entry) ||
                        name == null || !ReleaseFiles.safeEntry(name) || !ArchiveEntry.sizeIsSet(entry) || size !in 0..remaining)
                        throw UnreadableArchive(archiveName)
                    val stream = EntryStream(reader, size, archiveName)
                    save(ReleaseFiles.cleanName(name), stream)
                    if (stream.remaining != 0L) throw UnreadableArchive(archiveName)
                    remaining -= size
                    files++
                }
                if (files == 0) throw UnreadableArchive(archiveName)
            } finally {
                Archive.free(reader)
            }
        } catch (failure: ArchiveException) {
            throw UnreadableArchive(archiveName, failure)
        }
    }

    private class EntryStream(val reader: Long, size: Long, val archiveName: String) : InputStream() {
        var remaining = size
            private set

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val target = ByteBuffer.wrap(buffer, off, len)
            Archive.readData(reader, target)
            val count = target.position() - off
            if (count > remaining || (count == 0 && remaining > 0)) throw UnreadableArchive(archiveName)
            remaining -= count
            return if (count == 0) -1 else count
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }
    }
}
