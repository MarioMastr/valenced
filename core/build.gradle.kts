plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

// Apple targets need a macOS host, so they only configure when asked for.
// Build them with -Prevenge.appleTargets=true on a Mac. On Linux they are absent entirely.
val appleTargets = providers.gradleProperty("revenge.appleTargets").orNull == "true"

kotlin {
    jvmToolchain(libs.versions.javaVersion.get().toInt())

    android {
        namespace = "io.github.revenge.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdkLibrary.get().toInt()

        withHostTest {
            // Logger wraps android.util.Log; return default values so host tests can run.
            isReturnDefaultValues = true
        }
    }

    if (appleTargets) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.okio)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
        }
    }
}

// commonMain can't read BuildConfig, so the API version arrives as a generated constant.
val generateCoreConstants = tasks.register("generateCoreConstants") {
    val apiVersion = libs.versions.apiVersion.get()
    val outputDir = layout.buildDirectory.dir("generated/revenge/commonMain/kotlin")

    inputs.property("apiVersion", apiVersion)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().file("io/github/revenge/core/CoreConstants.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.github.revenge.core

            /** The published `:api` version, mirrored from `gradle/libs.versions.toml`. */
            internal const val API_VERSION_STRING: String = "$apiVersion"
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateCoreConstants)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = "io.github.revenge"
        version = libs.versions.apiVersion.get()
    }
}
