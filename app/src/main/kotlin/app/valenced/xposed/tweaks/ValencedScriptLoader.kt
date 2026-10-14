package app.valenced.xposed.tweaks

import app.valenced.xposed.ValencedConstants
import app.valenced.xposed.ensureDir
import app.valenced.xposed.ensureFile
import app.valenced.xposed.tweak
import app.valenced.xposed.tweaks.base.InjectorScope
import app.valenced.xposed.tweaks.base.registerScriptInjector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Waits for script updates and loads the Valenced bundle. Depends on [app.valenced.xposed.tweaks.base.scriptLoader].
 *
 * 1. Awaits the bundle download from [valencedUpdater].
 * 2. Runs every file under `files/valenced/preloads/`.
 * 3. Loads `cache/valenced/bundle.js` (downloaded copy) if present.
 * 4. Falls back to the in-APK `assets://valenced.bundle` asset shipped with this module.
 */
val valencedScriptLoader by tweak {
    val dataDir = appInfo.dataDir
    val filesDir = File(dataDir, ValencedConstants.FILES_DIR).apply { ensureDir() }
    val cacheDir = File(dataDir, ValencedConstants.CACHE_DIR).apply { ensureDir() }
    val preloadsDir = File(filesDir, ValencedConstants.PRELOADS_DIR).apply { ensureDir() }
    val mainScript = File(cacheDir, ValencedConstants.MAIN_SCRIPT_FILE).apply { ensureFile() }

    registerScriptInjector { scope: InjectorScope ->
        runValencedScripts(scope, preloadsDir, mainScript)
    }
}

private fun runValencedScripts(scope: InjectorScope, preloadsDir: File, mainScript: File) {
    val log = scope.tweakLog
    log.i("Running Valenced custom scripts...")

    runBlocking {
        try {
            withTimeout(ValencedUpdater.TIMEOUT) { ValencedUpdater.downloadReady.await() }
        } catch (e: Throwable) {
            log.w("Bundle download did not complete", e)
        }
    }

    try {
        preloadsDir.walk().filter { it.isFile }.sorted().forEach { f ->
            log.d("Running preload: ${f.absolutePath}")
            scope.runFile(f.absolutePath)
        }

        if (mainScript.exists()) {
            log.i("Loading downloaded bundle: ${mainScript.absolutePath}")
            scope.runFile(mainScript.absolutePath)
        } else {
            log.i("Downloaded bundle missing; falling back to ${ValencedConstants.FALLBACK_BUNDLE_ASSET}")
            scope.runAsset(ValencedConstants.FALLBACK_BUNDLE_ASSET)
        }
    } catch (e: Throwable) {
        log.e("Unable to run Valenced scripts", e)
    }
}

