package sstu.grivvus.ym.playback.artwork

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FilePlaybackArtworkCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun ensureLocalArtwork_downloadsOnceAndReturnsCachedFile() = runTest {
        var requestCount = 0
        val cache = FilePlaybackArtworkCache(
            context = context,
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        requestCount += 1
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                TEST_ARTWORK_BYTES.toResponseBody(
                                    "image/jpeg".toMediaType(),
                                ),
                            )
                            .build()
                    },
                )
                .build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        cache.clear()
        val sourceUri = Uri.parse("https://example.test/albums/42/cover")

        val firstLocalUri = cache.ensureLocalArtwork(sourceUri)
        val secondLocalUri = cache.ensureLocalArtwork(sourceUri)

        assertThat(firstLocalUri).isEqualTo(secondLocalUri)
        assertThat(requestCount).isEqualTo(1)
        assertThat(File(checkNotNull(firstLocalUri?.path)).readBytes())
            .isEqualTo(TEST_ARTWORK_BYTES)

        cache.clear()

        assertThat(File(checkNotNull(firstLocalUri.path)).exists()).isFalse()
    }

    private companion object {
        private val TEST_ARTWORK_BYTES = byteArrayOf(1, 2, 3, 4)
    }
}
