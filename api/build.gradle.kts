plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "app.valenced.api"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdkLibrary.get().toInt()
    }

    sourceSets {
        named("main") {
            kotlin.directories += "src/main/kotlin"
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    }

    kotlin {
        jvmToolchain(libs.versions.javaVersion.get().toInt())
    }
}

dependencies {
    api(project(":core"))

    compileOnly(libs.xposed.api)
    compileOnly(libs.kotlinx.coroutines.android)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "app.valenced"
            artifactId = "api"
            version = libs.versions.apiVersion.get()

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
