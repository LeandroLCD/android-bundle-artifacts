package com.leandrolcd.bundleartifacts

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.DefaultConfig
import org.gradle.api.plugins.ExtensionAware

/**
 * Acceso al DSL del plugin desde el Kotlin DSL de Android.
 *
 * Gradle genera accessors type-safe solo para extensiones del contenedor del proyecto, así que
 * sin esto hay que escribir el `extensions.configure<…>("bundleArtifacts") { … }` a mano en
 * cada build type. Con estas funciones queda:
 *
 * ```kotlin
 * android {
 *     buildTypes {
 *         release {
 *             bundleArtifacts {
 *                 automaticGeneracionFiles.set(true)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * La extensión se registra en `defaultConfig` y en cada build type, así que lo mismo funciona
 * para los dos.
 */

/** La extensión del plugin dentro de este build type. */
val BuildType.bundleArtifacts: BundleArtifactsExtension
    get() = bundleArtifactsExtensionOf(this)

/** Configura el plugin para este build type. */
fun BuildType.bundleArtifacts(configure: BundleArtifactsExtension.() -> Unit) {
    bundleArtifactsExtensionOf(this).apply(configure)
}

/** La extensión del plugin dentro de `defaultConfig`. */
val DefaultConfig.bundleArtifacts: BundleArtifactsExtension
    get() = bundleArtifactsExtensionOf(this)

/** Configura el plugin para todas las variantes de la app. */
fun DefaultConfig.bundleArtifacts(configure: BundleArtifactsExtension.() -> Unit) {
    bundleArtifactsExtensionOf(this).apply(configure)
}

private fun bundleArtifactsExtensionOf(target: Any): BundleArtifactsExtension {
    val extensions = (target as? ExtensionAware)?.extensions
        ?: throw MissingBundleArtifactsException(target)
    return extensions.findByType(BundleArtifactsExtension::class.java)
        ?: throw MissingBundleArtifactsException(target)
}

/**
 * El build type no tiene la extensión: casi siempre significa que el plugin no se aplicó a
 * ese módulo (solo anda en `com.android.application`).
 */
class MissingBundleArtifactsException(target: Any) : IllegalStateException(
    "android-bundle-artifacts: no hay extensión 'bundleArtifacts' en ${target.javaClass.name}. " +
        "El plugin se aplica solo a módulos com.android.application."
)
