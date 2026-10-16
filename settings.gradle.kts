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

// El módulo `:sample` consume el plugin desde mavenLocal (un plugin producido por
// `java-gradle-plugin` no se puede aplicar desde su propio build), así que necesita que el
// plugin esté publicado antes. En CI eso no pasa, y el sample tampoco aporta a los tests del
// plugin: por eso se puede excluir con `-PskipSample=true` (es lo que hace el pipeline).
if (!providers.gradleProperty("skipSample").getOrElse("false").toBoolean()) {
    include(":sample")
}
