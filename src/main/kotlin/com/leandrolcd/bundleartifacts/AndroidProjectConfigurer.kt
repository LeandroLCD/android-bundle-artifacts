package com.leandrolcd.bundleartifacts

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import java.util.Locale

/**
 * Todo el wiring con el Variant API de AGP vive acá, en una clase aparte de
 * [BundleArtifactsPlugin]. Ver el KDoc de esa clase para el porqué.
 */
internal class AndroidProjectConfigurer(private val project: Project) {

    fun configure() {
        val androidComponents =
            project.extensions.findByType(ApplicationAndroidComponentsExtension::class.java)
        if (androidComponents == null) {
            project.logger.warn(BundleArtifactsPlugin.MESSAGE_UNSUPPORTED)
            return
        }

        val android = project.extensions.findByType(ApplicationExtension::class.java)
        if (android != null) {
            // Extensión Kotlin opcional, en defaultConfig y en cada build type.
            // `configureEach` es lazy: cubre también los build types creados con `create("…")`.
            registerBundleArtifactsExtension(android.defaultConfig)
            // OJO: `configureEach` es un `Action`, no una lambda con receiver. `this` dentro
            // del bloque sería ESTA clase, no el build type: hay que usar el parámetro.
            android.buildTypes.configureEach { registerBundleArtifactsExtension(it) }
        }

        androidComponents.onVariants { variant ->
            try {
                val name = variant.name
                val buildTypeName = variant.buildType ?: name
                configureVariant(
                    variantName = name,
                    buildTypeName = buildTypeName,
                    bundle = variant.artifacts.get(SingleArtifact.BUNDLE),
                    mapping = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE),
                    mergedNativeLibs = variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS)
                )
            } catch (throwable: Throwable) {
                project.logger.warn(MESSAGE_VARIANT_FAILED, throwable)
            }
        }
    }

    private fun configureVariant(
        variantName: String,
        buildTypeName: String,
        bundle: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        mapping: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        mergedNativeLibs: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>
    ) {
        val config = PluginConfig.from(
            gradleProperty = { project.providers.gradleProperty(it).orNull },
            environmentVariable = { project.providers.environmentVariable(it).orNull }
        )
        val android = project.extensions.findByType(ApplicationExtension::class.java)

        val decision = VariantPolicy.decide(
            variantName = variantName,
            buildTypeName = buildTypeName,
            minifyEnabled = isMinifyEnabled(android, buildTypeName),
            debuggable = isDebuggable(android, buildTypeName),
            enabled = config.enabled,
            include = config.include,
            exclude = config.exclude,
            mappingMode = config.mappingMode,
            symbolsMode = config.symbolsMode
        )

        val overrides = resolveOverrides(android, buildTypeName)
        // El master switch gana: los flags individuales no pueden apagar lo que él encendió.
        val generateMapping = overrides.master ?: overrides.mapping ?: decision.generateMapping
        val generateSymbols = overrides.master ?: overrides.symbols ?: decision.generateSymbols

        if (!generateMapping && !generateSymbols) return

        val artifactsTask =
            project.tasks.register("${variantName}BundleArtifacts", BundleArtifactsTask::class.java)
        artifactsTask.configure { task ->
            task.group = GROUP
            task.description = "Copia mapping.txt / native debug symbols junto al .aab de " +
                "$variantName e imprime los enlaces"

            task.bundleFile.set(bundle)
            task.mappingSource.set(mapping)
            task.mergedNativeLibs.set(mergedNativeLibs)
            task.versionCode.set(android?.defaultConfig?.versionCode ?: 0)
            task.versionName.set(android?.defaultConfig?.versionName.orEmpty())
            task.variantName.set(variantName)
            task.generateMapping.set(generateMapping)
            task.generateSymbols.set(generateSymbols)
            task.masterSwitchOn.set(overrides.master == true)
            task.locale.set(config.locale)
            task.clean.set(config.clean)
        }

        // `matching`/`configureEach` es lazy a propósito: cuando corre onVariants el task
        // `bundle…` de AGP todavía NO existe, y `tasks.named(...)` tiraría UnknownTaskException.
        val bundleTaskName = "bundle" + buildTypeName.replaceFirstChar { it.uppercase() }
        project.tasks.matching { it.name == bundleTaskName }.configureEach { task ->
            task.finalizedBy(artifactsTask)
        }
    }

    /**
     * Si la build type ofusca o no.
     *
     * AGP 9 tiene dos interruptores: el viejo `isMinifyEnabled` y el nuevo bloque
     * `optimization { enable = true }`. Con AGP 9.4 el primero NO refleja al segundo —
     * el build ofusca y `isMinifyEnabled` sigue en false — así que hay que mirar los dos
     * o el plugin deja de copiar el `mapping.txt` sin avisar.
     */
    private fun isMinifyEnabled(android: ApplicationExtension?, buildTypeName: String): Boolean {
        val buildType = android?.buildTypes?.findByName(buildTypeName) ?: return false
        return buildType.isMinifyEnabled || buildType.optimization.enable
    }

    /** Si la build type es depurable (`isDebuggable`): esas variantes no se distribuyen. */
    private fun isDebuggable(android: ApplicationExtension?, buildTypeName: String): Boolean =
        android?.buildTypes?.findByName(buildTypeName)?.isDebuggable == true

    /** Tri-estado por build type -> defaultConfig -> null (deja el mando a la política). */
    private fun resolveOverrides(android: ApplicationExtension?, buildTypeName: String): Overrides {
        val fromDefaultConfig = bundleArtifactsExtension(android?.defaultConfig)
        val fromBuildType = bundleArtifactsExtension(android?.buildTypes?.findByName(buildTypeName))

        return Overrides(
            master = booleanOrNull(fromBuildType?.automaticGeneracionFiles)
                ?: booleanOrNull(fromDefaultConfig?.automaticGeneracionFiles),
            mapping = booleanOrNull(fromBuildType?.automaticGeneracionMapping)
                ?: booleanOrNull(fromDefaultConfig?.automaticGeneracionMapping),
            symbols = booleanOrNull(fromBuildType?.automaticGeneracionNativeDebugSymbols)
                ?: booleanOrNull(fromDefaultConfig?.automaticGeneracionNativeDebugSymbols)
        )
    }

    private data class Overrides(
        val master: Boolean?,
        val mapping: Boolean?,
        val symbols: Boolean?
    )

    private fun bundleArtifactsExtension(target: Any?): BundleArtifactsExtension? =
        (target as? ExtensionAware)?.extensions?.findByType(BundleArtifactsExtension::class.java)

    private fun registerBundleArtifactsExtension(target: Any?) {
        val aware = target as? ExtensionAware ?: return
        if (aware.extensions.findByName(BundleArtifactsPlugin.EXTENSION_NAME) != null) return
        aware.extensions.add(
            BundleArtifactsPlugin.EXTENSION_NAME,
            BundleArtifactsExtension::class.java
        )
    }

    private fun booleanOrNull(value: Property<Boolean>?): Boolean? =
        if (value != null && value.isPresent) value.get() else null

    private companion object {
        const val GROUP = "publishing"
        const val MESSAGE_VARIANT_FAILED =
            "android-bundle-artifacts: no se pudo configurar una variante. Revisá la " +
                "configuración de esa build type."
    }
}

/** Configuración global leída de gradle properties. */
internal data class PluginConfig(
    val enabled: Boolean,
    val include: Set<String>,
    val exclude: Set<String>,
    val mappingMode: Mode,
    val symbolsMode: Mode,
    val locale: String,
    /** Borra los artefactos de corridas anteriores antes de copiar. */
    val clean: Boolean = true
) {
    companion object {
        fun from(
            gradleProperty: (String) -> String?,
            environmentVariable: (String) -> String? = { null }
        ): PluginConfig {
            fun property(name: String) = gradleProperty(name)
            fun boolean(name: String, default: Boolean): Boolean =
                property(name)?.trim()?.toBooleanStrictOrNull() ?: default

            fun mode(name: String, default: Mode): Mode =
                Mode.entries.firstOrNull { it.name.equals(property(name)?.trim(), true) }
                    ?: default

            fun names(name: String): Set<String> =
                property(name)
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    .orEmpty()

            return PluginConfig(
                enabled = boolean("bundleArtifacts.enabled", default = true),
                include = names("bundleArtifacts.includeVariants"),
                exclude = if (property("bundleArtifacts.excludeVariants") != null) {
                    names("bundleArtifacts.excludeVariants")
                } else {
                    VariantPolicy.DEFAULT_EXCLUDE
                },
                mappingMode = mode("bundleArtifacts.mapping", Mode.AUTO),
                symbolsMode = mode("bundleArtifacts.symbols", Mode.AUTO),
                locale = property("bundleArtifacts.locale")
                    ?: environmentVariable("BUNDLE_ARTIFACTS_LOCALE")
                    ?: Locale.getDefault().language,
                clean = boolean("bundleArtifacts.clean", default = true)
            )
        }
    }
}