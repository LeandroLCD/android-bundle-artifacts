package com.leandrolcd.bundleartifacts

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * El módulo `:sample` nunca puede quedar incluido en una publicación: se configura antes de que
 * exista el plugin en `mavenLocal()`, así que un `publish*` que lo incluya falla siempre.
 * Esto pasó de verdad y tumbó el release de 0.1.0 en JitPack, así que el comando de publicación
 * lleva el flag explícito y `settings.gradle.kts` lo descarta por las tresvias.
 */
class SamplePublishingGuardTest {

    private val root = File(System.getProperty("user.dir"))

    @Test
    fun `the jitpack install command never configures the sample`() {
        val install = root.resolve("jitpack.yml")
            .readLines()
            .first { it.trimStart().startsWith("install:") }

        assertTrue(
            install.contains("-PskipSample=true"),
            "El `install` de jitpack.yml publica el plugin: sin `-PskipSample=true`, `:sample` se " +
                "configura antes de que exista el plugin en mavenLocal() y el build falla. Actual: $install"
        )
    }

    @Test
    fun `the sample is excluded automatically when publishing or on CI`() {
        val settings = root.resolve("settings.gradle.kts").readText()

        listOf("publish", "CI", "skipSample").forEach { trigger ->
            assertTrue(
                settings.contains(trigger),
                "settings.gradle.kts tiene que excluir `:sample` por `$trigger`; si no, el comando " +
                    "de publicación depende de que cada uno se acuerde del flag."
            )
        }
    }
}