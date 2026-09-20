package io.github.revenge.xposed.tweaks.plugins.internal

import io.github.revenge.plugins.BundleManifest
import io.github.revenge.xposed.tweaks.plugins.PluginFactory

/**
 * Handles registration of internal JS-only (bundled) plugins. Registering them here makes the dependency graph complete.
 *
 * Native discovers external plugins and handles dependency resolution before JS starts, so without this,
 * it cannot tell an ID belonging to a bundled plugin from one that does not exist, which causes it to drop any
 * external plugin that depends on one.
 *
 * Even though nothing here is enabled by default, native registers them regardless, making them linkable by dependents.
 * JS later sets each plugin's default flags into the slot when its implementation registers.
 */
internal fun bundledPlugins(bundleManifest: BundleManifest): List<PluginFactory> =
    bundleManifest.pluginManifests().map { internalPlugin(it, BUNDLED_PLUGIN_FLAGS) {} }

private val BUNDLED_PLUGIN_FLAGS = setOf(InternalPluginFlags.INTERNAL)
