import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

val openApiSpec = rootProject.layout.projectDirectory.file("openapi/openapi.yml")
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")
val openApiRemoteSpecUrl = providers.gradleProperty("openapi.remoteSpecUrl").orNull

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(openApiSpec.asFile.absolutePath)
    outputDir.set(openApiOutputDir.get().asFile.absolutePath)
    packageName.set("sstu.grivvus.yamusic.openapi")
    configOptions.set(
        mapOf(
            "dateLibrary" to "java8",
            "serializationLibrary" to "kotlinx_serialization"
        )
    )
}

android {
    namespace = "sstu.grivvus.yamusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "sstu.grivvus.yamusic"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main").kotlin.srcDir("${layout.buildDirectory.get().asFile}/generated/openapi/src/main/kotlin")
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.named("preBuild") {
    dependsOn(tasks.named("openApiGenerate"))
}

val syncOpenApiSpec = tasks.register("syncOpenApiSpec") {
    group = "openapi"
    description =
        "Downloads OpenAPI spec from remote repository (if openapi.remoteSpecUrl is configured)."

    outputs.file(openApiSpec)
    onlyIf { !openApiRemoteSpecUrl.isNullOrBlank() }

    doLast {
        val remoteUrl = checkNotNull(openApiRemoteSpecUrl)
        val connection = (URI.create(remoteUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/yaml, text/yaml, application/vnd.github.raw")
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody =
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GradleException(
                "Failed to download OpenAPI spec from $remoteUrl. HTTP $responseCode. $errorBody"
            )
        }

        openApiSpec.asFile.parentFile.mkdirs()
        connection.inputStream.use { input ->
            openApiSpec.asFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

openApiValidate {
    inputSpec.set(openApiSpec.asFile.absolutePath)
}

val patchOpenApiGeneratedSources = tasks.register("patchOpenApiGeneratedSources") {
    dependsOn(tasks.named("openApiGenerate"))

    doLast {
        val apiClientPath = openApiOutputDir.get()
            .file("src/main/kotlin/sstu/grivvus/yamusic/openapi/infrastructure/ApiClient.kt")
            .asFile

        if (!apiClientPath.exists()) return@doLast

        val original = apiClientPath.readText()
        val patched = original.replace(
            "is OffsetDateTime, is OffsetTime, is LocalDateTime, is LocalDate, is LocalTime ->\n            parseDateToQueryString(value)",
            "is OffsetDateTime, is OffsetTime, is LocalDateTime, is LocalDate, is LocalTime ->\n            value.toString()",
        )
            .replace(
                "if(body == null) {\n            return null\n        }",
                "if (T::class == Unit::class) {\n            @Suppress(\"UNCHECKED_CAST\")\n            return Unit as T\n        }\n        if(body == null) {\n            return null\n        }",
            )


        if (patched != original) {
            apiClientPath.writeText(patched)
        }
    }
}

tasks.named("openApiGenerate") {
    dependsOn(syncOpenApiSpec)
    onlyIf { openApiSpec.asFile.exists() }
}

tasks.named("preBuild") {
    dependsOn(patchOpenApiGeneratedSources)
}


dependencies {
    // App dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.androidx.test.espresso.idling.resources)

    // Architecture Components
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.media3.session)
    ksp(libs.room.compiler)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)

    // Hilt
    implementation(libs.hilt.android.core)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)

    // HttpOk
    implementation(libs.okhttp)

    // Coil
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // JSON-serialization
    implementation(libs.kotlinx.serialization.json)

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation.core)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.accompanist.appcompat.theme)
    implementation(libs.accompanist.swiperefresh)

    debugImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Dependencies for local unit tests
    testImplementation(composeBom)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.archcore.testing)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.espresso.contrib)
    testImplementation(libs.androidx.test.espresso.intents)
    testImplementation(libs.google.truth)
    testImplementation(libs.androidx.compose.ui.test.junit)

    // JVM tests - Hilt
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    // Dependencies for Android unit tests
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit)

    // AndroidX Test - JVM testing
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext)
    testImplementation(libs.androidx.test.rules)

    // AndroidX Test - Instrumented testing
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.archcore.testing)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.contrib)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.espresso.idling.resources)
    androidTestImplementation(libs.androidx.test.espresso.idling.concurrent)

    // AndroidX Test - Hilt testing
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
