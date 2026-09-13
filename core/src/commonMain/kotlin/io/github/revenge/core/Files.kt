package io.github.revenge.core

import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Writes [target] via a sibling `.tmp` file, then moves it into place.
 *
 * The move is atomic on every filesystem this runs on, so a reader sees either the old contents or
 * the new ones, never a half-written file. Unlike `AtomicFile` there is no backup copy, so callers
 * still need the "corrupt file reads as empty" fallback.
 */
fun FileSystem.atomicWrite(target: Path, block: (BufferedSink) -> Unit) {
    val tmp = target.parent?.resolve("${target.name}.tmp")
        ?: throw IOException("Cannot write to a filesystem root: $target")

    target.parent?.let { createDirectories(it) }
    try {
        write(tmp) { block(this) }
        atomicMove(tmp, target)
    } catch (t: Throwable) {
        runCatching { delete(tmp) }
        throw t
    }
}

/** [atomicWrite] for text, which is every store but the states codec. */
fun FileSystem.atomicWriteUtf8(target: Path, text: String) = atomicWrite(target) { it.writeUtf8(text) }

/** Reads [path] as text, or returns `null` when it is absent or unreadable. */
fun FileSystem.readUtf8OrNull(path: Path): String? =
    runCatching { read(path) { readUtf8() } }.getOrNull()

/** Reads [path] through [block], or returns `null` when it is absent, empty or unreadable. */
fun <T> FileSystem.readOrNull(path: Path, block: (BufferedSource) -> T): T? {
    val size = runCatching { metadataOrNull(path)?.takeIf { it.isRegularFile }?.size }.getOrNull()
    if (size == null || size <= 0L) return null
    return runCatching { read(path) { block(this) } }.getOrNull()
}

/** Names of the regular files directly under [dir], or an empty list when it does not exist. */
fun FileSystem.listFileNames(dir: Path): List<String> =
    listOrNull(dir).orEmpty().filter { metadataOrNull(it)?.isRegularFile == true }.map { it.name }
