package org.mobilism.material.data

import java.time.Instant
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ForumReleaseParserTest {
    @Test fun readsWearReleaseFromOriginalPostAndNormalizesForumMarkup() {
        val release = ForumReleaseParser.release(topic(), 6075324)
        assertEquals("Pujie Watch Faces - Wear OS 4 v6.5.14 [Patched]", release.title)
        assertEquals("Author", release.author)
        assertEquals(ContentSection.Apps, release.section)
        assertEquals(1347L, release.categoryId)
        assertEquals(12016008L, release.likeId)
        assertEquals(1L, release.replies)
        assertEquals(Instant.parse("2025-10-09T17:01:00Z").epochSecond, release.publishedAt)
        assertEquals("https://forum.mobilism.org/viewtopic.php?f=1347&t=6075324", release.browserUrl)
        assertEquals(listOf("https://images.example.org/watch.png"), release.images)
        assertEquals(release.images.first(), release.thumbnail)
        val content = Jsoup.parse(release.contentHtml)
        assertEquals("Download Instructions:", content.selectFirst("u")!!.text())
        assertEquals("Watch APK:", content.selectFirst("b")!!.text())
        assertEquals("https://files.example.org/watch.apk", content.selectFirst("a")!!.attr("href"))
        assertTrue(content.text().contains("😊"))
        assertFalse(release.contentHtml.contains("Reader reply"))
        assertFalse(release.contentHtml.contains("signature"))
        assertTrue(content.select("script").isEmpty())
        assertFalse(release.contentHtml.contains("javascript:"))
        assertNull(release.liked)
    }

    @Test fun fallbackIsNotHardcodedToWearOrOneTopic() {
        val html = topic().replace("6075324", "99").replace("1347", "100")
            .replace(">Apps<", ">Books<")
        val release = ForumReleaseParser.release(html, 99)
        assertEquals(99L, release.id)
        assertEquals(100L, release.categoryId)
        assertEquals(ContentSection.Books, release.section)
    }

    @Test fun rejectsWrongTopicLoginPagesAndNonReleaseForums() {
        listOf(topic().replace("t=6075324", "t=999"), "<h1>Log in</h1>",
            topic().replace(">Apps<", ">Discussion<"),
            topic().replace("class='postbody'", "class='missing'"),
            topic().replace("./viewtopic.php?", "https://example.org/viewtopic.php?")
        ).forEach { html ->
            val error = runCatching { ForumReleaseParser.release(html, 6075324) }.exceptionOrNull()
            assertTrue(error is ApiFailure)
            assertEquals(FailureKind.InvalidResponse, (error as ApiFailure).kind)
        }
    }

    @Test fun commentsExcludeOriginalPostAndUseForumPaginationOffsets() {
        val html = topic() + """<div class='pagination'>
            <a href='viewtopic.php?t=6075324&amp;sd=d&amp;start=10'>2</a>
            <a href='viewtopic.php?t=6075324&amp;sd=d&amp;start=20'>3</a>
            <a href='viewtopic.php?t=99&amp;start=1'>Unrelated</a></div>"""
        val result = ForumReleaseParser.comments(html, 6075324, 12016008, 0)
        assertEquals(10, result.nextStart)
        assertTrue(result.page.canLoadMore)
        val comment = result.page.items.single()
        assertEquals(12033953L, comment.id)
        assertEquals("Reader", comment.author)
        assertEquals("Reader reply", Jsoup.parse(comment.contentHtml).text())
        assertEquals("Earlier", comment.quotes.single().author)
        assertEquals("Quoted text", Jsoup.parse(comment.quotes.single().html).text())
        val last = ForumReleaseParser.comments(topic(), 6075324, 12016008, 10)
        assertFalse(last.page.canLoadMore)
        assertNull(last.nextStart)
    }

    @Test fun liveReportedWearReleaseHasDetailsAndDownloads() {
        assumeTrue(System.getenv("MOBILISM_LIVE_WEAR_TEST") == "true")
        val client = OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        val api = Request.Builder().url("https://app.mobilism.org/api-new/release/6075324").build()
        client.newCall(api).execute().use { response ->
            assertEquals(400, response.code)
            assertTrue(response.body!!.string().contains("No records found."))
        }
        val request = Request.Builder().url("https://forum.mobilism.org/viewtopic.php?t=6075324").build()
        client.newCall(request).execute().use { response ->
            assertTrue("Forum HTTP ${response.code}", response.isSuccessful)
            val release = ForumReleaseParser.release(response.body!!.string(), 6075324)
            assertTrue(release.title.contains("Pujie Watch Faces"))
            assertEquals(1347L, release.categoryId)
            assertEquals(ContentSection.Apps, release.section)
            assertEquals(12016008L, release.likeId)
            assertTrue(release.images.isNotEmpty())
            val content = Jsoup.parse(release.contentHtml)
            assertTrue(content.text().contains("Download Instructions"))
            assertTrue(content.select("a[href]").any { it.attr("href").contains("uploadrar.com") })
        }
    }

    private fun topic() = """
        <div class='page-category-topic'><h3><a href='./viewtopic.php?f=1347&amp;t=6075324&amp;sid=example'>Pujie Watch Faces - Wear OS 4 v6.5.14 [Patched]</a></h3></div>
        <div class='pagination'><a href='viewforum.php?f=402'>Apps</a><a href='viewforum.php?f=1347'>Wear</a>2 posts</div>
        <div id='p12016008' class='row-fluid blue-bar'><article><div>
          <div class='user-profile-username'><a>Author</a></div>
          <img src='https://images.example.org/avatar.png'>
          <div class='postbody'><div class='hidden-phone'><span class='muted'><em><a id='12016008'></a>Oct 9th, 2025, 6:01 pm</em></span></div>
            <div class='content'>Watch description<img src='//images.example.org/watch.png'>
              <img class='smilies' src='/smile.png' alt='😊'>
              <span style='text-decoration: underline'>Download Instructions:</span><br>
              <span style='font-weight: bold'>Watch APK:</span><br>
              <a href='https://files.example.org/watch.apk'>https://files.example.org/wa ... .apk</a>
              <script>untrusted()</script><a href='javascript:alert(1)'>Bad link</a>
            </div><div class='signature'><img src='https://images.example.org/signature.png'>signature</div>
          </div></div></article></div>
        <div id='p12033953' class='row-fluid blue-bar'><article><div>
          <div class='user-profile-username'><a>Reader</a></div>
          <div class='postbody'><div class='content'><blockquote><cite>Earlier wrote:</cite>Quoted text</blockquote>Reader reply</div></div>
        </div></article></div><abbr title='Daylight Saving Time'>DST</abbr>
    """.trimIndent()
}
