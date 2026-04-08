package sstu.grivvus.ym.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkClientModuleTest {
    @Test
    fun provideTrackUploadHttpClient_increasesReadAndWriteTimeoutsOnly() {
        val client = NetworkClientModule.provideTrackUploadHttpClient()

        assertThat(client.connectTimeoutMillis).isEqualTo(10_000)
        assertThat(client.readTimeoutMillis).isEqualTo(5 * 60 * 1_000)
        assertThat(client.writeTimeoutMillis).isEqualTo(5 * 60 * 1_000)
        assertThat(client.callTimeoutMillis).isEqualTo(0)
    }
}
