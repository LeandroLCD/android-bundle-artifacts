pluginManagement {
    repositories {
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