package io.github.revenge.plugins.external

import io.github.revenge.plugins.PluginError
import io.github.revenge.plugins.PluginErrorCodes
import io.github.revenge.plugins.PluginManifest
import io.github.revenge.plugins.Version
import io.github.revenge.plugins.VersionRange
import io.github.revenge.plugins.pluginLog

/**
 * A plugin whose manifest parsed, but whose artifacts are not loaded yet.
 *
 * Platforms subclass this to carry where the plugin lives (a directory on Android, a bundle on iOS).
 */
open class ParsedPlugin(val manifest: ExternalManifest) {
    /** Dependencies this plugin can link against. Unsatisfied ones are removed later. */
    var availableDeps: Set<String> = manifest.dependencies.keys

    /** Installed optional dependencies that are unsatisfied. This plugin will load without them. */
    val unsatisfiedOptionalDeps: MutableSet<String> = mutableSetOf()
}

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

class DependencyProblem(val code: String, val reason: String)

fun unsatisfiedDependencyReason(
    depId: String,
    dep: ExternalDependency,
    /** null = missing */
    version: Version?,
): DependencyProblem? {
    val range = dep.version?.let(VersionRange::parse) ?: VersionRange.ANY
    return when {
        version == null -> DependencyProblem(
            PluginErrorCodes.DEPENDENCY_MISSING,
            "missing dependency '$depId'",
        )

        !range.satisfies(version) -> DependencyProblem(
            PluginErrorCodes.DEPENDENCY_UNSATISFIED,
            "dependency '$depId' version $version does not satisfy '${dep.version}'",
        )

        else -> null
    }
}

/**
 * Drops plugins with unsatisfiable required dependencies and their dependents, then topologically sorts the remainder
 * (dependencies before dependents).
 *
 * Unsatisfied optionals are removed from [ParsedPlugin.availableDeps] and recorded in
 * [ParsedPlugin.unsatisfiedOptionalDeps], and dependency cycles are skipped.
 */
fun <T : ParsedPlugin> orderByDependencies(
    parsed: Map<String, T>,
    knownVersions: Map<String, Version>,
    failures: MutableMap<String, DiscoveryFailure>,
): List<T> {
    val known = knownVersions + parsed.mapValues { (_, plugin) -> Version.parse(plugin.manifest.version) }
    val dropped = mutableSetOf<String>()

    var changed = true
    while (changed) {
        changed = false
        for ((id, plugin) in parsed) {
            if (id in dropped) continue

            val problems = mutableListOf<PluginError>()
            for ((depId, dep) in plugin.manifest.dependencies) {
                val version = if (depId in dropped) null else known[depId]
                val problem = unsatisfiedDependencyReason(depId, dep, version) ?: continue

                if (dep.optional) {
                    if (depId in plugin.availableDeps) {
                        plugin.availableDeps -= depId
                        if (version != null) {
                            plugin.unsatisfiedOptionalDeps += depId
                            pluginLog.w("Optional ${problem.reason} for plugin '$id'; ignoring")
                        }
                    }
                    continue
                }

                problems += PluginError(problem.code, "Unsatisfied dependency: ${problem.reason}")
            }

            if (problems.isNotEmpty()) {
                dropped += id
                changed = true
                failures.sessionSkip(id, plugin.manifest.toPluginManifest(), problems)
            }
        }
    }

    val remaining = parsed.filterKeys { it !in dropped }.toMutableMap()
    val ordered = mutableListOf<T>()
    while (remaining.isNotEmpty()) {
        val ready = remaining.values.filter { plugin ->
            plugin.manifest.dependencies.none { (depId, dep) ->
                depId in remaining && plugin.manifest.id != depId &&
                        (!dep.optional || depId in plugin.availableDeps)
            }
        }
        if (ready.isEmpty()) {
            for (id in remaining.keys) {
                failures.sessionSkip(
                    id, remaining[id]?.manifest?.toPluginManifest(),
                    listOf(PluginError(PluginErrorCodes.DEPENDENCY_CYCLE, "Dependency cycle")),
                )
            }
            break
        }
        for (plugin in ready) {
            ordered += plugin
            remaining.remove(plugin.manifest.id)
        }
    }
    return ordered
}
