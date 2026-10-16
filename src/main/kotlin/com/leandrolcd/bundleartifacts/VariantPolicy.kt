package com.leandrolcd.bundleartifacts

/** Cómo interpretar un flag de la política por defecto. */
enum class Mode {
    /** Solo aplica a variantes que no estén excluidas. */
    AUTO,

    /** Aplica a todas las variantes, incluidas las excluidas. */
    ALWAYS,

    /** Nunca aplica. */
    NEVER
}

/** Qué hay que hacer con una variant concreta. */
data class VariantDecision(
    val generateMapping: Boolean,
    val generateSymbols: Boolean
) {
    /** `true` cuando hay algo que hacer y vale la pena registrar la tarea. */
    val hasWork: Boolean get() = generateMapping || generateSymbols

    companion object {
        val NOTHING = VariantDecision(generateMapping = false, generateSymbols = false)
    }
}

/**
 * Política por defecto: decide qué artefactos generar para cada variant sin que el usuario
 * tenga que configurar nada.
 *
 * Es una función pura a propósito: toda la lógica testeable vive acá y no en el plugin.
 */
object VariantPolicy {

    /** Variantes excluidas si el usuario no dice otra cosa. */
    val DEFAULT_EXCLUDE: Set<String> = setOf("debug")

fun decide(
        variantName: String,
        buildTypeName: String,
        minifyEnabled: Boolean,
        enabled: Boolean = true,
        include: Set<String> = emptySet(),
        exclude: Set<String> = DEFAULT_EXCLUDE,
        mappingMode: Mode = Mode.AUTO,
        symbolsMode: Mode = Mode.AUTO,
        debuggable: Boolean = false
    ): VariantDecision {
        if (!enabled) return VariantDecision.NOTHING

        val variant = variantName.lowercase()
        val buildType = buildTypeName.lowercase()
        val included = include.map { it.lowercase() }.toSet()
        val excluded = exclude.map { it.lowercase() }.toSet()

        // Una lista de include no vacía funciona como allow-list sobre ambos nombres.
        if (included.isNotEmpty() && variant !in included && buildType !in included) {
            return VariantDecision.NOTHING
        }

        // Una build type `debuggable` nunca se distribuye: es una build de depuración con
        // otra firma, otro backend y, si ofusca, un mapping que no corresponde al release. Por
        // eso se saltea aunque se llame `qaDevice` y aunque esté en la allow-list. El nombre
        // `debug` además está excluido por default, que es la regla histórica.
        //
        // Escape hatch: `mapping=always` / `symbols=always` (o el master switch del DSL) generan
        // igual, para cuando la variante debuggable sí se quiere publicar.
        val isExcluded = variant in excluded || buildType in excluded || debuggable

        val generateMapping = when (mappingMode) {
            Mode.NEVER -> false
            Mode.AUTO -> !isExcluded && minifyEnabled
            Mode.ALWAYS -> minifyEnabled
        }

        val generateSymbols = when (symbolsMode) {
            Mode.NEVER -> false
            Mode.AUTO -> !isExcluded
            Mode.ALWAYS -> true
        }

        return VariantDecision(generateMapping, generateSymbols)
    }
}