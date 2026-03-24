package sstu.grivvus.yamusic.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class OpenApiRoutesRealBackendTest {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val json = Json { ignoreUnknownKeys = true }

    private data class Token(
        val userId: Long,
        val tokenType: String,
        val accessToken: String,
        val refreshToken: String,
    )

    private data class ParsedUser(
        val id: Long,
        val username: String,
        val email: String?,
        val hasEmailField: Boolean,
    )

    @Test
    fun ping_returns200_andEmptyBody() {
        val response = client.newCall(
            requestBuilder("/ping", null)
                .get()
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
            val body = res.body.string().trim()
            assertTrue("Expected empty body for /ping, got: $body", body.isEmpty())
        }
    }

    @Test
    fun register_returns201_andTokenResponse() {
        val username = uniqueUsername("register")
        val password = uniquePassword()

        val response = client.newCall(
            requestBuilder("/auth/register", null)
                .post(jsonBody("username" to username, "password" to password))
                .build()
        ).execute()

        response.use { res ->
            assertEquals(201, res.code)
            val token = parseToken(res.body.string())
            val user = fetchUserById(token)
            assertEquals(username, user.username)
        }
    }

    @Test
    fun login_returns200_andTokenResponse() {
        val username = uniqueUsername("login")
        val password = uniquePassword()
        val registeredToken = registerUser(username, password)

        val response = client.newCall(
            requestBuilder("/auth/login", null)
                .post(jsonBody("username" to username, "password" to password))
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
            val token = parseToken(res.body.string())
            assertEquals(registeredToken.userId, token.userId)
        }
    }

    @Test
    fun getUserById_returns200_andUserBody() {
        val username = uniqueUsername("get-user")
        val password = uniquePassword()
        val token = registerUser(username, password)

        val response = client.newCall(
            requestBuilder("/users/${token.userId}", authHeader(token))
                .get()
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
            val user = parseUser(res.body.string())
            assertEquals(token.userId, user.id)
            assertEquals(username, user.username)
            assertTrue("Email field must be present", user.hasEmailField)
        }
    }

    @Test
    fun changeUser_returns200_andUpdatedUserBody() {
        val username = uniqueUsername("change-user")
        val password = uniquePassword()
        val token = registerUser(username, password)

        val newUsername = uniqueUsername("updated")
        val newEmail = "${newUsername}@example.com"

        val response = client.newCall(
            requestBuilder("/users/${token.userId}", authHeader(token))
                .patch(
                    jsonBody(
                        "new_username" to newUsername,
                        "new_email" to newEmail,
                    )
                )
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
            val user = parseUser(res.body.string())
            assertEquals(token.userId, user.id)
            assertEquals(newUsername, user.username)
            assertEquals(newEmail, user.email)
        }
    }

    @Test
    fun changePassword_returns200_andMessageBody() {
        val username = uniqueUsername("change-password")
        val oldPassword = uniquePassword()
        val newPassword = uniquePassword()
        val token = registerUser(username, oldPassword)

        val response = client.newCall(
            requestBuilder("/users/${token.userId}/change_password", authHeader(token))
                .patch(
                    jsonBody(
                        "old_password" to oldPassword,
                        "new_password" to newPassword,
                    )
                )
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
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
            requestBuilder("/users/${token.userId}", authHeader(token))
                .get()
                .build()
        ).execute()

        response.use { res ->
            assertEquals(200, res.code)
            return parseUser(res.body.string())
        }
    }

    private fun parseToken(body: String?): Token {
        val jsonObject = parseJson(body)
        val userIdValue = jsonObject["user_id"]
        assertTrue(
            "user_id must be a number",
            userIdValue is JsonPrimitive && userIdValue.isString.not()
        )
        val userId = (userIdValue as JsonPrimitive).content.toLong()

        val tokenType = jsonObject.getString("token_type")
        val access = jsonObject.getString("access_token")
        val refresh = jsonObject.getString("refresh_token")
        assertTrue("token_type should not be blank", tokenType.isNotBlank())
        assertTrue("access_token should not be blank", access.isNotBlank())
        assertTrue("refresh_token should not be blank", refresh.isNotBlank())
        return Token(
            userId = userId,
            tokenType = tokenType,
            accessToken = access,
            refreshToken = refresh,
        )
    }

    private fun parseUser(body: String?): ParsedUser {
        val jsonObject = parseJson(body)
        val idValue = jsonObject["id"]
        assertTrue("id must be a number", idValue is JsonPrimitive && idValue.isString.not())
        val id = (idValue as JsonPrimitive).content.toLong()

        val username = jsonObject.getString("username")
        assertTrue("username should not be blank", username.isNotBlank())
        val hasEmail = jsonObject.containsKey("email")
        val emailValue = jsonObject["email"]
        val email = when {
            !hasEmail -> null
            emailValue is JsonNull -> null
            emailValue is JsonPrimitive && emailValue.isString -> emailValue.content
            else -> null
        }
        return ParsedUser(
            id = id,
            username = username,
            email = email,
            hasEmailField = hasEmail,
        )
    }

    private fun parseJson(body: String?): JsonObject {
        val text = body?.trim().orEmpty()
        assertTrue("Expected JSON body, got empty", text.isNotEmpty())
        val element = json.parseToJsonElement(text)
        assertTrue("Expected JSON object", element is JsonObject)
        return element as JsonObject
    }

    private fun JsonObject.getString(key: String): String {
        val value = this[key]
        assertNotNull("Missing field: $key", value)
        val primitive = value as? JsonPrimitive
        assertTrue("Field $key must be string", primitive != null && primitive.isString)
        return primitive!!.content
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
        val baseUrlArg = System.getProperty("apiBaseUrl")?.trim().orEmpty()
        if (baseUrlArg.isNotBlank()) {
            val normalized = if (baseUrlArg.endsWith("/")) baseUrlArg else "$baseUrlArg/"
            return normalized.toHttpUrl()
        }

        val host = System.getProperty("apiHost")?.trim().orEmpty().ifBlank { "127.0.0.1" }
        val port = System.getProperty("apiPort")?.trim().orEmpty().ifBlank { "8000" }
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
        val jsonObject = buildString {
            append("{")
            fields.forEachIndexed { index, (key, value) ->
                if (index > 0) append(",")
                append("\"").append(escapeJson(key)).append("\":")
                append("\"").append(escapeJson(value)).append("\"")
            }
            append("}")
        }
        return jsonObject.toRequestBody(jsonMediaType)
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun uniqueUsername(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    }

    private fun uniquePassword(): String {
        return "pw_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    }
}
