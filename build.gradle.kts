import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

group = "com.github.LeandroLCD"

// La versión vive en `gradle.properties` (clave `version`) y es la fuente de verdad del
// release pipeline: de ahí lee el tag que tiene que crear.
// JitPack pisa esta propiedad con `-Pversion=<tag>`, así que la precedencia es JitPack > local.
version = providers.gradleProperty("version").get()

// Sin `jvmToolchain`: el auto-provisioning silencioso del toolchain está deprecado y
// Gradle 10 lo va a rechazar. El target se fija explícitamente.
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

    // AGP es compileOnly en el plugin, pero hace falta en el classpath de tests para poder
    // cargar las clases que mencionan tipos del Variant API.
    testImplementation(libs.android.gradle.plugin)

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

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "android-bundle-artifacts",
            "Implementation-Version" to project.version
        )
    }
}