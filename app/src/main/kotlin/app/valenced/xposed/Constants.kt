package app.valenced.xposed

object ValencedConstants {
    const val TARGET_PACKAGE = "com.discord"
    const val TARGET_ACTIVITY = "$TARGET_PACKAGE.react_activities.ReactActivity"

    const val FILES_DIR = "files/valenced"
    const val CACHE_DIR = "cache/valenced"
    const val MAIN_SCRIPT_FILE = "bundle.js"
    const val BUNDLE_MANIFEST_FILE = "manifest.json"
    const val PRELOADS_DIR = "preloads"

    const val LOADER_NAME = "ValencedXposed"
    val LOADER_VERSION
        get() = BuildConfig.VERSION_NAME
    val USER_AGENT
        get() = "ValencedXposed/$LOADER_VERSION"

    /**
     * Fallback Hermes bundle shipped inside this APK's `assets/` directory.
     * Loaded by [app.valenced.xposed.tweaks.valencedScriptLoader] when the cached `bundle.js` isn't available.
     */
    const val FALLBACK_BUNDLE_ASSET = "assets://valenced.bundle"
}

/**
 * Hermes bytecode assets shipped inside the Xposed module APK.
 */
val scriptAssets = emptyList<String>()
