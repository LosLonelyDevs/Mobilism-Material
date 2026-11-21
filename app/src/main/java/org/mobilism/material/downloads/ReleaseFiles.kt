package org.mobilism.material.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeHex
import okio.buffer
import okio.source
import org.mobilism.material.R

internal open class DownloadProblem(message: String?, cause: Throwable? = null) : IOException(message, cause)
internal class ArchiveProblem(val reason: Int) : DownloadProblem(null)

internal object ReleaseFiles {
    const val MAX_ENTRIES = 2048
    const val MAX_UNPACKED_BYTES = 8L * 1024 * 1024 * 1024
    private val zipSignature = "504b".decodeHex()
    private val rar4Signature = "526172211a0700".decodeHex()
    private val rar5Signature = "526172211a070100".decodeHex()
    private val sevenZipSignature = "377abcaf271c".decodeHex()
    private val directExtensions = setOf("apk", "apks", "xapk", "apkm", "zip", "rar", "7z",
        "epub", "pdf", "mobi", "azw", "azw3", "cbz", "cbr", "fb2", "mp3", "m4b")

    fun directName(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val name = parsed.pathSegments.last()
        return cleanName(name).takeIf { parsed.isHttps && name.substringAfterLast('.', "").lowercase(Locale.ROOT) in directExtensions }
    }

    fun name(url: String, disposition: String?, mime: String?, title: String): String {
        val extended = disposition?.let { Regex("""(?i)(?:^|;)\s*filename\*\s*=\s*UTF-8'[^']*'([^;]+)""").find(it) }
            ?.groupValues?.get(1)?.trim()?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
        val plain = disposition?.let { Regex("""(?i)(?:^|;)\s*filename\s*=\s*(?:"([^"]+)"|([^;]+))""").find(it) }
            ?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        val candidate = extended ?: plain ?: url.toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty()
        val name = cleanName(candidate.ifBlank { title })
        if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) in directExtensions) return name
        val suffix = when (mime) {
            "application/vnd.android.package-archive" -> "apk"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/vnd.rar", "application/x-rar", "application/x-rar-compressed" -> "rar"
            "application/x-7z-compressed" -> "7z"
            else -> null
        }
        return if (suffix != null) cleanName("$name.$suffix") else name
    }

    fun cleanName(value: String): String {
        val leaf = value.substringAfterLast('/').substringAfterLast('\\').trim().trim('.')
            .replace(Regex("""[\p{Cc}<>:"|?*]"""), "_")
        if (leaf.isBlank()) return "release.bin"
        if (leaf.length <= 160) return leaf
        val extension = leaf.substringAfterLast('.', "").takeIf { it.length in 1..12 }
        return if (extension == null) leaf.take(160) else leaf.take(159 - extension.length) + "." + extension
    }

    fun mime(name: String): String = when (val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    // APK, EPUB and several other release formats also have ZIP headers.
    fun publish(file: File, name: String, extract: Boolean, checkActive: () -> Unit,
        save: (String, InputStream) -> Unit) {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in directExtensions && extension !in setOf("zip", "rar", "7z")) {
            file.inputStream().use { save(name, it) }
            return
        }
        val format = file.source().buffer().use { source ->
            when {
                source.rangeEquals(0, rar4Signature) || source.rangeEquals(0, rar5Signature) -> "rar"
                source.rangeEquals(0, sevenZipSignature) -> "7z"
                source.rangeEquals(0, zipSignature) -> "zip"
                else -> null
            }
        }
        if (format == "rar" || format == "7z") {
            val archiveName = if (extension == format) name else name.substringBeforeLast('.', name) + "." + format
            if (extract) NativeArchive.extract(file, archiveName, checkActive, save)
            else file.inputStream().use { save(archiveName, it) }
            return
        }
        if (format != "zip") {
            if (extract && extension == "zip") throw ArchiveProblem(R.string.download_zip_invalid)
            if (extract && (extension == "rar" || extension == "7z")) throw UnreadableArchive(name)
            file.inputStream().use { save(name, it) }
            return
        }
        val archiveFile = try { ZipFile(file) } catch (invalid: java.util.zip.ZipException) {
            if (extract) throw invalid
            file.inputStream().use { save(name, it) }
            return
        }
        archiveFile.use { zip ->
            val packageFile = when {
                zip.getEntry("AndroidManifest.xml") != null -> "apk"
                zip.getEntry("META-INF/container.xml") != null && zip.getEntry("mimetype") != null -> "epub"
                else -> null
            }
            if (packageFile != null || !extract) {
                val suffix = packageFile ?: "zip"
                val actual = if (extension == suffix) name else name.substringBeforeLast('.', name) + "." + suffix
                file.inputStream().use { save(actual, it) }
                return
            }
            if (zip.size() > MAX_ENTRIES) throw ArchiveProblem(R.string.download_archive_too_many)
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            if (entries.isEmpty()) throw ArchiveProblem(R.string.download_archive_empty)
            var remaining = MAX_UNPACKED_BYTES
            for (entry in entries) {
                if (!safeEntry(entry.name) || entry.size !in 0..remaining)
                    throw ArchiveProblem(R.string.download_archive_unsafe)
                remaining -= entry.size
            }
            for (entry in entries) {
                checkActive()
                zip.getInputStream(entry).use { input ->
                    val checked = CheckedEntryStream(input, entry.size)
                    save(cleanName(entry.name), checked)
                    if (checked.count != entry.size || checked.checksum.value != entry.crc)
                        throw ArchiveProblem(R.string.download_archive_damaged)
                }
            }
        }
    }

    fun safeEntry(name: String): Boolean = name.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." } &&
        !(name.length >= 2 && name[0].isLetter() && name[1] == ':')
}

private class CheckedEntryStream(input: InputStream, private val size: Long) :
    java.util.zip.CheckedInputStream(input, java.util.zip.CRC32()) {
    var count = 0L
        private set
    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        val read = super.read(buffer, off, len)
        if (read > 0) {
            count += read
            if (count > size) throw ArchiveProblem(R.string.download_archive_damaged)
        }
        return read
    }
}

internal class ReleaseFolder(private val context: Context, val tree: Uri) {
    private val resolver = context.contentResolver
    private val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun requireWritable() {
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission && it.isReadPermission })
            throw DownloadProblem(context.getString(R.string.download_folder_access_lost))
        val flags = DocumentsContract.Document.COLUMN_FLAGS
        val writable = resolver.query(root, arrayOf(flags), null, null, null)?.use {
            it.moveToFirst() && (it.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
        } == true
        if (!writable) throw DownloadProblem(context.getString(R.string.download_writable_folder))
    }

    fun create(name: String): Uri = DocumentsContract.createDocument(resolver, root, ReleaseFiles.mime(name), name)
        ?: throw DownloadProblem(context.getString(R.string.download_file_create_failed))
}
