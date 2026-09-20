package io.github.revenge.xposed.tweaks

import android.content.res.XModuleResources
import io.github.revenge.plugins.API_VERSION
import io.github.revenge.plugins.BundleManifest
import io.github.revenge.plugins.parseBundleManifest
import io.github.revenge.xposed.RevengeConstants
import io.github.revenge.xposed.tweak

lateinit var bundleManifest: BundleManifest

val revengeBundleManifest by tweak {
    runCatching { RevengeUpdater.readBundleManifest() }
        .onFailure { log.e("Unable to read downloaded bundle manifest", it) }
        .getOrNull()?.let {
            if (setBundleManifest(it)) return@tweak
        }

    runCatching {
        XModuleResources.createInstance(modulePath, null)
            .assets.open(RevengeConstants.BUNDLE_MANIFEST_FILE)
            .use { it.readBytes().decodeToString() }
    }
        .onFailure { log.w("No bundle manifest in module assets", it) }
        .getOrNull()?.let {
            if (setBundleManifest(it)) return@tweak
        }

    bundleManifest = BundleManifest(API_VERSION.toString(), emptyList())
}

private fun setBundleManifest(json: String): Boolean {
    bundleManifest = parseBundleManifest(json) ?: return false
    return true
}
