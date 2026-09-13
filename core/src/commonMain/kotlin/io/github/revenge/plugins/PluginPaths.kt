package io.github.revenge.plugins

import okio.FileSystem
import okio.Path

/**
 * Every path the plugin system owns, rooted at the host app's data directory.
 *
 * The [fs] travels with the paths so tests can hand the stores a `FakeFileSystem` and production
 * hands them `FileSystem.SYSTEM`.
 *
 * ```
 * files/revenge/plugins/
 *   dist/<id>/          installed plugin files
 *   storage/<slot>/<id> per-slot plugin storage
 *   states/             slot pointers and slot files
 *   repos/              repos.json, sources.json and the index caches
 * ```
 */
class PluginPaths(val fs: FileSystem, val dataDir: Path) {
    val root: Path = dataDir / "files" / "revenge" / "plugins"

    /** Installed plugin directories, one per plugin ID. */
    val dist: Path = root / "dist"

    /** Parent of the per-slot storage roots. */
    val storage: Path = root / "storage"

    val states: Path = root / "states"
    val slots: Path = states / "slots"

    /** Pointer file naming the slot the user chose. */
    val activeSlotFile: Path = states / "active"

    /** Pointer file naming the one-shot slot, consumed on the next boot. */
    val nextSlotFile: Path = states / "next"

    val repos: Path = root / "repos"

    /** Plugin storage root for one slot, so slots isolate plugin data as well as flags. */
    fun storageFor(slotId: String): Path = storage / slotId

    /**
     * A plugin's storage directory in every slot that has one, whether or not this boot loaded that slot.
     *
     * Uninstall is a cross-slot action: a directory left behind would resurrect the plugin's data on reinstall.
     */
    fun storageDirsOfAllSlots(pluginId: String): List<Path> = fs.listOrNull(storage).orEmpty()
        .filter { fs.metadataOrNull(it)?.isDirectory == true }
        .map { it / pluginId }
}
