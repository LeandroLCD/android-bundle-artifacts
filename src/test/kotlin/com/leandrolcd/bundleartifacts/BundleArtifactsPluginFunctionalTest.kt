package com.leandrolcd.bundleartifacts

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

/**
 * Test de integración real: monta un proyecto Android mínimo y corre `bundleRelease`.
 *
 * Es lento (resuelve AGP, corre R8) y necesita un Android SDK, por eso va aparte de `test`
 * y se corre con `./gradlew functionalTest`.
 *
 * El fixture es un proyecto de UN solo módulo a propósito: `withPluginClasspath()` inyecta el
 * plugin en el buildscript del proyecto raíz, y en un build multi-módulo el classloader del
 * subproyecto (donde vive AGP) sería hermano — no ancestro — del nuestro, y el plugin no
 * vería las clases del Variant API.
 */
@Tag("functional")
class BundleArtifactsPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var androidSdk: File

    @BeforeEach
    fun setUp() {
        androidSdk = File(
            System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: "${System.getProperty("user.home")}/Library/Android/sdk"
        )
        assumeTrue(androidSdk.isDirectory, "necesita un Android SDK para correr")

        writeFixture()
    }

    @Test
    fun `release bundle gets the mapping file next to the aab`() {
        val result = run("bundleRelease")

        assertEquals(TaskOutcome.SUCCESS, result.task(":releaseBundleArtifacts")?.outcome)

        val bundleDir = File(projectDir, "build/outputs/bundle/release")
        val aab = bundleDir.listFiles { file -> file.extension == "aab" }.orEmpty()
        assertEquals(1, aab.size, "no se generó el .aab en $bundleDir")
        assertTrue(
            File(bundleDir, "mapping.txt").isFile,
            "no se copió el mapping.txt junto al .aab"
        )
    }

    @Test
    fun `release bundle gets a native debug symbols zip with the right layout`() {
        run("bundleRelease")

        val zip = File(projectDir, "build/outputs/bundle/release/native-debug-symbols-release.zip")
        assertTrue(zip.isFile, "no se generó el zip de símbolos")

        val entries = ZipFile(zip).use { file -> file.entries().toList().map { it.name } }
        assertEquals(listOf("lib/x86_64/libfixture.so"), entries)
    }

    @Test
    fun `the console block links every generated artifact`() {
        val output = run("bundleRelease").output

        assertTrue(
            output.contains("Bundle ready") || output.contains("Bundle listo"),
            "no se imprimió el bloque de enlaces:\n$output"
        )
        assertTrue(output.contains("Ver archivos") || output.contains("View files"))
        assertTrue(output.contains("Ver mapping") || output.contains("View mapping"))
        assertTrue(output.contains("file://"), "los links tienen que ser file:// para ser clicables")
    }

    @Test
    fun `the locale can be forced to english`() {
        val output = run("bundleRelease", "-PbundleArtifacts.locale=en").output

        assertTrue(output.contains("Bundle ready"), "esperaba el bloque en inglés:\n$output")
    }

    @Test
    fun `debug builds are skipped by default`() {
        val result = run("bundleDebug")

        assertFalse(
            result.output.contains("debugBundleArtifacts"),
            "la variante debug no debería registrarse"
        )
        assertFalse(
            File(projectDir, "build/outputs/bundle/debug").walkTopDown()
                .any { it.name == "mapping.txt" },
            "debug no debería generar mapping.txt"
        )
    }

    @Test
    fun `a second run reuses the configuration cache`() {
        // La entrada se guarda solo si la primera corrida también lleva el flag.
        run("bundleRelease", "--configuration-cache")
        val second = run("bundleRelease", "--configuration-cache")

        val cacheMessages = listOf(
            "Reusing configuration cache",
            "Configuration cache entry reused"
        )
        assertTrue(
            cacheMessages.any { second.output.contains(it) },
            "la segunda corrida debía reusar la configuración cache"
        )
        assertFalse(
            second.output.contains("problems were found storing the configuration cache"),
            "el plugin no debe romper el configuration cache:\n${second.output}"
        )
        assertEquals(TaskOutcome.SUCCESS, second.task(":releaseBundleArtifacts")?.outcome)
    }

    @Test
    fun `the plugin can be disabled from gradle properties`() {
        val output = run("bundleRelease", "-PbundleArtifacts.enabled=false").output

        assertFalse(output.contains("releaseBundleArtifacts"))
    }

    @Test
    fun `a build without the android plugin is not broken`() {
        projectDir.file("build.gradle.kts").writeText("")

        val result = runner("tasks").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    private fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withTestKitDir(File(System.getProperty("user.home"), ".gradle"))
        .withArguments(*arguments, "--stacktrace")

    private fun writeFixture() {
        projectDir.file("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories { google(); mavenCentral() }
            }
            rootProject.name = "fixture"
            """.trimIndent()
        )
        projectDir.file("gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2g
            android.useAndroidX=true
            """.trimIndent()
        )
        projectDir.file("local.properties").writeText("sdk.dir=${androidSdk.absolutePath}\n")

        projectDir.file("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "$AGP_VERSION"
                id("com.github.LeandroLCD.android-bundle-artifacts")
            }

            android {
                namespace = "com.example.fixture"
                compileSdk = 36

                defaultConfig {
                    applicationId = "com.example.fixture"
                    minSdk = 24
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }

                packaging {
                    jniLibs {
                        // Evita que AGP intente symbol-strippear el .so sintético del fixture.
                        keepDebugSymbols += "**/*.so"
                    }
                }
            }
            """.trimIndent()
        )

        // Sin keep rules, R8 borra todo el código del fixture y no queda ningún dex.
        projectDir.file("proguard-rules.pro").writeText("-keep class com.example.fixture.** { *; }\n")
        projectDir.file("src/main/AndroidManifest.xml")
            .writeText("""<?xml version="1.0" encoding="utf-8"?><manifest />""")

        projectDir.file("src/main/java/com/example/fixture/Hello.java").writeText(
            """
            package com.example.fixture;

            public final class Hello {
                public static String greet() {
                    return "hello";
                }
            }
            """.trimIndent()
        )

        val jni = File(projectDir, "src/main/jniLibs/x86_64").apply { mkdirs() }
        File(jni, "libfixture.so").writeBytes(ByteArray(64))
    }

    private fun File.file(relative: String) =
        File(this, relative).apply { parentFile.mkdirs() }

    private companion object {
        /** Versión mínima de AGP que el plugin declara soportar. */
        const val AGP_VERSION = "9.0.1"
    }
}