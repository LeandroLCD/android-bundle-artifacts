package com.leandrolcd.bundleartifacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextLayoutTest {

    @Test
    fun `plain ascii counts one column per char`() {
        assertEquals(12, TextLayout.displayWidth("Bundle listo"))
    }

    @Test
    fun `emoji counts two columns`() {
        assertEquals(2, TextLayout.displayWidth("📦"))
        assertEquals(4, TextLayout.displayWidth("📂📂"))
    }

    @Test
    fun `variation selectors and zero width joiner take no column`() {
        assertEquals(2, TextLayout.displayWidth("📦️"))
        assertEquals(0, TextLayout.displayWidth("‍"))
        assertEquals(0, TextLayout.displayWidth("️"))
    }

    @Test
    fun `box drawing and arrows take one column`() {
        assertEquals(1, TextLayout.displayWidth("╔"))
        assertEquals(1, TextLayout.displayWidth("▸"))
        assertEquals(1, TextLayout.displayWidth("→"))
    }

    @Test
    fun `centerToWidth produces exactly the requested width`() {
        val title = "📦  Bundle listo — release  (versionCode 48, 1.6.0)"
        val centered = TextLayout.centerToWidth(title, 68)

        assertEquals(68, TextLayout.displayWidth(centered))
        assertEquals(title, centered.trim())
    }

    @Test
    fun `centerToWidth never truncates and absorbs negative padding`() {
        val tooLong = "x".repeat(100)
        val centered = TextLayout.centerToWidth(tooLong, 10)

        assertEquals(tooLong, centered)
    }

    @Test
    fun `padEndToWidth aligns labels with emoji`() {
        val padded = TextLayout.padEndToWidth("📂 Ver archivos:", 30)

        assertEquals(30, TextLayout.displayWidth(padded))
        assertEquals("📂 Ver archivos:", padded.trimEnd())
    }

    @Test
    fun `a full box keeps every border on the same column`() {
        val title = TextLayout.centerToWidth("📦  $TITLE", 68)
        val box = listOf("╔" + "═".repeat(68) + "╗", "║$title║", "╚" + "═".repeat(68) + "╝")

        assertEquals(setOf(70), box.map { TextLayout.displayWidth(it) }.toSet())
    }

    private companion object {
        const val TITLE = "Bundle listo — control-max  (versionCode 48, 1.6.0)"
    }
}