package io.github.revenge.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun manifest(id: String, vararg dependencies: Pair<String, PluginDependency>) = PluginManifest(
    id = id,
    name = id,
    description = "",
    author = "",
    dependencies = dependencies.toMap(),
    version = Version.parse("1.0.0"),
)

private fun required(id: String) = id to PluginDependency()

private fun optional(id: String) = id to PluginDependency(optional = true)

private fun running(vararg manifests: PluginManifest) = manifests.associateBy { it.id }

/** The cascade has the worst bug history in the loader, so every edge kind gets its own case. */
class CascadeTest {
    @Test
    fun `a plugin nobody depends on only stops itself`() {
        val actions = cascadeStop("a", running(manifest("a"), manifest("b")), { emptySet() })

        assertEquals(listOf("a"), actions.map { it.id })
        assertEquals(null, actions.single().because)
    }

    @Test
    fun `a plugin that isn't running has nothing to stop`() {
        assertTrue(cascadeStop("a", running(manifest("b")), { emptySet() }).isEmpty())
    }

    @Test
    fun `required dependents stop before their dependency`() {
        val actions = cascadeStop(
            "a",
            running(manifest("a"), manifest("b", required("a"))),
            { emptySet() },
        )

        assertEquals(listOf("b", "a"), actions.map { it.id })
        assertEquals(CascadeEdge.Required, actions.first().edge)
        assertEquals("a", actions.first().because)
    }

    @Test
    fun `linked optional dependents stop too`() {
        val actions = cascadeStop(
            "a",
            running(manifest("a"), manifest("b", optional("a"))),
            { emptySet() },
        )

        assertEquals(listOf("b", "a"), actions.map { it.id })
        assertEquals(CascadeEdge.LinkedOptional, actions.first().edge)
    }

    @Test
    fun `optional dependents that never linked keep running`() {
        val actions = cascadeStop(
            "a",
            running(manifest("a"), manifest("b", optional("a"))),
            { if (it == "b") setOf("a") else emptySet() },
        )

        assertEquals(listOf("a"), actions.map { it.id })
    }

    @Test
    fun `the cascade is transitive and deepest first`() {
        val actions = cascadeStop(
            "a",
            running(manifest("a"), manifest("b", required("a")), manifest("c", required("b"))),
            { emptySet() },
        )

        assertEquals(listOf("c", "b", "a"), actions.map { it.id })
    }

    @Test
    fun `a dependency cycle stops each plugin once`() {
        val actions = cascadeStop(
            "a",
            running(manifest("a", optional("b")), manifest("b", required("a"))),
            { emptySet() },
        )

        assertEquals(listOf("a", "b").sorted(), actions.map { it.id }.sorted())
        assertEquals(actions.size, actions.map { it.id }.toSet().size)
    }

    @Test
    fun `disabling takes required dependents with it, but not optionals`() {
        val known = running(
            manifest("a"),
            manifest("b", required("a")),
            manifest("c", required("b")),
            manifest("d", optional("a")),
        )

        assertEquals(setOf("a", "b", "c"), cascadeDisable("a", known, { false }))
    }

    @Test
    fun `essential plugins can't be disabled, directly or through a dependent`() {
        val known = running(manifest("a"), manifest("b", required("a")))

        assertFailsWith<PluginSystemError> { cascadeDisable("a", known, { it == "a" }) }
        val viaDependent = assertFailsWith<PluginSystemError> { cascadeDisable("a", known, { it == "b" }) }

        assertEquals(PluginErrorCodes.NOT_ALLOWED, viaDependent.code)
        assertTrue("'b'" in viaDependent.message.orEmpty())
    }
}
