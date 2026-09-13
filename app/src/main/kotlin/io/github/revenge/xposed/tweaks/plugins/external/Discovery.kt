package io.github.revenge.xposed.tweaks.plugins.external

import io.github.revenge.plugins.PluginError
import io.github.revenge.plugins.PluginErrorCodes
import io.github.revenge.plugins.PluginManifest
import io.github.revenge.plugins.Version
import io.github.revenge.plugins.VersionRange
import io.github.revenge.plugins.external.DiscoveryFailure
import io.github.revenge.plugins.external.ExternalDependency
import io.github.revenge.plugins.external.ExternalManifest
import io.github.revenge.plugins.external.MANIFEST_FILE
import io.github.revenge.plugins.external.ParsedPlugin
import io.github.revenge.plugins.external.orderByDependencies
import io.github.revenge.plugins.external.sessionSkip
import io.github.revenge.plugins.pluginLog
import io.github.revenge.xposed.tweaks.plugins.*
import java.io.File

/** Plugin directory with parsed manifest, but unloaded artifacts. */
internal class ParsedExternalPlugin(val dir: File, manifest: ExternalManifest) : ParsedPlugin(manifest)

internal class ExternalDiscovery(
    val factories: List<PluginFactory>,
    /** Keyed by plugin ID, or directory name if manifest failed to parse. */
    val failures: Map<String, DiscoveryFailure>,
)

/**
 * Reads installed plugins in `<dataDir>/files/revenge/plugins/dist`.
 * Each plugin has its own `<id>/` directory with a [MANIFEST_FILE] and `dist` artifacts.
 *
 * Plugins with missing or unsatisfying required dependencies are skipped, cascading to its dependents.
 * Dependencies always load first, so a plugin can link against the classes of its dependencies.
 */
internal fun discoverExternalPlugins(
    dataDir: String,
    knownVersions: Map<String, Version>,
): ExternalDiscovery {
    val failures = mutableMapOf<String, DiscoveryFailure>()
    val root = externalPluginsRoot(dataDir)
    if (!root.isDirectory) return ExternalDiscovery(emptyList(), failures)

    // Temp files and spooled ZIPs left mid-install.
    root.listFiles { it.name.startsWith(".") && it.name != DEX_CACHE_DIR }
        ?.forEach { it.deleteRecursively() }

    // Plugin IDs can't start with a dot.
    val dirs = root.listFiles { it.isDirectory && !it.name.startsWith(".") }
        ?: return ExternalDiscovery(emptyList(), failures)

    val parsed = mutableMapOf<String, ParsedExternalPlugin>()
    for (dir in dirs) {
        if (!File(dir, MANIFEST_FILE).isFile) continue

        try {
            val plugin = parseExternalPluginDir(dir)
            parsed[plugin.manifest.id] = plugin
        } catch (e: Throwable) {
            failures[dir.name] = DiscoveryFailure(
                null,
                listOf(
                    PluginError(
                        PluginErrorCodes.MANIFEST_INVALID,
                        "Failed to read plugin manifest: ${e.message}",
                    ),
                ),
            )
            pluginLog.e("Failed to read external plugin in '${dir.name}'", e)
        }
    }

    val ordered = orderByDependencies(parsed, knownVersions, failures)

    // Failed required dependencies fail dependents too. Optionals are ignored.
    val failed = mutableSetOf<String>()
    val result = mutableListOf<PluginFactory>()
    for (plugin in ordered) {
        val id = plugin.manifest.id
        plugin.availableDeps = plugin.availableDeps.filterNot { depId ->
            depId in failed && plugin.manifest.dependencies[depId]?.optional == true
        }.toSet()

        val failedDeps = plugin.manifest.dependencies
            .filter { (depId, dep) -> !dep.optional && depId in failed }
            .keys
        if (failedDeps.isNotEmpty()) {
            failed += id
            failures.sessionSkip(
                id, plugin.manifest.toPluginManifest(),
                failedDeps.map {
                    PluginError(PluginErrorCodes.DEPENDENCY_FAILED, "Dependency '$it' failed to load")
                },
            )
            continue
        }

        try {
            result += buildExternalFactory(plugin)
        } catch (e: Throwable) {
            failed += id
            failures[id] = DiscoveryFailure(
                runCatching { plugin.manifest.toPluginManifest() }.getOrNull(),
                listOf(PluginError(PluginErrorCodes.LOAD_FAILED, "Failed to load: ${e.message}")),
            )
            pluginLog.e("Failed to load external plugin '$id'", e)
        }
    }

    return ExternalDiscovery(result, failures)
}
