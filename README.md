# android-bundle-artifacts

Gradle plugin para Android App Bundles. Copia `mapping.txt` y
`native-debug-symbols-<variant>.zip` **junto al `.aab`** que acabás de generar, e imprime un
bloque con enlaces clicables en la salida de Gradle.

Sin configuración. Un renglón en el `plugins {}` y listo.

```
╔════════════════════════════════════════════════════════════════════╗
║        📦  Bundle listo — release  (versionCode 48, 1.6.0)         ║
╚════════════════════════════════════════════════════════════════════╝
   ▸ 📂 Ver archivos:               file:///…/app/build/outputs/bundle/release
   ▸ 🗺 Ver mapping:                file:///…/release/mapping.txt
   ▸ 🧬 Ver native debug symbols:   file:///…/native-debug-symbols-release.zip
```

## Instalación

El plugin se publica con [JitPack](https://jitpack.io). Agregá el repo a
`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
        google()
    }
}
```

Y el plugin al `build.gradle.kts` del módulo:

```kotlin
plugins {
    id("com.android.application")
    id("com.github.LeandroLCD.android-bundle-artifacts") version "v1.0.0"
}
```

Listo. No hay nada más que tocar: ni `android { }`, ni `buildTypes { }`.

> **Desarrollo local:** en vez de la versión publicada podés usar
> `pluginManagement { includeBuild("../android-bundle-artifacts") }`.

## Qué hace por defecto

Para cada variant que se distribuye:

| Artefacto | Condición | Por qué |
|---|---|---|
| `mapping.txt` | el build type tiene `isMinifyEnabled = true` | AGP no genera mapping sin ofuscación, y Play Console solo lo pide para builds ofuscados |
| `native-debug-symbols-<variant>.zip` | la variant tiene librerías `.so` | Play Console lo pide para cualquier build que distribuya código nativo |
| Bloque de enlaces | siempre que exista el `.aab` | es la notificación de "bundle listo" |

Las variants cuyo **build type se llama `debug`** quedan fuera: no es un build de
distribución y ensuciaría la consola en cada `assembleDebug`.

Ojo con un detalle: la exclusión es por *nombre de build type*, no por `debuggable`. Un
build type tipo `control-max` es debuggable pero sí se distribuye, así que se incluye.

## Configuración opcional

Todo se ajusta con gradle properties — no hace falta escribir Kotlin.

```properties
# app/gradle.properties o ~/.gradle/gradle.properties
bundleArtifacts.enabled=true
bundleArtifacts.includeVariants=            # vacío = todas
bundleArtifacts.excludeVariants=debug       # default
bundleArtifacts.mapping=auto                # auto | always | never
bundleArtifacts.symbols=auto                # auto | always | never
bundleArtifacts.locale=es                   # es | en; default = locale del proceso JVM
```

| Propiedad | Valores | Default |
|---|---|---|
| `bundleArtifacts.enabled` | `true` / `false` | `true` |
| `bundleArtifacts.includeVariants` | lista separada por comas (allow-list) | vacío = todas |
| `bundleArtifacts.excludeVariants` | lista separada por comas | `debug` |
| `bundleArtifacts.mapping` | `auto` (solo si hay minify) · `always` · `never` | `auto` |
| `bundleArtifacts.symbols` | `auto` (solo no excluidas) · `always` (también excluidas) · `never` | `auto` |
| `bundleArtifacts.locale` | `es` · `en` | locale de la JVM |

El idioma también se puede forzar con la variable de entorno
`BUNDLE_ARTIFACTS_LOCALE`. Cualquier valor distinto de `es` cae a inglés.

## DSL Kotlin (avanzado)

Si preferís configurar por código, hay una extensión `bundleArtifacts` disponible dentro de
`android.defaultConfig` y de cada `android.buildTypes.<name>`:

```kotlin
android {
    buildTypes {
        release {
            (this as ExtensionAware).extensions
                .configure<BundleArtifactsExtension>("bundleArtifacts") {
                    automaticGeneracionFiles.set(true)   // master switch
                    automaticGeneracionMapping.set(true)
                    automaticGeneracionNativeDebugSymbols.set(true)
                }
        }
    }
}
```

Precedencia: `buildType` → `defaultConfig` → gradle properties → política por defecto.
El master switch `automaticGeneracionFiles` gana sobre los otros dos: si vale `true`,
genera ambos artefactos y los flags individuales no pueden apagarlo.

## Qué subir a Play Console

| Link del bloque | Dónde va |
|---|---|
| `.aab` | Play Console → Producción → Crear release → Subir AAB |
| `mapping.txt` | App bundle explorer → `<versionCode>(<versionName>)` → Descargas → Archivo de asignación |
| `native-debug-symbols-*.zip` | App bundle explorer → `<versionCode>(<versionName>)` → Descargas → Símbolos de depuración nativos |

## FAQ

**¿Por qué no hay `mapping.txt` en mi build?**
Porque esa build type no tiene `isMinifyEnabled = true`. AGP no genera mapping sin
ofuscación, y Play Console no lo pide. Forzal con `-PbundleArtifacts.mapping=always` no
ayuda: el archivo no existe.

**Apareció el aviso "mapping.txt was not generated".**
Pasa cuando pediste mapping para una variant no ofuscada. Es informativo.

**Apareció "no native libraries (.so)".**
Solo a nivel `info`. Significa que la app no empaqueta código nativo: no hay nada que
subir.

**¿Por qué el bloque dice "clic derecho → Reveal in Finder"?**
Porque un enlace `file://` en la consola de Gradle no puede abrir el explorador del
sistema operativo. Desde Android Studio los paths salen clicables en el Build output.

**¿Se puede usar con Android Gradle Plugin 8?**
No. El plugin usa el Variant API de AGP 9. Con AGP 8 no registra tareas y lo avisa en la
consola.

## Requisitos

- AGP 9.0 o superior
- Gradle 9.x
- JDK 17 o superior
- Solo aplica a módulos `com.android.application` (no a libraries)

## Desarrollo

```bash
./gradlew build          # compila + tests unitarios
./gradlew functionalTest # tests de integración con Gradle TestKit (necesita Android SDK)
```

## Publicar

La versión vive en la clave `version` de `gradle.properties`. El Release Pipeline la lee de ahí,
valida que el tag sea nuevo y mayor al último, y crea el tag + el GitHub Release al mergear el PR.

```bash
# 1. bumpear la versión en gradle.properties
#    version=0.0.2

# 2. PR de develop a main → el pipeline publica solo
```

Flujo completo del pipeline (`.github/workflows/release.yml`):

| Paso | Qué hace | Cuándo |
|---|---|---|
| Unit Tests | `./gradlew test` | en cada push al PR |
| Functional Tests | `./gradlew functionalTest` (TestKit + AGP real) | en cada push al PR |
| Check Tag | valida que `version` sea > último tag y que el tag no exista | en cada push al PR |
| Build JAR & POM | `jar` + POM de `pluginMaven` | al mergear |
| Create Release | crea el tag y el GitHub Release con el JAR | al mergear |
| JitPack Build | dispara el build y muestra el log | al mergear |

Detalles que importan:

- El **tag es lo que dispara JitPack**. Crear el tag a mano (como se hizo con `v0.0.1`) también
  publica, pero se saltea la validación de versión.
- Se publica la publicación `pluginMaven`, que `java-gradle-plugin` + `maven-publish` crean
  automáticamente. El consumo es por `plugins { id(...) version ... }`, no por `dependencies`.
- Si JitPack falla, el log está en
  `https://jitpack.io/com/github/LeandroLCD/android-bundle-artifacts/<version>/build.log`.
  Un `429 Too Many Requests` de Maven Central es un rate limit de su lado: se reintenta
  moviendo el tag a un commit nuevo o desde el botón *Rebuild* de jitpack.io.

## Licencia

[Apache 2.0](LICENSE)