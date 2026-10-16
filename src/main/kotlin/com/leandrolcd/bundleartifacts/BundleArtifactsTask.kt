package com.leandrolcd.bundleartifacts

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Deja `mapping.txt` y `native-debug-symbols-<variant>.zip` junto al `.aab` y emite el bloque
 * de enlaces en la consola.
 *
 * Todo el estado entra por `Property`/`Provider` resueltos en tiempo de configuración, así que
 * la acción es segura con el configuration cache: no toca `project`, `tasks` ni el plugin.
 */
@DisableCachingByDefault(because = "copia archivos y escribe en el directorio del AAB, que " +
    "AGP ya declara como output")
abstract class BundleArtifactsTask : DefaultTask() {

    init {
        // Copia + zip + impresión: siempre tiene que correr cuando se bundlea.
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }

    @get:Internal
    abstract val bundleFile: RegularFileProperty

    @get:Internal
    abstract val mappingSource: RegularFileProperty

    @get:Internal
    abstract val mergedNativeLibs: DirectoryProperty

    @get:Internal
    abstract val versionCode: Property<Int>

    @get:Internal
    abstract val versionName: Property<String>

    @get:Internal
    abstract val variantName: Property<String>

    @get:Internal
    abstract val generateMapping: Property<Boolean>

    @get:Internal
    abstract val generateSymbols: Property<Boolean>

    @get:Internal
    abstract val masterSwitchOn: Property<Boolean>

    @get:Internal
    abstract val locale: Property<String>

    @TaskAction
    fun generateBundleArtifacts() {
        val messages = Messages.of(locale.orNull)
        val variant = variantName.get()
        val bundle = bundleFile.orNull?.asFile

        if (bundle == null || !bundle.isFile) {
            logger.warn(String.format(messages.aabMissing, variant))
            return
        }

        val outputDir = bundle.parentFile
        if (outputDir == null || !outputDir.isDirectory) {
            logger.warn(String.format(messages.aabMissing, variant))
            return
        }

        val links = mutableListOf<Pair<String, String>>()
        val warnings = mutableListOf<String>()

        if (generateMapping.get()) {
            val source = runCatching { mappingSource.orNull?.asFile }.getOrNull()
            if (source != null && source.isFile) {
                val target = File(outputDir, MAPPING_FILE_NAME)
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                links += messages.viewMapping to fileUri(target)
            } else {
                // Vale la pena avisar: el usuario pidió mapping y no se generó.
                warnings += messages.mappingMissing
            }
        }

        if (generateSymbols.get()) {
            val zip = zipNativeLibs(variant, outputDir)
            if (zip != null) {
                links += messages.viewSymbols to fileUri(zip)
            } else {
                // Una app sin nativas es lo normal, no un problema: solo info.
                logger.info(
                    String.format(messages.noNativeLibs, mergedNativeLibs.orNull?.asFile ?: variant)
                )
            }
        }

        warnings.forEach { logger.warn("[android-bundle-artifacts] $it") }
        printLinksBlock(messages, links)
    }

    private fun zipNativeLibs(variant: String, outputDir: File): File? {
        val merged = mergedNativeLibs.orNull?.asFile ?: return null
        // AGP expone <...>/merge<Variant>NativeLibs/out; las .so viven en out/lib/<abi>/.
        val libRoot = File(merged, "lib").takeIf { it.isDirectory } ?: merged
        if (!libRoot.isDirectory) return null

        val soFiles = libRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".so") }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()
        if (soFiles.isEmpty()) return null

        val target = File(outputDir, "native-debug-symbols-$variant.zip")
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            soFiles.forEach { soFile ->
                val entryName = "lib/" + soFile.relativeTo(libRoot).invariantSeparatorsPath
                zos.putNextEntry(ZipEntry(entryName))
                soFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return target
    }

    private fun printLinksBlock(
        messages: ConsoleMessages,
        links: List<Pair<String, String>>
    ) {
        val inner = BOX_INNER_WIDTH
        val title = String.format(
            Locale.ROOT,
            "%s — %s  (versionCode %d, %s)",
            messages.ready,
            variantName.get(),
            versionCode.get(),
            versionName.get()
        )

        val bundle = bundleFile.orNull?.asFile
        val entries = buildList {
            bundle?.parentFile?.let { add(messages.viewFiles to fileUri(it)) }
            addAll(links)
        }
        val labelWidth = entries.maxOf { TextLayout.displayWidth(it.first) }

        logger.lifecycle("")
        logger.lifecycle("╔" + "═".repeat(inner) + "╗")
        logger.lifecycle("║" + TextLayout.centerToWidth("📦  $title", inner) + "║")
        logger.lifecycle("╚" + "═".repeat(inner) + "╝")
        entries.forEach { (label, uri) ->
            logger.lifecycle("   ▸ " + TextLayout.padEndToWidth(label, labelWidth) + "   " + uri)
        }
        if (masterSwitchOn.get()) {
            logger.lifecycle("")
            logger.lifecycle("   " + messages.masterSwitchOn)
        }
        logger.lifecycle("")
        logger.lifecycle("   " + messages.revealHint)
        logger.lifecycle("")
    }

    private fun fileUri(file: File): String {
        val path = file.absolutePath.replace('\\', '/')
        return if (path.startsWith("/")) "file://$path" else "file:///$path"
    }

    companion object {
        const val MAPPING_FILE_NAME = "mapping.txt"
        private const val BOX_INNER_WIDTH = 68
    }
}