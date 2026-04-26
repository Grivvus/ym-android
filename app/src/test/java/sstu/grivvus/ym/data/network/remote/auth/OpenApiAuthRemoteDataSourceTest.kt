package sstu.grivvus.ym.data.network.remote.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.AuthApiMapper
import sstu.grivvus.ym.openapi.apis.DefaultApi
import sstu.grivvus.ym.openapi.infrastructure.ApiResponse
import sstu.grivvus.ym.openapi.infrastructure.Success
import sstu.grivvus.ym.openapi.models.MessageResponse
import sstu.grivvus.ym.openapi.models.PasswordResetConfirmRequest
import sstu.grivvus.ym.openapi.models.PasswordResetRequest

@OptIn(ExperimentalCoroutinesApi::class)
class OpenApiAuthRemoteDataSourceTest {
    @Test
    fun requestPasswordReset_usesPublicGeneratedApi() = runTest {
        val api = mockk<DefaultApi>()
        every {
            api.requestPasswordResetWithHttpInfo(
                PasswordResetRequest(email = "tester@example.com"),
            )
        } returns Success(MessageResponse(msg = "accepted"), statusCode = 202)
        val dataSource = createDataSource(api)

        val message = dataSource.requestPasswordReset("tester@example.com")

        assertThat(message).isEqualTo("accepted")
    }

    @Test
    fun confirmPasswordReset_usesPublicGeneratedApi() = runTest {
        val api = mockk<DefaultApi>()
        every {
            api.confirmPasswordResetWithHttpInfo(
                PasswordResetConfirmRequest(
                    email = "tester@example.com",
                    code = "123456",
                    newPassword = "new-pass",
                ),
            )
        } returns Success(MessageResponse(msg = "changed"), statusCode = 200)
        val dataSource = createDataSource(api)

        val message = dataSource.confirmPasswordReset(
            email = "tester@example.com",
            code = "123456",
            newPassword = "new-pass",
        )

        assertThat(message).isEqualTo("changed")
    }

    private fun createDataSource(api: DefaultApi): OpenApiAuthRemoteDataSource {
        return OpenApiAuthRemoteDataSource(
            generatedApiProvider = object : GeneratedApiProvider {
                override suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T {
                    return block(api)
                }

                override suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T {
                    error("Password reset should use public API")
                }
            },
            apiExecutor = object : ApiExecutor {
                override suspend fun <T : Any> execute(block: suspend () -> ApiResponse<T?>): T {
                    val response = block()
                    check(response is Success<*>) { "Expected Success response" }
                    @Suppress("UNCHECKED_CAST")
                    return response.data as T
                }

                override suspend fun executeUnit(block: suspend () -> ApiResponse<Unit?>) {
                    block()
                }

                override suspend fun <T> executeRaw(block: suspend () -> T): T = block()
            },
            authApiMapper = AuthApiMapper(),
        )
    }
}
