package sstu.grivvus.ym.data.network.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultErrorBodyParserTest {
    private val parser = DefaultErrorBodyParser()

    @Test
    fun parseMessage_validErrorResponse_returnsContractField() {
        val parsedMessage = parser.parseMessage("""{"error":"playlist not found"}""")

        assertThat(parsedMessage).isEqualTo("playlist not found")
    }

    @Test
    fun parseMessage_blankBody_returnsNull() {
        val parsedMessage = parser.parseMessage("   ")

        assertThat(parsedMessage).isNull()
    }

    @Test
    fun parseMessage_nonContractJson_throwsSerializationApiException() {
        val error = expectThrows<SerializationApiException> {
            parser.parseMessage("""{"message":"unexpected shape"}""")
        }

        assertThat(error.message).isEqualTo("Error response does not match API contract")
    }

    @Test
    fun parseMessage_plainText_throwsSerializationApiException() {
        val error = expectThrows<SerializationApiException> {
            parser.parseMessage("bad gateway")
        }

        assertThat(error.message).isEqualTo("Error response does not match API contract")
    }

    private inline fun <reified T : Throwable> expectThrows(
        block: () -> Unit,
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) {
                return error
            }
            throw error
        }
        throw AssertionError("Expected ${T::class.simpleName} to be thrown")
    }
}
