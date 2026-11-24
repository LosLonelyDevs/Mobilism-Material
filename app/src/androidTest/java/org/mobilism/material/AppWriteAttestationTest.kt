package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.AppHttp
import org.mobilism.material.usage.AppWriteAttestation

class AppWriteAttestationTest {
    @Test fun signsOriginalPayloadOnlyForConfiguredDestination() {
        val alias = "test-writes-" + UUID.randomUUID()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(alias, mode)
        }
        val prefs = context.getSharedPreferences(alias, Context.MODE_PRIVATE)
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val key = KeyPairGenerator.getInstance("EC", "AndroidKeyStore").run {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
                generateKeyPair()
            }
            prefs.edit().putString("ticket", "sample").putLong("expires", System.currentTimeMillis() / 1000 + 600).commit()
            val endpoint = "https://report.example.invalid/test"
            val signer = AppWriteAttestation(context, endpoint, alias)
            val original = JSONObject().put("sample", "Café\n日本語").toString()
            var received = ""
            val client = OkHttpClient.Builder().addInterceptor(signer.interceptor("usage")).addInterceptor { chain ->
                val request = chain.request()
                val buffer = Buffer()
                checkNotNull(request.body).writeTo(buffer)
                received = buffer.readUtf8()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body("{}".toResponseBody()).build()
            }.build()
            fun post(url: String) {
                client.newCall(Request.Builder().url(url).header(AppHttp.USER_AGENT_HEADER, "fixture")
                    .post(original.toRequestBody()).build()).execute().close()
            }
            post(endpoint)
            val envelope = JSONObject(received)
            assertEquals(original, envelope.getString("payload"))
            val signed = listOf("mm-write-1", "usage", "", "fixture", envelope.getLong("at").toString(),
                envelope.getString("nonce"), "sample", original).joinToString("\n")
            val valid = Signature.getInstance("SHA256withECDSA").run {
                initVerify(key.public)
                update(signed.toByteArray(Charsets.UTF_8))
                verify(Base64.getUrlDecoder().decode(envelope.getString("signature")))
            }
            assertTrue(valid)
            post("https://other.example.invalid/test")
            assertEquals(original, received)
        } finally {
            keystore.deleteEntry(alias)
            target.deleteSharedPreferences(alias)
        }
    }
}
