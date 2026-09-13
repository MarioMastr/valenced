package io.github.revenge.xposed.tweaks.plugins.internal

import io.github.revenge.core.hostVersion
import io.github.revenge.plugins.*

/** The version of the host Discord app, kept in `:core` so shared code resolves the `discord` dependency. */
internal var DISCORD_VERSION: Version
    get() = hostVersion
    set(value) {
        hostVersion = value
    }


/**
 * Provider plugin representing Revenge's API, providing the Revenge module's class loader
 * that external native plugins link against. It also tracks the API version so an update automatically re-verifies every plugin's compatibility range.
 *
 * > `CompositeClassLoader`'s parent also fulfills the actual lookup, so this plugin is redundant for providing the class loader.
 *
 * The JS API is split into multiple `revenge.api.*` sub-plugins which get run at different JS stages, so this has no JS entry point.
 */
internal val apiProviderPlugin = internalPlugin(
    PluginManifest(
        id = API_DEPENDENCY_ID,
        name = "Revenge Plugin API",
        description = "Provides the Revenge plugin API.",
        author = "Revenge",
        version = API_VERSION,
    ),
    setOf(InternalPluginFlags.INTERNAL, InternalPluginFlags.ESSENTIAL, InternalPluginFlags.API),
) {}

/**
 * Provider plugin representing the host Discord app.
 *
 * Tracks the host app so a Discord update automatically re-verifies every plugin's compatibility range.
 */
internal val discordProviderPlugin by lazy {
    internalPlugin(
        PluginManifest(
            id = DISCORD_DEPENDENCY_ID,
            name = "Discord",
            description = "Provides the host Discord app version.",
            author = "Discord",
            version = DISCORD_VERSION,
        ),
        setOf(InternalPluginFlags.INTERNAL, InternalPluginFlags.ESSENTIAL, InternalPluginFlags.API),
    ) {}
}
