package sstu.grivvus.yamusic

import io.github.cdimascio.dotenv.dotenv

private val dotenv = dotenv{
    directory = "/assets"
    filename = ".env"
}

class Settings
private constructor(
    val apiHost: String,
    val apiPort: String,
) {
    private var instance: Settings? = null;
    fun getSettings(): Settings {
        if (instance == null) {
            instance = Settings(dotenv["API_HOST"], dotenv["API_PORT"])
        }
        return instance!!
    }
}