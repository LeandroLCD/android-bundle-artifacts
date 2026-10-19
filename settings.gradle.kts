pluginManagement {
    repositories {
        // Primero de todo: el módulo `:sample` consume el plugin publicado localmente
        // (ver `sampleSync`). Si esto no va primero, gana el tag de JitPack.
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Mirror oficial de Maven Central en Google Cloud Storage. Va PRIMERO a propósito:
        // JitPack resuelve desde una IP compartida que Maven Central suele rate-limitar con
        // 429, y resolver primero contra el mirror evita el request al original.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        // Mirror oficial de Maven Central en Google Cloud Storage. Va PRIMERO a propósito:
        // JitPack resuelve desde una IP compartida que Maven Central suele rate-limitar con
        // 429, y resolver primero contra el mirror evita el request al original.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "android-bundle-artifacts"

// ─────────────────────────────────────────────────────────────────────────────
// Inclusión de `:sample`
//
// El módulo `:sample` existe solo para probar el plugin con builds Android reales (ver
// `sample/README.md`): aplica el plugin desde `mavenLocal()` y no aporta nada a los tests.
//
// Por eso nunca puede quedar incluido en una publicación. La classpath del script de `:sample`
// se resuelve en la fase de configuración —antes de que corra la tarea que publica el plugin—, así
// que incluirlo en un `publish*` falla siempre: al configurar, `mavenLocal()` todavía está vacío.
//
// Se excluye entonces por cualquier motivo de estos tres (gana el primero que aplique):
//   1. `-PskipSample=true`, explícito.
//   2. La invocación pide publicar (`publish*`). Es el caso de JitPack.
//   3. `CI=true`. En una máquina limpia `mavenLocal()` arranca vacío y el sample tampoco aporta.
// ─────────────────────────────────────────────────────────────────────────────

val skipSampleByProperty = providers.gradleProperty("skipSample").map { it.toBoolean() }.getOrElse(false)
val publishRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').startsWith("publish")
}
val runningOnCi = System.getenv("CI")?.toBoolean() == true

if (skipSampleByProperty || publishRequested || runningOnCi) {
    logger.lifecycle(
        "settings: `:sample` fuera del build " +
            "(skipSample=$skipSampleByProperty, publish=$publishRequested, CI=$runningOnCi)"
    )
} else {
    include(":sample")
}
