package app.valenced.plugins

import app.valenced.core.ValencedJson
import app.valenced.plugins.external.requireValidPluginId
import app.valenced.plugins.repos.PluginSource
import kotlinx.serialization.Serializable

const val BUNDLE_MANIFEST_FORMAT = 1

@Serializable
data class BundleManifest(
    val format: Int = BUNDLE_MANIFEST_FORMAT,
    val version: String,
    val plugins: List<Plugin> = emptyList(),
) {
    @Serializable
    data class Plugin(
        val id: String,
        /** Cannot be turned off. Implies [enabledByDefault]. */
        val essential: Boolean = false,
        /** On until the user says otherwise. */
        val enabledByDefault: Boolean = false,
        val dependencies: Map<String, Dependency> = emptyMap(),
        val defaultSource: PluginSource? = null,
    )

    @Serializable
    data class Dependency(
        val version: String? = null,
        val optional: Boolean = false,
    )

    fun pluginManifests(): List<Pair<Plugin, PluginManifest>> {
        val bundleVersion = Version.parse(version)

        return plugins.mapNotNull { entry ->
            runCatching { entry to entry.toPluginManifest(bundleVersion) }
                .onFailure { pluginLog.e("Skipping bundled plugin '${entry.id}'", it) }
                .getOrNull()
        }
    }
}

fun parseBundleManifest(json: String) =
    runCatching { ValencedJson.decodeFromString<BundleManifest>(json) }
        .onFailure { pluginLog.e("Unable to parse bundle manifest", it) }
        .getOrNull()

// JS will fill these in later.
private fun BundleManifest.Plugin.toPluginManifest(version: Version): PluginManifest {
    requireValidPluginId(id)

    return PluginManifest(
        id = id,
        name = id,
        description = "",
        author = "",
        dependencies = dependencies.entries.associate { (depId, dep) ->
            requireValidPluginId(depId)
            depId to PluginDependency(
                version = dep.version?.let(VersionRange::parse) ?: VersionRange.ANY,
                optional = dep.optional,
            )
        },
        version = version,
    )
}
