package com.leandrolcd.bundleartifacts

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Copia `mapping.txt` y `native-debug-symbols-<variant>.zip` junto al `.aab` generado e
 * imprime un bloque con enlaces clicables en la salida de Gradle.
 *
 * No hace falta configurar nada: la política por defecto cubre las variantes que se
 * distribuyen. Todo lo opcional se ajusta con gradle properties (ver README).
 *
 * OJO: esta clase no puede mencionar ningún tipo del Variant API de AGP, ni siquiera en
 * la firma de un método o de un lambda. Gradle genera una subclase decorada de cada plugin
 * aplicándolo y, si en ese recorrido no encuentra AGP en el classpath, revienta la
 * aplicación. Por eso todo lo de AGP vive en [AndroidProjectConfigurer].
 */
class BundleArtifactsPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN) {
            try {
                AndroidProjectConfigurer(this).configure()
            } catch (throwable: Throwable) {
                logger.warn(MESSAGE_UNSUPPORTED, throwable)
            }
        }
    }

    companion object {
        const val ANDROID_APPLICATION_PLUGIN = "com.android.application"
        internal const val EXTENSION_NAME = "bundleArtifacts"

        internal const val MESSAGE_UNSUPPORTED =
            "android-bundle-artifacts: requiere el Android Gradle Plugin 9.x. " +
                "El plugin no se aplicó a este proyecto."
    }
}