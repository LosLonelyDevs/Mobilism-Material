package org.mobilism.material.usage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.mobilism.material.data.AppHttp

internal class AppWriteAttestation(context: Context, endpoint: String, private val alias: String = "mobilism-service-writes-v1") {
    private val service = endpoint.toHttpUrlOrNull()?.takeIf { it.isHttps }
    private val enrollment = service?.resolve("/api/app-attestation")
    private val prefs = context.getSharedPreferences("app-write-attestation", Context.MODE_PRIVATE)
    private val enrolling = AtomicBoolean()
    private val executor = Executors.newSingleThreadExecutor()
    private val json = "application/json".toMediaType()
    private val client by lazy {
        AppHttp.builder().followRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(8, TimeUnit.SECONDS).build()
    }

    fun interceptor(purpose: String) = Interceptor { chain ->
        val original = chain.request()
        val target = if (purpose == "usage") service else service?.resolve("/api/latest-apps")
        if (original.method != "POST" || original.url != target) return@Interceptor chain.proceed(original)
        val ticket = prefs.getString("ticket", null)
        val signed = runCatching { sign(original, purpose, ticket) }.getOrNull()
        val response = chain.proceed(signed ?: original)
        if (response.header("X-App-Attestation") == "1" && prefs.getString("ticket", null) == ticket &&
            (signed == null || response.header("X-App-Attestation-Renew") == "1")) prepare()
        response
    }

    private fun sign(request: Request, purpose: String, ticket: String?): Request? {
        val now = System.currentTimeMillis() / 1000
        if (ticket == null || prefs.getLong("expires", 0) <= now + 30) return null
        val key = store().getKey(alias, null) as? PrivateKey ?: return null
        val buffer = Buffer()
        (request.body ?: return null).writeTo(buffer)
        val payload = buffer.readUtf8()
        val nonce = UUID.randomUUID().toString()
        val message = listOf("mm-write-1", purpose, request.header("X-Following-Check").orEmpty(),
            request.header(AppHttp.USER_AGENT_HEADER).orEmpty(), now.toString(), nonce, ticket, payload).joinToString("\n")
        val proof = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(message.toByteArray(Charsets.UTF_8))
            Base64.getUrlEncoder().withoutPadding().encodeToString(sign())
        }
        val envelope = JSONObject().put("payload", payload).put("ticket", ticket).put("at", now)
            .put("nonce", nonce).put("signature", proof)
        return request.newBuilder().post(envelope.toString().toRequestBody(json)).build()
    }

    // Enrollment stays off the cache lookup path, even if the hardware or a trust service is unavailable.
    private fun prepare() {
        val now = System.currentTimeMillis() / 1000
        if (prefs.getLong("retryAt", 0) > now || !enrolling.compareAndSet(false, true)) return
        executor.execute {
            try {
                prefs.edit().putLong("retryAt", now + 3600).apply()
                enroll()
            } catch (_: Exception) {
                // Cache reads still work. Retry only after another ordinary request.
            } finally {
                enrolling.set(false)
            }
        }
    }

    private fun enroll() {
        val url = enrollment ?: return
        val challenge = client.newCall(Request.Builder().url(url).build()).execute().use {
            readJson(it).getString("challenge")
        }
        require(challenge.length in 1..512)
        val keystore = store()
        prefs.edit().remove("ticket").remove("expires").apply()
        keystore.deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(MessageDigest.getInstance("SHA-256").digest(challenge.toByteArray(Charsets.UTF_8)))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(spec)
            generateKeyPair()
        }
        val certs = JSONArray()
        keystore.getCertificateChain(alias).forEach { certs.put(Base64.getEncoder().encodeToString(it.encoded)) }
        val body = JSONObject().put("challenge", challenge).put("chain", certs)
        client.newCall(Request.Builder().url(url).post(body.toString().toRequestBody(json)).build()).execute().use {
            val reply = readJson(it)
            prefs.edit().putString("ticket", reply.getString("ticket")).putLong("expires", reply.getLong("expiresAt"))
                .remove("retryAt").apply()
        }
    }

    private fun readJson(response: Response): JSONObject {
        check(response.isSuccessful)
        val source = checkNotNull(response.body).source()
        source.request(4097)
        require(source.buffer.size <= 4096)
        return JSONObject(source.readUtf8())
    }

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
