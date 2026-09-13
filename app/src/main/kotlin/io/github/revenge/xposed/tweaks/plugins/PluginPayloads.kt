package io.github.revenge.xposed.tweaks.plugins

import io.github.revenge.core.toNativeMap
import io.github.revenge.plugins.PluginError
import io.github.revenge.plugins.PluginErrorCodes
import io.github.revenge.plugins.PluginManifest
import io.github.revenge.plugins.PluginPayload
import io.github.revenge.plugins.PluginScope
import io.github.revenge.plugins.SourcePayload
import io.github.revenge.plugins.external.DiscoveryFailure
import io.github.revenge.plugins.pluginLog
import io.github.revenge.plugins.repos.PluginSource
import io.github.revenge.plugins.toJSPayload
import io.github.revenge.plugins.toPayload
import io.github.revenge.plugins.toPluginError
import io.github.revenge.xposed.tweaks.plugins.internal.InternalPluginFlags
import java.io.File

internal fun PluginFactory.toJSPayload(
    loaded: LoadedPlugin? = null,
    bootError: PluginError? = null,
    /* `null` = sideloaded */
    source: PluginSource? = null,
): Map<String, Any?> = PluginPayload(
    manifest = manifest.toPayload(),
    script = readScript(),
    internal = InternalPluginFlags.INTERNAL in internalFlags,
    essential = InternalPluginFlags.ESSENTIAL in internalFlags,
    enabledByDefault = InternalPluginFlags.ENABLED_BY_DEFAULT in internalFlags,
    api = InternalPluginFlags.API in internalFlags,
    source = source?.toPayload(),
    unsatisfiedOptionalDependencies = unsatisfiedOptionalDependencies.toList(),
    errors = buildList {
        bootError?.let(::add)
        loaded?.let { addAll(it.scope.pluginErrors) }
    },
).toNativeMap()

/** Everything the plugin has thrown so far this session. */
val PluginScope.pluginErrors: List<PluginError>
    get() = errors.replayCache.map { it.toPluginError(PluginErrorCodes.PLUGIN_ERROR) }

val PluginScope.errorsJSPayload
    get() = pluginErrors.map { it.toJSPayload() }

internal fun DiscoveryFailure.toJSPayload(
    manifest: PluginManifest,
    source: PluginSource? = null,
): Map<String, Any?> = PluginPayload(
    manifest = manifest.toPayload(),
    script = null,
    internal = false,
    essential = false,
    enabledByDefault = false,
    api = false,
    source = source?.toPayload(),
    errors = errors,
    failed = true,
).toNativeMap()

internal fun PluginSource.toPayload(): SourcePayload = SourcePayload(repo = repo, channel = channel, held = held)

internal fun PluginSource.toJSPayload(): Map<String, Any?> = toPayload().toNativeMap()

/** Read the plugin's `dist.script` source (if any) for JS to evaluate. */
private fun PluginFactory.readScript(): String? = scriptPath?.let { path ->
    try {
        File(path).readText()
    } catch (e: Throwable) {
        pluginLog.e("Failed to read plugin script for ${manifest.id}: $path", e)
        null
    }
}
