plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.javaVersion.get().toInt())
}

android {
    namespace = "app.valenced.xposed"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.valenced.xposed"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1700
        versionName = "1.7.0"
    }

    sourceSets {
        named("main") {
            kotlin.directories += "src/main/kotlin"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        // Logger wraps android.util.Log; return default values so unit tests can run.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    compileOnly(libs.xposed.api)

    implementation(project(":api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    testImplementation(libs.kotlin.test)
}
