package io.github.revenge.xposed.tweaks.plugins

import io.github.revenge.Logger
import io.github.revenge.logger
import io.github.revenge.plugins.DependencyProblemPayload
import io.github.revenge.plugins.Plugin
import io.github.revenge.plugins.PluginErrorCodes
import io.github.revenge.plugins.PluginFlags
import io.github.revenge.plugins.PluginScope
import io.github.revenge.plugins.PluginStatesStore
import io.github.revenge.plugins.PluginSystemError
import io.github.revenge.plugins.Version
import io.github.revenge.plugins.cascadeDisable
import io.github.revenge.plugins.cascadeStop
import io.github.revenge.plugins.pluginLog
import io.github.revenge.xposed.api.HostScope
import io.github.revenge.xposed.api.callJSMethod
import io.github.revenge.xposed.tweaks.plugins.internal.InternalPluginFlags
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*

private inline val loaded get() = pluginRegistry.loaded

internal class LoadedPlugin(
    val plugin: Plugin,
    val scope: PluginScopeImpl,
    /** Collector persisting flag changes + broadcasting them to JS. Canceled on stop. */
    val persistJob: Job,
    /** Collector syncing native errors + broadcasting them to JS. Canceled on stop. */
    val errorSyncJob: Job,
) {
    /** Whether the native side is currently running. */
    @Volatile
    var started = true
}

internal class PluginScopeImpl(
    tweakScope: HostScope,
    plugin: Plugin,
    /** Lifecycle flags. */
    val flags: MutableStateFlow<Set<PluginFlags>>,
) : PluginScope, HostScope by tweakScope {
    override val log: Logger = logger("plugin:${plugin.manifest.id}")
    override val manifest = plugin.manifest

    override val storageDir: File by lazy {
        pluginStorageRoot(appInfo.dataDir).resolve(manifest.id).apply {
            mkdirs()
            if (!isDirectory || !canWrite()) throw PluginSystemError(
                PluginErrorCodes.STORAGE_FAILED,
                "Plugin storage directory is not writable: $this",
            )
        }
    }

    // Arbitrary limit of 1000 errors, should be enough in most cases
    override val errors = MutableSharedFlow<Throwable>(replay = 1000)

    override val enabled get() = PluginFlags.ENABLED in flags.value
    override val startedLate get() = PluginFlags.STARTED_LATE in flags.value

    override fun requireReload() {
        flags.value += PluginFlags.PENDING_RELOAD
    }

    override fun stop() {
        stopPlugin(manifest.id)
    }

    override fun disable() {
        disablePlugin(manifest.id)
    }
}

/**
 * Builds and starts a plugin. Throws if the plugin can't be built or started.
 *
 * Exceptions thrown by the plugin's own `start()` are captured into its scope's errors instead.
 */
context(host: HostScope)
internal fun loadPlugin(
    factory: PluginFactory,
    /** Loading mid-session (user enable) instead of at boot. */
    late: Boolean = false,
): LoadedPlugin {
    val manifest = factory.manifest

    val plugin = factory.builder.build(manifest)
    // Use the boot flags as it's what's currently running this session.
    val flags = PluginStatesStore.boot.flagsOf(manifest.id)

    // Turn on plugins that are essential or on by default, unless the slot already says so.
    if (
        PluginFlags.ENABLED !in flags.value &&
        (InternalPluginFlags.ESSENTIAL in factory.internalFlags ||
                InternalPluginFlags.ENABLED_BY_DEFAULT in factory.internalFlags)
    ) {
        flags.value += PluginFlags.ENABLED
    }

    if (late) flags.value += PluginFlags.STARTED_LATE

    val scope = PluginScopeImpl(host, plugin, flags)
    val bootSlot = PluginStatesStore.boot

    // `drop(1)` skips the initial load so we don't re-persist the values we just loaded.
    val persistJob = flags.drop(1).onEach { newFlags ->
        bootSlot.write(manifest.id, newFlags)
        sendStateToJS(bootSlot.id, manifest.id, newFlags)
    }.launchIn(pluginJobScope)

    val errorSyncJob = scope.errors.onEach {
        runCatching {
            scope.callJSMethod(PluginEvents.PLUGIN_ERRORED, listOf(manifest.id, scope.errorsJSPayload))
        }
    }.launchIn(pluginJobScope)

    // The plugin's own code runs here, so anything it throws belongs to the plugin, not the loader.
    try {
        plugin.start(scope)
    } catch (e: Throwable) {
        scope.errors.tryEmit(e)
        pluginLog.e("Plugin ${manifest.id} threw in start()", e)
    }

    val entry = LoadedPlugin(plugin, scope, persistJob, errorSyncJob)
    loaded[manifest.id] = entry
    pluginLog.i("Started plugin: ${manifest.id}")

    return entry
}

/** Stops a running plugin, taking every dependent that's linked to it down first. */
context(host: HostScope)
internal fun stopPlugin(pluginId: String) {
    // Snapshot the cascade first: stopping mutates [loaded].
    val actions = cascadeStop(
        pluginId,
        running = loaded.mapValues { (_, entry) -> entry.scope.manifest },
        unlinkedOptionals = { pluginRegistry.factories[it]?.unsatisfiedOptionalDependencies.orEmpty() },
    )

    for (action in actions) {
        action.because?.let { pluginLog.i("Stopping ${action.id}: its ${action.edge?.label} dependency $it is stopping") }
        stopLoadedPlugin(action.id)
    }
}

/** Stops one plugin, with no cascade. */
context(host: HostScope)
private fun stopLoadedPlugin(pluginId: String) {
    val entry = loaded.remove(pluginId) ?: return
    try {
        if (entry.started) entry.plugin.stop(entry.scope)
    } catch (e: Throwable) {
        entry.scope.errors.tryEmit(e)
        pluginLog.e("Plugin $pluginId threw in stop()", e)
    } finally {
        // stop() may have changed flags (requireReload), we need to sync before stopping the job.
        dispatchStateToJS(PluginStatesStore.bootSlotId, pluginId, entry.scope.flags.value)

        entry.persistJob.cancel()
        entry.errorSyncJob.cancel()
    }
}

/**
 * Disables and stops a plugin.
 * 
 * Required dependents (transitively) lose their enabled state in the active slot too.
 * Linked optionals only stop, since they can load fine without this plugin next start.
 *
 * Throws when the plugin is essential.
 */
context(host: HostScope)
internal fun disablePlugin(pluginId: String) {
    val toDisable = cascadeDisable(pluginId, pluginRegistry.knownManifests(), ::isEssential)

    PluginStatesStore.batchSave {
        for (id in toDisable) {
            val plugin = loaded[id]
            val currentFlags = plugin?.scope?.flags?.value ?: emptySet()
            val newFlags = currentFlags.filter { it.persistAfterDisable }.toSet()
            writeActiveSlotFlags(id, newFlags)

            // Sync running instances for the boot slot as well.
            loaded[id]?.let { it.scope.flags.value = newFlags }
        }
    }

    stopPlugin(pluginId)
}

internal fun pluginEnablementFlags(requiredByUser: Boolean) = buildSet {
    add(PluginFlags.ENABLED)
    if (requiredByUser) add(PluginFlags.REQUIRED_BY_USER)
}

context(host: HostScope)
internal fun enablePlugin(pluginId: String, requiredByUser: Boolean) =
    writeActiveSlotFlags(pluginId, pluginEnablementFlags(requiredByUser))

private fun isEssential(pluginId: String): Boolean =
    pluginRegistry.factories[pluginId]?.let { InternalPluginFlags.ESSENTIAL in it.internalFlags } ?: false

private fun isPluginEnabled(pluginId: String, factory: PluginFactory?): Boolean {
    loaded[pluginId]?.let { return PluginFlags.ENABLED in it.scope.flags.value }
    val active = PluginStatesStore.active
    if (active.isPluginEnabled(pluginId)) return true
    if (factory == null) return false
    return InternalPluginFlags.ESSENTIAL in factory.internalFlags ||
            (InternalPluginFlags.ENABLED_BY_DEFAULT in factory.internalFlags &&
                    !active.hasPlugin(pluginId))
}

/** Writes the slot the user chose, which is what applies on the next boot, and tells JS. */
context(host: HostScope)
internal fun writeActiveSlotFlags(pluginId: String, flags: Iterable<PluginFlags>) {
    val newFlags = flags.toSet()
    PluginStatesStore.active.write(pluginId, newFlags)
    dispatchStateToJS(PluginStatesStore.activeSlotId, pluginId, newFlags)
}

/** Drops the plugin's entry from the slot this boot runs on. */
internal fun clearBootSlotFlags(pluginId: String) = PluginStatesStore.boot.remove(pluginId)

internal fun unsatisfiedDependenciesMessage(pluginId: String, problems: List<DependencyProblemPayload>): String {
    val details = problems.joinToString(", ") { p ->
        val disabled = if (p.installed != null && !p.enabled) ", disabled" else ""
        "\"${p.id}\" (requires ${p.required}, installed: ${p.installed ?: "none"}$disabled)"
    }
    return "Cannot enable plugin \"$pluginId\": unsatisfied dependencies: $details"
}

internal fun PluginFactory.dependencyProblems(
    factories: Map<String, PluginFactory>,
): List<DependencyProblemPayload> = manifest.dependencies.mapNotNull { (depId, dep) ->
    if (dep.optional) return@mapNotNull null

    fun problem(installed: Version?, depEnabled: Boolean) = DependencyProblemPayload(
        id = depId,
        required = dep.version.toString(),
        installed = installed?.toString(),
        enabled = depEnabled,
    )

    val depFactory = factories[depId]
    val installed = depFactory?.manifest?.version
    when {
        installed == null -> problem(null, false)
        !dep.version.satisfies(installed) -> problem(installed, isPluginEnabled(depId, depFactory))
        !isPluginEnabled(depId, depFactory) -> problem(installed, false)
        else -> null
    }
}
