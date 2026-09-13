package io.github.revenge.plugins

/** Why a dependent goes down together with its dependency. */
enum class CascadeEdge(val label: String) {
    Required("required"),

    /** An optional dependency the dependent actually linked against this session. */
    LinkedOptional("linked optional"),
}

/** One plugin to stop, in the order it must stop. */
data class StopAction(
    val id: String,
    /** The dependency that takes this plugin down, or `null` for the plugin the cascade started from. */
    val because: String? = null,
    val edge: CascadeEdge? = null,
)

/**
 * The ordered stop list for [pluginId]: every running dependent that is linked to it, deepest first, then [pluginId].
 *
 * A dependent goes down when it requires the plugin, or when it linked it as an optional dependency. Optionals that
 * were never linked ([unlinkedOptionals]) are unaffected: they already run without it.
 *
 * Returns an empty list when [pluginId] isn't running.
 */
fun cascadeStop(
    pluginId: String,
    /** Manifests of the currently running plugins, keyed by ID. */
    running: Map<String, PluginManifest>,
    /** Per plugin, the optional dependency IDs it did not link this session. */
    unlinkedOptionals: (String) -> Set<String>,
): List<StopAction> {
    if (pluginId !in running) return emptyList()

    val actions = mutableListOf<StopAction>()
    val visited = mutableSetOf<String>()

    fun visit(id: String, because: String?, edge: CascadeEdge?) {
        if (!visited.add(id)) return

        for ((dependentId, manifest) in running) {
            val dep = manifest.dependencies[id] ?: continue
            val dependentEdge = when {
                !dep.optional -> CascadeEdge.Required
                id !in unlinkedOptionals(dependentId) -> CascadeEdge.LinkedOptional
                else -> continue
            }
            visit(dependentId, id, dependentEdge)
        }

        actions += StopAction(id, because, edge)
    }

    visit(pluginId, null, null)
    return actions
}

/**
 * The IDs that lose their enabled state when [pluginId] is disabled: itself plus every transitive required dependent.
 *
 * Linked optionals are not included: they only stop, since they load fine without the plugin next boot.
 *
 * Throws [PluginSystemError] with [PluginErrorCodes.NOT_ALLOWED] when the plugin, or anything that requires it,
 * is essential.
 */
fun cascadeDisable(
    pluginId: String,
    /** Every known plugin's manifest, loaded or not. An unloaded plugin must be disabled as well. */
    knownManifests: Map<String, PluginManifest>,
    isEssential: (String) -> Boolean,
): Set<String> {
    if (isEssential(pluginId)) throw PluginSystemError(
        PluginErrorCodes.NOT_ALLOWED,
        "Plugin $pluginId is essential and cannot be disabled",
    )

    val toDisable = mutableSetOf(pluginId)
    var changed = true
    while (changed) {
        changed = false
        for ((dependentId, manifest) in knownManifests) {
            if (dependentId in toDisable) continue
            val requiredOnDisabled = manifest.dependencies.any { (depId, dep) ->
                !dep.optional && depId in toDisable
            }
            if (!requiredOnDisabled) continue

            if (isEssential(dependentId)) throw PluginSystemError(
                PluginErrorCodes.NOT_ALLOWED,
                "Plugin '$pluginId' is depended by '$dependentId' which is essential, so it cannot be disabled",
            )

            toDisable += dependentId
            changed = true
        }
    }
    return toDisable
}
