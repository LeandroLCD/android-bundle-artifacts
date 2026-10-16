package com.leandrolcd.bundleartifacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariantPolicyTest {

    private fun decide(
        variant: String = "release",
        buildType: String = "release",
        minify: Boolean = true,
        enabled: Boolean = true,
        include: Set<String> = emptySet(),
        exclude: Set<String> = VariantPolicy.DEFAULT_EXCLUDE,
        mapping: Mode = Mode.AUTO,
        symbols: Mode = Mode.AUTO
    ) = VariantPolicy.decide(variant, buildType, minify, enabled, include, exclude, mapping, symbols)

    @Test
    fun `minified variant gets mapping and symbols`() {
        val decision = decide(minify = true)

        assertTrue(decision.generateMapping)
        assertTrue(decision.generateSymbols)
        assertTrue(decision.hasWork)
    }

    @Test
    fun `non minified variant gets only symbols`() {
        val decision = decide(minify = false)

        assertFalse(decision.generateMapping)
        assertTrue(decision.generateSymbols)
    }

    @Test
    fun `debug build type is excluded by default`() {
        val decision = decide(variant = "debug", buildType = "debug", minify = true)

        assertFalse(decision.generateMapping)
        assertFalse(decision.generateSymbols)
        assertFalse(decision.hasWork)
    }

    @Test
    fun `control-max is not excluded even though it is debuggable`() {
        val decision = decide(variant = "control-max", buildType = "control-max", minify = false)

        assertFalse(decision.generateMapping)
        assertTrue(decision.generateSymbols)
    }

    @Test
    fun `disabled plugin does nothing`() {
        val decision = decide(enabled = false)

        assertEquals(VariantDecision.NOTHING, decision)
    }

    @Test
    fun `include acts as an allow list`() {
        val include = setOf("release")

        assertTrue(decide(include = include).hasWork)
        assertFalse(decide(variant = "demo", buildType = "demo", include = include).hasWork)
        assertFalse(decide(variant = "dev", buildType = "dev", include = include).hasWork)
    }

    @Test
    fun `exclude can be emptied`() {
        val decision = decide(
            variant = "debug",
            buildType = "debug",
            minify = true,
            exclude = emptySet()
        )

        assertTrue(decision.generateMapping)
        assertTrue(decision.generateSymbols)
    }

    @Test
    fun `mapping mode NEVER disables it`() {
        assertFalse(decide(mapping = Mode.NEVER).generateMapping)
    }

    @Test
    fun `mapping mode ALWAYS still requires the mapping to exist`() {
        assertTrue(decide(minify = true, mapping = Mode.ALWAYS).generateMapping)
        assertFalse(decide(minify = false, mapping = Mode.ALWAYS).generateMapping)
    }

    @Test
    fun `mapping mode ALWAYS reaches excluded variants but AUTO does not`() {
        assertFalse(decide(variant = "debug", buildType = "debug", mapping = Mode.AUTO).generateMapping)
        assertTrue(decide(variant = "debug", buildType = "debug", mapping = Mode.ALWAYS).generateMapping)
    }

    @Test
    fun `symbols mode ALWAYS reaches excluded variants but AUTO does not`() {
        assertFalse(decide(variant = "debug", buildType = "debug", symbols = Mode.AUTO).generateSymbols)
        assertTrue(decide(variant = "debug", buildType = "debug", symbols = Mode.ALWAYS).generateSymbols)
        assertFalse(decide(symbols = Mode.NEVER).generateSymbols)
    }

    @Test
    fun `names are compared case insensitively`() {
        assertFalse(decide(variant = "DEBUG", buildType = "DEBUG").hasWork)
    }

    @Test
    fun `include also matches the build type name`() {
        assertTrue(decide(variant = "freeRelease", buildType = "release", include = setOf("release")).hasWork)
    }
}