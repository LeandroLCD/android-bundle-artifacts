package com.leandrolcd.bundleartifacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginConfigTest {

    private fun config(
        properties: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap()
    ) = PluginConfig.from(
        gradleProperty = properties::get,
        environmentVariable = environment::get
    )

    @Test
    fun `defaults apply when nothing is configured`() {
        val config = config()

        assertTrue(config.enabled)
        assertEquals(emptySet<String>(), config.include)
        assertEquals(setOf("debug"), config.exclude)
        assertEquals(Mode.AUTO, config.mappingMode)
        assertEquals(Mode.AUTO, config.symbolsMode)
        assertTrue(config.clean, "clean debería venir en true")
    }

    @Test
    fun `clean is parsed strictly and defaults to true`() {
        assertEquals(false, config(mapOf("bundleArtifacts.clean" to "false")).clean)
        assertEquals(true, config(mapOf("bundleArtifacts.clean" to "TRUE")).clean)
        assertEquals(true, config(mapOf("bundleArtifacts.clean" to "banana")).clean)
    }

    @Test
    fun `enabled is parsed strictly`() {
        assertEquals(false, config(mapOf("bundleArtifacts.enabled" to "false")).enabled)
        assertEquals(true, config(mapOf("bundleArtifacts.enabled" to "TRUE")).enabled)
        // Un valor basura no rompe el build: cae al default.
        assertEquals(true, config(mapOf("bundleArtifacts.enabled" to "banana")).enabled)
    }

    @Test
    fun `mode is case insensitive and falls back on garbage`() {
        assertEquals(Mode.NEVER, config(mapOf("bundleArtifacts.mapping" to "never")).mappingMode)
        assertEquals(Mode.ALWAYS, config(mapOf("bundleArtifacts.mapping" to " AlWaYs ")).mappingMode)
        assertEquals(Mode.AUTO, config(mapOf("bundleArtifacts.mapping" to "wat")).mappingMode)
        assertEquals(Mode.NEVER, config(mapOf("bundleArtifacts.symbols" to "NEVER")).symbolsMode)
    }

    @Test
    fun `exclude is comma separated and trimmed`() {
        assertEquals(
            setOf("debug", "demo", "dev"),
            config(mapOf("bundleArtifacts.excludeVariants" to " debug , demo,dev ")).exclude
        )
    }

    @Test
    fun `an empty exclude property disables the exclusion`() {
        assertEquals(emptySet<String>(), config(mapOf("bundleArtifacts.excludeVariants" to "")).exclude)
    }

    @Test
    fun `include is comma separated`() {
        assertEquals(
            setOf("release", "qa-device"),
            config(mapOf("bundleArtifacts.includeVariants" to "release, qa-device")).include
        )
    }

    @Test
    fun `locale comes from the gradle property first`() {
        assertEquals(
            "en",
            config(
                properties = mapOf("bundleArtifacts.locale" to "en"),
                environment = mapOf("BUNDLE_ARTIFACTS_LOCALE" to "es")
            ).locale
        )
    }

    @Test
    fun `locale falls back to the environment variable`() {
        assertEquals(
            "es",
            config(environment = mapOf("BUNDLE_ARTIFACTS_LOCALE" to "es")).locale
        )
    }
}