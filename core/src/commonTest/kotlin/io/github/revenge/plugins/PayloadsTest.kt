package io.github.revenge.plugins

import io.github.revenge.core.RevengeJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun encode(payload: PluginPayload) = RevengeJson.encodeToJsonElement(payload) as JsonObject

private val manifest = PluginManifest(
    id = "a.b",
    name = "A",
    description = "",
    author = "",
    icon = null,
    version = Version.parse("1.0.0"),
)

private val payload = PluginPayload(
    manifest = manifest.toPayload(),
    script = null,
    internal = false,
    essential = false,
    enabledByDefault = false,
    api = false,
    source = null,
)

/** The wire shape is a contract with revenge-next: keys never silently appear or disappear. */
class PayloadsTest {
    @Test
    fun `a plugin entry always carries its keys`() {
        val json = encode(payload)

        assertEquals(
            setOf(
                "manifest", "script", "internal", "essential", "enabledByDefault", "api", "source",
                "unsatisfiedOptionalDependencies", "errors",
            ),
            json.keys,
        )
        assertEquals(MANIFEST_FORMAT_JSON, (json["manifest"] as JsonObject)["format"].toString())
    }

    @Test
    fun `failed only shows up on a discovery failure`() {
        assertFalse("failed" in encode(payload))
        assertTrue("failed" in encode(payload.copy(failed = true)))
    }

    @Test
    fun `an error keeps stack and drops absent details`() {
        val json = RevengeJson.encodeToJsonElement(PluginError(PluginErrorCodes.LOAD_FAILED, "boom")) as JsonObject

        assertEquals(setOf("code", "message", "stack"), json.keys)
    }
}

private const val MANIFEST_FORMAT_JSON = "1"
