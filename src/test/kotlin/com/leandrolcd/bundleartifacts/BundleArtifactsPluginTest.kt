package com.leandrolcd.bundleartifacts

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BundleArtifactsPluginTest {

    @Test
    fun `applying the plugin to a project without AGP is a no-op`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(BundleArtifactsPlugin::class.java)

        assertTrue(
            project.tasks.names.none { it.endsWith("BundleArtifacts") },
            "no debe registrar tareas sin el plugin de Android"
        )
    }

    @Test
    fun `applying the plugin twice does not fail`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(BundleArtifactsPlugin::class.java)
        project.pluginManager.apply(BundleArtifactsPlugin::class.java)

        assertTrue(project.tasks.names.none { it.endsWith("BundleArtifacts") })
    }

    @Test
    fun `the bundle artifacts task name is derived from the variant name`() {
        assertEquals("releaseBundleArtifacts", taskName("release"))
        assertEquals("qa-deviceBundleArtifacts", taskName("qa-device"))
    }

    @Test
    fun `the bundle task name keeps the build type casing`() {
        assertEquals("bundleRelease", bundleTaskName("release"))
        assertEquals("bundleQa-device", bundleTaskName("qa-device"))
    }

    private fun taskName(variant: String) = "${variant}BundleArtifacts"

    private fun bundleTaskName(buildType: String) =
        "bundle" + buildType.replaceFirstChar { it.uppercase() }
}