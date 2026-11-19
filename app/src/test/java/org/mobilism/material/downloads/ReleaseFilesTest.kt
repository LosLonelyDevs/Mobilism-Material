package org.mobilism.material.downloads

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseFilesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun directFileAndAttachmentNamesSurviveSignedUrlsAndGenericMime() {
        val direct = "https://files.example/Reader.apk?signature=private"
        assertEquals("Reader.apk", ReleaseFiles.directName(direct))
        assertNull(ReleaseFiles.directName("https://host.example/file?id=Reader.apk"))
        assertEquals("Reader.apk", ReleaseFiles.name(direct, null, "application/octet-stream", "Release"))
        assertEquals("Reader Pro.apk", ReleaseFiles.name(direct, "attachment; filename*=UTF-8''Reader%20Pro.apk", null, "R"))
        assertEquals("Reader.apk", ReleaseFiles.name(direct, "attachment; filename=\"../Reader.apk\"", null, "R"))
    }

    @Test fun apkServedAsZipStaysByteIdentical() {
        val apk = archive("AndroidManifest.xml", "classes.dex")
        val saved = unpack(apk, "Reader.zip", true)
        assertEquals(setOf("Reader.apk"), saved.keys)
        assertArrayEquals(apk.readBytes(), saved.getValue("Reader.apk"))
    }

    @Test fun zipExtractionIsOptionalAndFlat() {
        val zip = archive("files/App.apk", "docs/notes.txt")
        assertEquals(setOf("App.apk", "notes.txt"), unpack(zip, "package.zip", true).keys)
        assertArrayEquals(zip.readBytes(), unpack(zip, "package.zip", false).getValue("package.zip"))
        assertArrayEquals(zip.readBytes(), unpack(zip, "book.epub", true).getValue("book.epub"))
    }

    @Test fun unsafeArchiveFailsBeforeItCreatesAnyFile() {
        val bad = archive("valid.txt", "../escape.apk")
        assertThrows(DownloadProblem::class.java) {
            ReleaseFiles.publish(bad, "archive.zip", true, {}) { _, _ -> fail("Partial extraction") }
        }
    }

    private fun unpack(file: File, name: String, extract: Boolean) = buildMap {
        ReleaseFiles.publish(file, name, extract, {}) { leaf, stream -> put(leaf, stream.readBytes()) }
    }

    private fun archive(vararg names: String) = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(name.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
