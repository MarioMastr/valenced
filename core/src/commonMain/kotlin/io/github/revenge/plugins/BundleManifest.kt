package io.github.revenge.plugins

import io.github.revenge.core.RevengeJson
import io.github.revenge.plugins.external.requireValidPluginId
import kotlinx.serialization.Serializable

@Serializable
data class BundleManifest(
    val version: String,
    val plugins: List<Plugin> = emptyList(),
) {
    @Serializable
    data class Plugin(
        val id: String,
        val dependencies: Map<String, Dependency> = emptyMap(),
    )

    @Serializable
    data class Dependency(
        val version: String? = null,
        val optional: Boolean = false,
    )

    fun pluginManifests(): List<PluginManifest> {
        val bundleVersion = Version.parse(version)

        return plugins.mapNotNull { entry ->
            runCatching { entry.toPluginManifest(bundleVersion) }
                .onFailure { pluginLog.e("Skipping bundled plugin '${entry.id}'", it) }
                .getOrNull()
        }
    }
}

fun parseBundleManifest(json: String) =
    runCatching { RevengeJson.decodeFromString<BundleManifest>(json) }
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
