package com.leandrolcd.bundleartifacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MessagesTest {

    @Test
    fun `es maps to the spanish table`() {
        assertSame(Messages.of("es"), Messages.of("ES"))
        assertSame(Messages.of("es"), Messages.of("  es  "))
    }

    @Test
    fun `anything that is not es falls back to english`() {
        assertSame(Messages.of("en"), Messages.of("en"))
        assertSame(Messages.of("en"), Messages.of("pt-BR"))
        assertSame(Messages.of("en"), Messages.of(""))
        assertSame(Messages.of("en"), Messages.of(null))
    }

    @Test
    fun `the two tables really differ`() {
        assertNotEquals(Messages.of("es").ready, Messages.of("en").ready)
        assertNotEquals(Messages.of("es").viewMapping, Messages.of("en").viewMapping)
    }

    @Test
    fun `every placeholder table has the same number of format specifiers`() {
        val es = Messages.of("es")
        val en = Messages.of("en")

        assertEquals(countSpecifiers(es.symbolsMissing), countSpecifiers(en.symbolsMissing))
        assertEquals(countSpecifiers(es.aabMissing), countSpecifiers(en.aabMissing))
    }

    private fun countSpecifiers(text: String): Int = Regex("%[sd]").findAll(text).count()
}