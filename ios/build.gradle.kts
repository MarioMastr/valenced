plugins {
    base
}

val isMacHost = System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)

val theosDir = providers.environmentVariable("THEOS")
    .orElse(providers.systemProperty("user.home").map { "$it/theos" })

val coreFrameworkDir = rootProject.layout.projectDirectory
    .dir("core/build/bin/iosArm64/debugFramework/ValencedCore.framework")

val coreStaticLib = rootProject.layout.projectDirectory
    .file("core/build/bin/iosArm64/debugStatic/libValencedCore.a")

val packageTweak = tasks.register<Exec>("packageTweak") {
    group = "build"
    description = "Builds the ValencedTweak deb with Theos."

    workingDir = layout.projectDirectory.asFile
    commandLine("make", "package")
    environment("THEOS", theosDir.get())
    if (isMacHost) {
        dependsOn(":core:linkDebugFrameworkIosArm64")
        environment("VALENCED_CORE_FW", coreFrameworkDir.asFile.absolutePath)
    } else {
        dependsOn(":core:linkValencedCoreStaticIosArm64")
        environment("VALENCED_CORE_LIB", coreStaticLib.asFile.absolutePath)
    }

    onlyIf("Theos is installed ($theosDir)") { file(theosDir.get()).isDirectory }
}

tasks.named("assemble") {
    dependsOn(packageTweak)
}

val cleanTheos = tasks.register<Exec>("cleanTheos") {
    group = "build"
    description = "Removes Theos build intermediates (.theos/)."
    workingDir = layout.projectDirectory.asFile
    commandLine("make", "clean")
    environment("THEOS", theosDir.get())
    isIgnoreExitValue = true
}

tasks.named<Delete>("clean") {
    dependsOn(cleanTheos)
}
