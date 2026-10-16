package com.leandrolcd.bundleartifacts

/** Un juego de textos para un idioma. */
data class ConsoleMessages(
    val ready: String,
    val viewFiles: String,
    val viewMapping: String,
    val viewSymbols: String,
    val revealHint: String,
    val masterSwitchOn: String,
    val mappingMissing: String,
    val symbolsMissing: String,
    val aabMissing: String,
    val noNativeLibs: String,
    val cleanFailed: String
)

/**
 * Textos del bloque de consola. Cualquier idioma que no sea `es` cae a inglés.
 */
object Messages {

    fun of(tag: String?): ConsoleMessages =
        if (tag?.trim().equals("es", ignoreCase = true)) ES else EN

    private val ES = ConsoleMessages(
        ready = "Bundle listo",
        viewFiles = "📂 Ver archivos:",
        viewMapping = "🗺 Ver mapping:",
        viewSymbols = "🧬 Ver native debug symbols:",
        revealHint = "Para revelar en Finder/Explorer: clic derecho sobre el archivo en el " +
            "árbol del proyecto → Reveal in Finder / Show in Files.",
        masterSwitchOn = "ℹ️  Master switch automaticGeneracionFiles = true: se generaron " +
            "mapping.txt y native-debug-symbols.",
        mappingMissing = "no se generó mapping.txt: la variante no tiene ofuscación " +
            "(isMinifyEnabled = false).",
        symbolsMissing = "no se generaron native debug symbols: no se encontraron librerías " +
            "nativas (.so) para %s.",
        aabMissing = "No se encontró el AAB de %s: se omite el bloque de enlaces.",
        noNativeLibs = "no hay librerías nativas (.so) en %s.",
        cleanFailed = "no se pudo borrar el %s de una corrida anterior."
    )

    private val EN = ConsoleMessages(
        ready = "Bundle ready",
        viewFiles = "📂 View files:",
        viewMapping = "🗺 View mapping:",
        viewSymbols = "🧬 View native debug symbols:",
        revealHint = "To reveal in Finder/Explorer: right-click the file in the project tree " +
            "→ Reveal in Finder / Show in Files.",
        masterSwitchOn = "ℹ️  Master switch automaticGeneracionFiles = true: mapping.txt and " +
            "native-debug-symbols were both generated.",
        mappingMissing = "mapping.txt was not generated: the variant has obfuscation " +
            "disabled (isMinifyEnabled = false).",
        symbolsMissing = "native debug symbols were not generated: no native libraries " +
            "(.so) found for %s.",
        aabMissing = "No AAB found for %s: skipping the links block.",
        noNativeLibs = "there are no native libraries (.so) in %s.",
        cleanFailed = "could not delete the %s left by a previous run."
    )
}