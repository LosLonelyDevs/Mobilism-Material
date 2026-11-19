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

internal class DownloadProblem(message: String) : IOException(message)

internal object ReleaseFiles {
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
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    // APK, EPUB and several other release formats also have ZIP headers.
    fun publish(file: File, name: String, extract: Boolean, checkActive: () -> Unit,
        save: (String, InputStream) -> Unit) {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val typedFile = extension in directExtensions && extension != "zip"
        val archive = !typedFile && file.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!archive) {
            if (extension == "zip" && extract) throw DownloadProblem("Invalid ZIP. Turn off extraction to save the original file.")
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
            if (zip.size() > 2048) throw DownloadProblem("Too many ZIP entries. Turn off extraction to save the archive.")
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            if (entries.isEmpty()) throw DownloadProblem("The ZIP contains no files.")
            var remaining = 8L * 1024 * 1024 * 1024
            for (entry in entries) {
                val parts = entry.name.replace('\\', '/').split('/')
                if (parts.any { it.isBlank() || it == "." || it == ".." } || entry.size !in 0..remaining)
                    throw DownloadProblem("This ZIP cannot be extracted safely. Turn off extraction to save it.")
                remaining -= entry.size
            }
            for (entry in entries) {
                checkActive()
                zip.getInputStream(entry).use { input ->
                    val checked = CheckedEntryStream(input, entry.size)
                    save(cleanName(entry.name), checked)
                    if (checked.count != entry.size || checked.checksum.value != entry.crc)
                        throw DownloadProblem("The ZIP is incomplete or damaged.")
                }
            }
        }
    }
}

private class CheckedEntryStream(input: InputStream, private val size: Long) :
    java.util.zip.CheckedInputStream(input, java.util.zip.CRC32()) {
    var count = 0L
        private set
    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        val read = super.read(buffer, off, len)
        if (read > 0) {
            count += read
            if (count > size) throw DownloadProblem("The ZIP contains an invalid file size.")
        }
        return read
    }
}

internal class ReleaseFolder(private val context: Context, val tree: Uri) {
    private val resolver = context.contentResolver
    private val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun requireWritable() {
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission && it.isReadPermission })
            throw DownloadProblem("Folder access was lost. Choose a folder in Downloads settings.")
        val flags = DocumentsContract.Document.COLUMN_FLAGS
        val writable = resolver.query(root, arrayOf(flags), null, null, null)?.use {
            it.moveToFirst() && (it.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
        } == true
        if (!writable) throw DownloadProblem("Choose a writable folder in Downloads settings.")
    }

    fun create(name: String): Uri = DocumentsContract.createDocument(resolver, root, ReleaseFiles.mime(name), name)
        ?: throw DownloadProblem("Could not create the file in the selected folder.")
}
