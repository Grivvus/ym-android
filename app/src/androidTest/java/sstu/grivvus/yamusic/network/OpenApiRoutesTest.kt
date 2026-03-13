package sstu.grivvus.yamusic.network

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sstu.grivvus.yamusic.Settings
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OpenApiRoutesRealBackendTest {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val webpMediaType = "image/webp".toMediaType()

    private data class Token(
        val userId: Long,
        val tokenType: String,
        val accessToken: String,
        val refreshToken: String,
    )

    @Test
    fun uploadUserAvatar_returns201_andMessageBody() = runBlocking {
        val username = uniqueUsername("avatar")
        val password = uniquePassword()
        val token = registerUser(username, password)

        val avatarBytes = createWebpAvatar()
        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/user/${token.userId}/avatar", authHeader(token))
                    .post(avatarBytes.toRequestBody(webpMediaType))
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(201, res.code)
            val message = parseMessage(res.body.string())
            assertTrue("Message should not be blank", message.isNotBlank())
        }
    }

    private fun registerUser(username: String, password: String): Token {
        val response = client.newCall(
            requestBuilder("/auth/register", null)
                .post(jsonBody("username" to username, "password" to password))
                .build()
        ).execute()

        response.use { res ->
            assertEquals(201, res.code)
            return parseToken(res.body.string())
        }
    }

    private data class ParsedUser(
        val id: Long,
        val username: String,
        val email: String?,
        val hasEmailField: Boolean,
    )

    private fun parseToken(body: String?): Token {
        val json = parseJson(body)
        val userIdValue = json.get("user_id")
        assertTrue("user_id must be a number", userIdValue is Number)
        val tokenType = json.getString("token_type")
        val access = json.getString("access_token")
        val refresh = json.getString("refresh_token")
        assertTrue("token_type should not be blank", tokenType.isNotBlank())
        assertTrue("access_token should not be blank", access.isNotBlank())
        assertTrue("refresh_token should not be blank", refresh.isNotBlank())
        return Token(
            userId = (userIdValue as Number).toLong(),
            tokenType = tokenType,
            accessToken = access,
            refreshToken = refresh,
        )
    }

    private fun parseMessage(body: String?): String {
        val json = parseJson(body)
        val msg = json.getString("msg")
        assertTrue("msg should not be blank", msg.isNotBlank())
        return msg
    }

    private fun parseJson(body: String?): JSONObject {
        val text = body?.trim().orEmpty()
        assertTrue("Expected JSON body, got empty", text.isNotEmpty())
        return JSONObject(text)
    }

    private fun requestBuilder(path: String, authHeader: String?): Request.Builder {
        val baseUrl = resolveBaseUrl()
        val url = baseUrl.newBuilder()
            .addPathSegments(path.trimStart('/'))
            .build()
        val builder = Request.Builder().url(url)
        if (!authHeader.isNullOrBlank()) {
            builder.header("Authorization", authHeader)
        }
        builder.header("Accept", "application/json")
        return builder
    }

    private fun resolveBaseUrl(): HttpUrl {
        val args = InstrumentationRegistry.getArguments()
        val baseUrlArg = args.getString("apiBaseUrl")?.trim().orEmpty()
        if (baseUrlArg.isNotBlank()) {
            val normalized = if (baseUrlArg.endsWith("/")) baseUrlArg else "$baseUrlArg/"
            return normalized.toHttpUrl()
        }

        val hostArg = args.getString("apiHost")?.trim().orEmpty()
        val portArg = args.getString("apiPort")?.trim().orEmpty()
        val host = hostArg.ifBlank { Settings.apiHost }
        val port = portArg.ifBlank { Settings.apiPort }
        Settings.configureApi(host, port)
        return HttpUrl.Builder()
            .scheme("http")
            .host(host)
            .port(port.toInt())
            .build()
    }

    private fun authHeader(token: Token): String {
        val type = token.tokenType.ifBlank { "Bearer" }
        return "$type ${token.accessToken}"
    }

    private fun jsonBody(vararg fields: Pair<String, String>): okhttp3.RequestBody {
        val json = JSONObject()
        fields.forEach { (key, value) -> json.put(key, value) }
        return json.toString().toRequestBody(jsonMediaType)
    }

    private fun uniqueUsername(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    }

    private fun uniquePassword(): String {
        return "pw_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    }

    private fun createWebpAvatar(): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        val success = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
        assertTrue("Failed to compress WEBP avatar", success)
        return output.toByteArray()
    }
}
