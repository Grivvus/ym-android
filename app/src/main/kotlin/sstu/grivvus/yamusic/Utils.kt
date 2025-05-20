package sstu.grivvus.yamusic

import io.github.cdimascio.dotenv.dotenv


object Settings {
    private val dotenv = dotenv{
        directory = "/assets"
        filename = ".env"
    }

    val apiHost = dotenv["API_HOST"] ?: "0.0.0.0"
    val apiPort = dotenv["API_PORT"] ?: "8000"
}