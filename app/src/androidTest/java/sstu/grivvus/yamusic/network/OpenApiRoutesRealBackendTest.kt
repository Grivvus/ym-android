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
    fun ping_returns200_andEmptyBody() = runBlocking {
        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/ping", null)
                    .get()
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(200, res.code)
            val body = res.body?.string().orEmpty().trim()
            assertTrue("Expected empty body for /ping, got: $body", body.isEmpty())
        }
    }

    @Test
    fun register_returns201_andTokenResponse() = runBlocking {
        val username = uniqueUsername("register")
        val password = uniquePassword()

        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/auth/register", null)
                    .post(jsonBody("username" to username, "password" to password))
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(201, res.code)
            val token = parseToken(res.body?.string())
            assertEquals(username, fetchUserById(token).username)
        }
    }

    @Test
    fun login_returns200_andTokenResponse() = runBlocking {
        val username = uniqueUsername("login")
        val password = uniquePassword()
        val registeredToken = registerUser(username, password)

        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/auth/login", null)
                    .post(jsonBody("username" to username, "password" to password))
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(200, res.code)
            val token = parseToken(res.body?.string())
            assertEquals(registeredToken.userId, token.userId)
        }
    }

    @Test
    fun getUserById_returns200_andUserBody() = runBlocking {
        val username = uniqueUsername("get-user")
        val password = uniquePassword()
        val token = registerUser(username, password)

        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/user/${token.userId}", authHeader(token))
                    .get()
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(200, res.code)
            val user = parseUser(res.body.string())
            assertEquals(token.userId, user.id)
            assertEquals(username, user.username)
            assertTrue("Email field must be present", user.hasEmailField)
        }
    }

    @Test
    fun changeUser_returns200_andUpdatedUserBody() = runBlocking {
        val username = uniqueUsername("change-user")
        val password = uniquePassword()
        val token = registerUser(username, password)

        val newUsername = uniqueUsername("updated")
        val newEmail = "${newUsername}@example.com"

        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/user/${token.userId}", authHeader(token))
                    .patch(
                        jsonBody(
                            "new_username" to newUsername,
                            "new_email" to newEmail,
                        )
                    )
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(200, res.code)
            val user = parseUser(res.body.string())
            assertEquals(token.userId, user.id)
            assertEquals(newUsername, user.username)
            assertEquals(newEmail, user.email)
        }
    }

    @Test
    fun changePassword_returns200() = runBlocking {
        val username = uniqueUsername("change-password")
        val oldPassword = uniquePassword()
        val newPassword = uniquePassword()
        val token = registerUser(username, oldPassword)

        val response = withContext(Dispatchers.IO) {
            client.newCall(
                requestBuilder("/user/${token.userId}/change_password", authHeader(token))
                    .patch(
                        jsonBody(
                            "old_password" to oldPassword,
                            "new_password" to newPassword,
                        )
                    )
                    .build()
            ).execute()
        }

        response.use { res ->
            assertEquals(200, res.code)
        }
    }

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

    private fun fetchUserById(token: Token): ParsedUser {
        val response = client.newCall(
            requestBuilder("/user/${token.userId}", authHeader(token))
                .get()
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
            return parseUser(res.body.string())
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

    private fun parseUser(body: String?): ParsedUser {
        val json = parseJson(body)
        val idValue = json.get("id")
        assertTrue("id must be a number", idValue is Number)
        val username = json.getString("username")
        assertTrue("username should not be blank", username.isNotBlank())
        val hasEmail = json.has("email")
        val email = if (!hasEmail || json.isNull("email")) null else json.getString("email")
        return ParsedUser(
            id = (idValue as Number).toLong(),
            username = username,
            email = email,
            hasEmailField = hasEmail,
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
