pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info/")
    }
}

rootProject.name = "Valenced"
include(":android")
include(":android:api")
include(":bundle")
include(":core")
include(":ios")

// cursed system for ios on linux
if (!System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)) {
    val wantsIos = gradle.startParameter.taskNames.any { task ->
        if (task.startsWith("-")) return@any false
        task.contains("ios", ignoreCase = true) ||
            task.contains("konan", ignoreCase = true) ||
            task.contains("tweak", ignoreCase = true) ||
            !task.contains(':')
    }
    if (wantsIos) {
        gradle.beforeProject {
            extensions.extraProperties.set("valenced.appleTargets", "true")
        }
    }
}
