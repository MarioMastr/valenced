package io.github.revenge.core

import kotlinx.serialization.json.Json

/** JSON reader/writer for every document the loader owns. Tolerant of unknown keys so old documents keep parsing. */
val RevengeJson: Json = Json { ignoreUnknownKeys = true }
