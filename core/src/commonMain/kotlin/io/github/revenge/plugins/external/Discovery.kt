package io.github.revenge.plugins.external

import io.github.revenge.plugins.PluginError
import io.github.revenge.plugins.PluginErrorCodes
import io.github.revenge.plugins.PluginManifest
import io.github.revenge.plugins.pluginLog

/**
 * A plugin whose manifest parsed, but whose artifacts are not loaded yet.
 *
 * Platforms subclass this to carry where the plugin lives (a directory on Android, a bundle on iOS).
 *
 * Dependency resolution happens at [io.github.revenge.plugins.PluginDependencyGraph].
 */
open class ParsedPlugin(val manifest: ExternalManifest)

class DiscoveryFailure(
    val manifest: PluginManifest?,
    val errors: List<PluginError>,
) {
    /**
     * If the plugin itself is at fault (bad code, bad manifest) rather than its environment (dependencies).
     * It'll get disabled instead of being skipped.
     */
    val isPluginFault: Boolean
        get() = errors.any {
            it.code == PluginErrorCodes.LOAD_FAILED || it.code == PluginErrorCodes.MANIFEST_INVALID
        }
}

/** Records a skipped plugin for [id] and logs. */
fun MutableMap<String, DiscoveryFailure>.sessionSkip(
    id: String,
    manifest: PluginManifest?,
    errors: List<PluginError>,
) {
    this[id] = DiscoveryFailure(manifest, errors)
    pluginLog.e("Skipping external plugin '$id': ${errors.joinToString("; ") { it.message }}")
}
