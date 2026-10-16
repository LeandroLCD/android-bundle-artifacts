package com.leandrolcd.bundleartifacts

import org.gradle.api.provider.Property

/**
 * Extensión Kotlin opcional — **no hace falta usarla**.
 *
 * Se registra como `bundleArtifacts` dentro de `android.defaultConfig` y de cada
 * `android.buildTypes.<name>` para quien prefiera configurar por código en vez de por
 * gradle properties.
 *
 * Las tres propiedades NO llevan `convention(...)` a propósito: un `Property<Boolean>` sin
 * valor permite distinguir "no seteado" de "seteado a false", que es lo que hace posible el
 * fallback `buildType -> defaultConfig -> gradle property -> política por defecto`.
 */
abstract class BundleArtifactsExtension {

    /**
     * Master switch: si es `true` genera mapping.txt Y native-debug-symbols, y pisa los
     * otros dos flags.
     */
    abstract val automaticGeneracionFiles: Property<Boolean>

    /** Fuerza (o desactiva con `false`) la generación de `mapping.txt`. */
    abstract val automaticGeneracionMapping: Property<Boolean>

    /** Fuerza (o desactiva con `false`) la generación del zip de native debug symbols. */
    abstract val automaticGeneracionNativeDebugSymbols: Property<Boolean>
}