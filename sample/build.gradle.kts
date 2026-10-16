plugins {
    alias(libs.plugins.android.application)
    // El plugin bajo prueba se consume desde un repositorio, no desde este build: un plugin
    // producido por `java-gradle-plugin` no es visible para sus propios submódulos, y meterlo
    // en `buildscript {}` Gradle lo rechaza ("must reside in a separate build").
    //
    // OJO con el versionado: la versión tiene que coincidir con la clave `version` de
    // gradle.properties, que es la que publica el release pipeline.
    //
    // NO referencies clases del plugin en este archivo (ni `import …BundleArtifactsExtension`,
    // ni `import …bundleArtifacts`). Si el sample usa una API que todavía no está publicada,
    // el build no compila y entonces no se puede ni siquiera publicar el cambio: deadlock.
    // Para probar el DSL Kotlin usá los functional tests (`./gradlew functionalTest`).
    id("com.github.LeandroLCD.android-bundle-artifacts") version libs.versions.bundleArtifacts.get()
}

android {
    namespace = "cl.blipblipcode.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cl.blipblipcode.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // `debug` queda fuera del plugin por default (bundleArtifacts.excludeVariants).
        }

        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }

        // Build type propio, debuggable pero distribuible: el README dice que se incluye
        // igual, así que sirve para verificar esa regla.
        create("qaDevice") {
            initWith(getByName("release"))
            isDebuggable = true
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            // El .so del sample es sintético: sin esto AGP intenta symbol-strippearlo.
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation(libs.appcompat)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
