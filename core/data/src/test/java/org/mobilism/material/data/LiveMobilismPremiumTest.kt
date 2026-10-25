package org.mobilism.material.data

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in locally. Credentials and signed download URLs never enter test output. */
class LiveMobilismPremiumTest {
    @Test fun loginResolveAndReadDownloadHeader() {
        val envPath = System.getenv("MOBILISM_PREMIUM_TEST_ENV")
        assumeTrue(!envPath.isNullOrBlank())
        val values = File(checkNotNull(envPath)).readLines().mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size != 2 || parts[0].startsWith('#')) null
            else parts[0].trim() to parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
        }.toMap()
        val api = MobilismPremiumApi(OkHttpClient.Builder().addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).also { response ->
                println("Premium check: ${chain.request().url.host} HTTP ${response.code}")
            }
        }.build())
        val key = api.credentials(values.getValue("MOBILISM_USERNAME"), values.getValue("MOBILISM_PREMIUM_PASSWORD"))
        api.validate(key)
        val hosts = api.hosts()
        assertTrue("Live host list must be nonempty", hosts.isNotEmpty())
        val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
        fun forum(path: String, token: String? = null, form: FormBody? = null): JSONObject {
            val request = Request.Builder().url("https://app.mobilism.org/api-new/$path")
                .header("Accept", "application/json")
            token?.let { request.header("X-Auth-Token", it) }
            form?.let { request.post(it) }
            return client.newCall(request.build()).execute().use { response ->
                assertTrue("Forum request must succeed", response.isSuccessful)
                JSONObject(checkNotNull(response.body).string())
            }
        }
        val session = forum("auth/login", form = FormBody.Builder().add("username", values.getValue("MOBILISM_USERNAME"))
            .add("password", values.getValue("MOBILISM_PASSWORD")).build())
        val release = ReleaseJson.parse(forum("release/6438871", session.getString("access_token")).getJSONObject("response"))
        val source = release.downloadLinks.firstOrNull { HostMatcher.matches(it, "rapidgator.net") }
        assertNotNull("Sample release needs a Rapidgator mirror", source)
        val direct = api.resolve(key, checkNotNull(source))
        assertTrue("Resolver must return a different URL", direct != source)
        println("Resolved host: ${direct.toHttpUrl().host}; query fields: ${direct.toHttpUrl().queryParameterNames}")
        client.newCall(Request.Builder().url(direct).header("Range", "bytes=0-3").build()).execute().use { response ->
            assertTrue("Resolved download must be readable", response.isSuccessful)
            val bytes = checkNotNull(response.body).source().readByteArray(4)
            println("Download check: HTTP ${response.code}; type ${response.header("Content-Type")}; signature ${bytes.joinToString("") { "%02x".format(it) }}")
            assertTrue("Sample PDF must have a PDF header", bytes.contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46)))
        }
    }
}
