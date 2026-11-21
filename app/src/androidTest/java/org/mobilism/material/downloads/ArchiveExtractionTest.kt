package org.mobilism.material.downloads

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveExtractionTest {
    // Three independently encoded archives containing the same nested text file.
    private val archives = listOf(
        "rar" to "UmFyIRoHAM+QcwAADQAAAAAAAACDdHQAgDAADQAAAA0AAAADPa5eFQAAAAAUMBAApIEAAG5lc3RlZC9oZWxsby50eHRBcmNoaXZlIHRlc3QKBLB7AAAHAA==",
        "rar" to "UmFyIRoHAQDFGjMyAwEAAA3VN4gfAgINBA2kgwI9rl4VAAEQbmVzdGVkL2hlbGxvLnR4dEFyY2hpdmUgdGVzdAoZsjo1AwUAAA==",
        "7z" to "N3q8ryccAARE07xKEQAAAAAAAABqAAAAAAAAADZbdncBAAxBcmNoaXZlIHRlc3QKAAEEBgABCREABwsBAAEhIQEaDA0ACAoBPa5eFQAABQEZDAAAAAAAAAAAAAAAABEjAG4AZQBzAHQAZQBkAC8AaABlAGwAbABvAC4AdAB4AHQAAAAZABQKAQCDWrYL2XndARUGAQAAAAAAAAA="
    )

    @Test fun releaseFilesExtractRar4Rar5AndSevenZipLocally() {
        val file = File.createTempFile("release", ".part", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            for ((format, data) in archives) {
                val original = Base64.getDecoder().decode(data)
                file.writeBytes(original)
                val extracted = mutableListOf<String>()
                ReleaseFiles.publish(file, "attachment.bin", true, {}) { name, input ->
                    extracted += name
                    assertEquals("Archive test\n", input.reader().readText())
                }
                assertEquals(listOf("hello.txt"), extracted)
                ReleaseFiles.publish(file, "attachment.bin", false, {}) { name, input ->
                    assertEquals("attachment.$format", name)
                    assertArrayEquals(original, input.readBytes())
                }
                ReleaseFiles.publish(file, "book.cbr", true, {}) { name, input ->
                    assertEquals("book.cbr", name)
                    assertArrayEquals(original, input.readBytes())
                }
                file.writeBytes(original.copyOf(10))
                val failure = assertThrows(UnreadableArchive::class.java) {
                    ReleaseFiles.publish(file, "incomplete.$format", true, {}) { _, input -> input.readBytes() }
                }
                assertEquals("incomplete.$format", failure.archiveName)
            }
        } finally {
            file.delete()
        }
    }
}
