import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.application) apply false
}

group = "com.github.LeandroLCD"

version = providers.gradleProperty("version").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}


dependencies {
    compileOnly(libs.android.gradle.plugin)
    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("androidBundleArtifacts") {
            id = "com.github.LeandroLCD.android-bundle-artifacts"
            implementationClass = "com.leandrolcd.bundleartifacts.BundleArtifactsPlugin"
            displayName = "Android Bundle Artifacts"
            description = "Copies mapping.txt and native-debug-symbols next to the generated " +
                ".aab and prints clickable links in the build output."
            tags = listOf(
                "android",
                "app-bundle",
                "aab",
                "r8",
                "mapping",
                "native-debug-symbols",
                "play-console"
            )
        }
    }
}

// `java-gradle-plugin` + `maven-publish` crean la publicación `pluginMaven`, que es la que
// publica JitPack. No crearla a mano: el nombre chocaría.

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Los tests de TestKit son aparte: `test` corre solo los rápidos.
// `withPluginClasspath()` inyecta el plugin en un classloader propio, aislado del buildscript
// donde vive AGP. Hay que sumarle las dependencias compileOnly del plugin o no encuentra las
// clases del Variant API.
val pluginUnderTestDependencies: Configuration =
    configurations.create("pluginUnderTestDependencies") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(configurations.compileOnly.get())
    }

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(pluginUnderTestDependencies)
}

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("functional") }
}

/**
 * Tests de integración con Gradle TestKit sobre un proyecto Android real.
 * Son lentos (descargan AGP, corren R8) y necesitan un Android SDK, así que viven aparte
 * de `test` y se corren explícitamente con `./gradlew functionalTest`.
 */
val functionalTest = tasks.register<Test>("functionalTest") {
    group = "verification"
    description = "Tests de integración con TestKit (requiere Android SDK)"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("functional") }
    shouldRunAfter(tasks.named("test"))
    maxParallelForks = 1
}

/**
 * Sincroniza el plugin que consume el módulo `:sample`.
 *
 * El sample es un submódulo de este mismo build, y un plugin producido por `java-gradle-plugin`
 * no se puede aplicar desde acá: Gradle rechaza las script classpath dependencies del mismo
 * build. Así que el sample lo toma de `mavenLocal()`, y eso obliga a dos pasos:
 *
 *   1. publicar el plugin;
 *   2. purgar el jar cacheado, porque Gradle no vuelve a leer `~/.m2` mientras el módulo no
 *      cambie de versión.
 *
 * Por eso son dos invocaciones: la classpath del script de `:sample` se resuelve en la fase de
 * configuración, antes de que corra ninguna tarea. Publicar en el mismo comando que consume no
 * sirve de nada, porque el jar ya está resuelto.
 */
val sampleSync = tasks.register("sampleSync") {
    group = "sample"
    description = "Publica el plugin en mavenLocal y purga su copia cacheada"
    dependsOn(tasks.named("publishToMavenLocal"))
    doLast {
        val cached = gradle.gradleUserHomeDir
            .resolve("caches/modules-2/files-2.1/com.github.LeandroLCD.android-bundle-artifacts")
        if (cached.deleteRecursively()) {
            logger.lifecycle("sampleSync: purgado ${cached.relativeTo(gradle.gradleUserHomeDir)}")
        }
    }
}

/**
 * El loop de desarrollo del sample, en un solo comando y siempre con el código recién
 * publicado: sincroniza el plugin y después compila el bundle.
 *
 * Son dos procesos de Gradle, no dos tareas, porque la classpath del script de `:sample` se
 * resuelve en la fase de configuración: publicar y consumir en el mismo proceso usaría el jar
 * viejo. El primero publica con `-PskipSample=true` porque, si la versión del sample todavía no
 * está en mavenLocal, configurar `:sample` fallaría y no se podría ni publicar.
 *
 *     ./gradlew sampleBundle
 *     ./gradlew sampleBundle -Pargs=:sample:bundleQaDevice
 */
val sampleBundle = tasks.register("sampleBundle") {
    group = "sample"
    description = "Sincroniza el plugin y compila un bundle del módulo :sample (ver -Pargs)"

    val args = providers.gradleProperty("args").getOrElse(":sample:bundleRelease")

    fun runGradle(vararg command: String) {
        val process = ProcessBuilder(*command)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
        if (process.waitFor() != 0) {
            throw GradleException("sampleBundle falló: ${command.joinToString(" ")}")
        }
    }

    doLast {
        runGradle("./gradlew", "-PskipSample=true", "sampleSync")
        runGradle("./gradlew", args)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "android-bundle-artifacts",
            "Implementation-Version" to project.version
        )
    }
}