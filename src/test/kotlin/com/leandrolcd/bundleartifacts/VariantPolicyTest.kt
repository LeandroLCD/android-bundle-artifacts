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
        symbols: Mode = Mode.AUTO,
        debuggable: Boolean = false
    ) = VariantPolicy.decide(
        variant, buildType, minify, enabled, include, exclude, mapping, symbols, debuggable
    )

    @Test
    fun `a debuggable build type is skipped even if it is not named debug`() {
        val decision = decide(
            variant = "qaDevice",
            buildType = "qaDevice",
            minify = true,
            debuggable = true
        )

        assertFalse(decision.generateMapping)
        assertFalse(decision.generateSymbols)
        assertFalse(decision.hasWork)
    }

    @Test
    fun `an allow list does not resurrect a debuggable build type`() {
        val decision = decide(
            variant = "qaDevice",
            buildType = "qaDevice",
            minify = true,
            include = setOf("qaDevice"),
            debuggable = true
        )

        assertFalse(decision.hasWork)
    }

    @Test
    fun `symbols ALWAYS is the escape hatch for a debuggable build type`() {
        val decision = decide(
            variant = "qaDevice",
            buildType = "qaDevice",
            minify = true,
            symbols = Mode.ALWAYS,
            debuggable = true
        )

        assertTrue(decision.generateSymbols)
        assertFalse(decision.generateMapping)
    }

    @Test
    fun `mapping ALWAYS still needs minify on a debuggable build type`() {
        val decision = decide(
            variant = "qaDevice",
            buildType = "qaDevice",
            minify = false,
            mapping = Mode.ALWAYS,
            debuggable = true
        )

        assertFalse(decision.generateMapping, "sin minify no hay mapping que copiar")
    }

    @Test
    fun `a debuggable build type can still generate mapping when minified and forced`() {
        val decision = decide(
            variant = "qaDevice",
            buildType = "qaDevice",
            minify = true,
            mapping = Mode.ALWAYS,
            debuggable = true
        )

        assertTrue(decision.generateMapping)
    }

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
    fun `a custom build type is included as long as it is not debuggable`() {
        val decision = decide(variant = "qa-device", buildType = "qa-device", minify = false)

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