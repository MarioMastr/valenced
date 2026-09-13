package io.github.revenge.xposed.tweaks.plugins

import io.github.revenge.bridge.asDelegate
import io.github.revenge.plugins.PluginStatesStore
import io.github.revenge.plugins.pluginFlagsFromJSPayload
import io.github.revenge.plugins.toJSPayload
import io.github.revenge.xposed.tweak

/**
 * `revenge.plugins.states.*` bridge methods.
 *
 * A slot is a named set of plugin flags:
 * - [PluginStatesStore.activeSlotId]: the slot the user chose. The UI reads and edits it.
 * - [PluginStatesStore.bootSlotId]: the slot this boot runs on. Plugin scopes read and write it.
 *
 * Both slot IDs are the same on a normal boot.
 * A defaults boot resolves [PluginStatesStore.bootSlotId] to [PluginStatesStore.DEFAULTS_SLOT].
 */
val pluginStates by tweak {
    val paths = pluginPaths(appInfo.dataDir)

    /** `states.read(): Record<slotId, Record<pluginId, PluginStates>>` */
    pluginSystemMethod("revenge.plugins.states.read") {
        PluginStatesStore.ensureLoaded(paths)
        PluginStatesStore.toJSPayload()
    }

    /** `states.getSlots(): { active, oneShot?, slots }` */
    pluginSystemMethod("revenge.plugins.states.getSlots") {
        PluginStatesStore.ensureLoaded(paths)
        buildMap {
            put("active", PluginStatesStore.activeSlotId)
            PluginStatesStore.oneShotSlotId?.let { put("oneShot", it) }
            put("slots", PluginStatesStore.persistentSlotIds(paths))
        }
    }

    /**
     * `states.setActiveSlot(slot, oneShot?)`
     *
     * Applies on the next boot.
     */
    pluginSystemMethod("revenge.plugins.states.setActiveSlot") { args ->
        val argv = args.asDelegate()
        val slot by argv.string()
        val oneShot by argv.booleanOrNull()

        PluginStatesStore.setActiveSlot(paths, slot, oneShot == true)
        null
    }

    /**
     * `states.update(slot, id, PluginStates): PluginStates`
     *
     * JS reporting flags it changed.
     */
    pluginSystemMethod("revenge.plugins.states.update") { args ->
        val argv = args.asDelegate()
        val slotId by argv.string()
        val pluginId by argv.string()
        val states by argv.hashMap()

        val newFlags = pluginFlagsFromJSPayload(states)
        PluginStatesStore.ensureLoaded(paths)
        PluginStatesStore.slot(slotId)?.write(pluginId, newFlags)
        newFlags.toJSPayload()
    }
}
