package io.github.revenge.xposed.tweaks.plugins.internal

import io.github.revenge.plugins.BundleManifest
import io.github.revenge.xposed.tweaks.plugins.PluginFactory

/**
 * Handles registration of internal JS-only (bundled) plugins. Registering them here makes the dependency graph complete.
 *
 * Native discovers external plugins and handles dependency resolution before JS starts, so without this,
 * it cannot tell an ID belonging to a bundled plugin from one that does not exist, which causes it to drop any
 * external plugin that depends on one.
 */
internal fun bundledPlugins(bundleManifest: BundleManifest): List<PluginFactory> =
    bundleManifest.pluginManifests().map { (entry, manifest) -> internalPlugin(manifest, entry.internalFlags()) {} }

private fun BundleManifest.Plugin.internalFlags() = buildSet {
    add(InternalPluginFlags.INTERNAL)
    if (essential) add(InternalPluginFlags.ESSENTIAL)
    if (enabledByDefault) add(InternalPluginFlags.ENABLED_BY_DEFAULT)
}
