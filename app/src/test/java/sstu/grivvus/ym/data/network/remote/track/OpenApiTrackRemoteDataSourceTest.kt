package sstu.grivvus.ym.data.network.remote.track

import com.google.common.truth.Truth.assertThat
import io.mockk.anyConstructed
import io.mockk.capture
import io.mockk.captureNullable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.TrackApiMapper
import sstu.grivvus.ym.data.network.model.UploadPart
import sstu.grivvus.ym.openapi.apis.DefaultApi
import sstu.grivvus.ym.openapi.infrastructure.ApiResponse
import sstu.grivvus.ym.openapi.infrastructure.Success
import sstu.grivvus.ym.openapi.models.TrackUploadSuccessResponse
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OpenApiTrackRemoteDataSourceTest {
    @After
    fun tearDown() {
        unmockkConstructor(DefaultApi::class)
    }

    @Test
    fun uploadTrack_forwardsGlobalAvailabilityToGeneratedClient() = runTest {
        val baseApi = mockk<DefaultApi>()
        every { baseApi.baseUrl } returns "https://example.com"

        mockkConstructor(DefaultApi::class)

        val nameSlot = slot<String>()
        val artistIdSlot = slot<Int>()
        val trackFileSlot = slot<File>()
        val albumIdSlot = slot<Int?>()
        val isSingleSlot = slot<Boolean?>()
        val isGloballyAvailableSlot = slot<Boolean?>()

        every {
            anyConstructed<DefaultApi>().uploadTrackWithHttpInfo(
                capture(nameSlot),
                capture(artistIdSlot),
                capture(trackFileSlot),
                captureNullable(albumIdSlot),
                captureNullable(isSingleSlot),
                captureNullable(isGloballyAvailableSlot),
            )
        } returns Success(
            TrackUploadSuccessResponse(trackId = 123),
            statusCode = 202,
        )

        val dataSource = OpenApiTrackRemoteDataSource(
            generatedApiProvider = object : GeneratedApiProvider {
                override suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T {
                    error("Unused in this test")
                }

                override suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T {
                    return block(baseApi)
                }
            },
            apiExecutor = object : ApiExecutor {
                override suspend fun <T : Any> execute(block: suspend () -> ApiResponse<T?>): T {
                    val response = block()
                    check(response is Success<T>) { "Expected Success response" }
                    return response.data
                }

                override suspend fun executeUnit(block: suspend () -> ApiResponse<Unit?>) {
                    block()
                }

                override suspend fun <T> executeRaw(block: suspend () -> T): T = block()
            },
            trackApiMapper = TrackApiMapper(),
            trackUploadHttpClient = OkHttpClient(),
        )

        val uploadFile = File.createTempFile("track-upload", ".mp3")
        uploadFile.writeText("stub-audio")

        try {
            val uploadedTrackId = dataSource.uploadTrack(
                name = "Track title",
                artistId = 7L,
                albumId = 9L,
                isSingle = false,
                track = UploadPart(file = uploadFile, mimeType = "audio/mpeg"),
                isGloballyAvailable = true,
            )

            assertThat(uploadedTrackId).isEqualTo(123L)
            assertThat(nameSlot.captured).isEqualTo("Track title")
            assertThat(artistIdSlot.captured).isEqualTo(7)
            assertThat(trackFileSlot.captured).isEqualTo(uploadFile)
            assertThat(albumIdSlot.captured).isEqualTo(9)
            assertThat(isSingleSlot.captured).isFalse()
            assertThat(isGloballyAvailableSlot.captured).isTrue()
        } finally {
            uploadFile.delete()
        }
    }
}
