# android-bundle-artifacts

[![Release](https://img.shields.io/github/v/tag/LeandroLCD/android-bundle-artifacts?label=v)](https://github.com/LeandroLCD/android-bundle-artifacts/releases/latest)
[![JitPack](https://jitpack.io/com/github/LeandroLCD/android-bundle-artifacts/badge.svg)](https://jitpack.io/#LeandroLCD/android-bundle-artifacts)

Gradle plugin para Android App Bundles. Copia `mapping.txt` y
`native-debug-symbols-<variant>.zip` **junto al `.aab`** que acabás de generar, e imprime un
bloque con enlaces clicables en la salida de Gradle.

Sin configuración. Un renglón en el `plugins {}` y listo.

```
╔════════════════════════════════════════════════════════════════════╗
║        📦  Bundle listo — release  (versionCode 12, 2.1.0)         ║
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

Y el plugin al `build.gradle.kts` del módulo (la versión es el último tag del repo):

```kotlin
plugins {
    id("com.android.application")
    id("com.github.LeandroLCD.android-bundle-artifacts") version "v0.1.1"
}
```

Listo. No hay nada más que tocar: ni `android { }`, ni `buildTypes { }`.

> **Desarrollo local:** en vez de la versión publicada podés usar
> `pluginManagement { includeBuild("../android-bundle-artifacts") }`.

## Qué hace por defecto

Para cada variant que se distribuye:

| Artefacto | Condición | Por qué |
|---|---|---|
| `mapping.txt` | el build type ofusca (`isMinifyEnabled = true` u `optimization { enable = true }`) y no es debuggable | AGP no genera mapping sin ofuscación, y Play Console solo lo pide para builds ofuscados |
| `native-debug-symbols-<variant>.zip` | la variant tiene librerías `.so` y no es debuggable | Play Console lo pide para cualquier build que distribuya código nativo |
| Bloque de enlaces | siempre que exista el `.aab` | es la notificación de "bundle listo" |

Quedan fuera dos cosas:

- Las variants cuyo **build type se llama `debug`**: no es un build de distribución y
  ensuciaría la consola en cada `assembleDebug`.
- Las variants cuyo build type tiene `isDebuggable = true`, se llame como se llame. Una build
  de depuración se firma distinto, apunta a otro backend y, si ofusca, produce un `mapping.txt`
  que no corresponde al release: subirla a Play es un error. own build type tipo `qaDevice`
  debuggable tampoco entra, aunque esté ofuscado.

Si una variante debuggable sí se quiere publicar (un `qa-device` interno, por ejemplo),
`bundleArtifacts.symbols=always` / `mapping=always` la fuerzan: el modo `always` aplica a
variantes excluidas.

## Configuración opcional

Todo se ajusta con gradle properties — no hace falta escribir Kotlin.

```properties
# app/gradle.properties o ~/.gradle/gradle.properties
bundleArtifacts.enabled=true
bundleArtifacts.includeVariants=            # vacío = todas
bundleArtifacts.excludeVariants=debug       # default
bundleArtifacts.mapping=auto                # auto | always | never
bundleArtifacts.symbols=auto                # auto | always | never
bundleArtifacts.clean=true                  # borra los artefactos de corridas anteriores
bundleArtifacts.locale=es                   # es | en; default = locale del proceso JVM
```

| Propiedad | Valores | Default |
|---|---|---|
| `bundleArtifacts.enabled` | `true` / `false` | `true` |
| `bundleArtifacts.includeVariants` | lista separada por comas (allow-list) | vacío = todas |
| `bundleArtifacts.excludeVariants` | lista separada por comas | `debug` |
| `bundleArtifacts.mapping` | `auto` (solo si hay minify) · `always` · `never` | `auto` |
| `bundleArtifacts.symbols` | `auto` (solo no excluidas) · `always` (también excluidas) · `never` | `auto` |
| `bundleArtifacts.clean` | `true` / `false` | `true` |
| `bundleArtifacts.locale` | `es` · `en` | locale de la JVM |

`clean` borra `mapping.txt` y `native-debug-symbols-<variant>.zip` de corridas anteriores antes
de copiar. Sin eso, apagar `mapping` o `symbols` deja el archivo viejo en la carpeta y parece que
lo generó el build actual. Con `bundleArtifacts.clean=false` los archivos previos se respetan
(útil, por ejemplo, para comparar dos builds).

El idioma también se puede forzar con la variable de entorno
`BUNDLE_ARTIFACTS_LOCALE`. Cualquier valor distinto de `es` cae a inglés.

## DSL Kotlin (avanzado)

Si preferís configurar por código, hay una extensión `bundleArtifacts` disponible dentro de
`android.defaultConfig` y de cada `android.buildTypes.<name>`:

```kotlin
import com.leandrolcd.bundleartifacts.bundleArtifacts

android {
    buildTypes {
        release {
            bundleArtifacts {
                automaticGeneracionFiles.set(true)   // master switch
                automaticGeneracionMapping.set(true)
                automaticGeneracionNativeDebugSymbols.set(true)
            }
        }
    }
}
```

`bundleArtifacts { }` es una función de extensión sobre `BuildType` y `DefaultConfig` que el
plugin publica para eso. Sin ella hay que escribir a mano
`extensions.configure<BundleArtifactsExtension>("bundleArtifacts") { … }`, porque Gradle solo
genera accessors para extensiones del contenedor del proyecto y esta vive en un contenedor
anidado dentro del build type.

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
Porque esa build type no ofusca (ni `isMinifyEnabled = true` ni `optimization { enable = true }`). AGP no genera mapping sin
ofuscación, y Play Console no lo pide. Forzal con `-PbundleArtifacts.mapping=always` no
ayuda: el archivo no existe.

**Apareció el aviso "mapping.txt was not generated".**
Pasa cuando pediste mapping para una variant no ofuscada. Es informativo.

**¿Cómo hago que una variante debuggable genere los archivos?**
`bundleArtifacts.symbols=always` (o `mapping=always`). Los modos `always` son la excepción
explícita: alcanzan también a las variantes excluidas y a las debuggables.

**Apareció "no native libraries (.so)".**
Solo a nivel `info`. Significa que la app no empaqueta código nativo: no hay nada que
subir.

**¿El `mapping.txt` que veo en la carpeta era de este build?**
Con `bundleArtifacts.clean=true` (default), sí: el plugin borra los dos artefactos antes de
copiar. Si dejaste el archivo ahí con `clean=false`, es de una corrida anterior.

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

En `sample/` hay un módulo Android (`com.android.application` con `debug`, `release` y
`qaDevice`) que aplica el plugin y comprueba, con builds reales, cada cosa que dice este README:

```bash
./gradlew -PskipSample=true sampleBundle    # sincroniza el plugin y compila el bundle
./sample/verify-readme.sh                  # 16 checks, uno por comportamiento documentado
```

El `-PskipSample=true` va en el comando de afuera a propósito: si al compilar todavía no
publicaste la versión que el sample pide, configurar `:sample` falla y la invocación ni siquiera
arranca.

Dos trampas de ese camino, ya resueltas acá y documentadas en `sample/README.md`:

- Un plugin producido por `java-gradle-plugin` **no** se puede aplicar desde un submódulo del
  mismo build, así que `:sample` lo toma de `mavenLocal()` (que va primero en `pluginManagement`
  para ganarle al tag de JitPack), y hay que publicar antes de cada prueba.
- El pipeline de release corre los tests con `-PskipSample=true`, porque en una máquina
  limpia no hay nada publicado todavía y el sample no aporta a los tests del plugin. Lo mismo
  hace `settings.gradle.kts` solo: descarta `:sample` si la invocación pide publicar (`publish*`,
  que es el caso de JitPack) o si detecta `CI=true`. O sea que ningún `publish*` puede
  terminar configurando el sample, ni aunque alguien se olvide del flag — igual `jitpack.yml`
  lo lleva explícito, porque esa omisión sí tumbó el release de 0.1.0.
- Como la classpath del script de `:sample` se resuelve antes de que corra ninguna tarea,
  publicar y consumir en una sola invocación no funciona: por eso `sampleBundle` lanza dos
  procesos de Gradle (sincroniza, y después compila). Cualquier `bundle*` del sample sí depende
  de `sampleSync`, pero eso solo afecta a la invocación siguiente.
- Por lo mismo, `sample/build.gradle.kts` no puede referenciar clases del plugin: si usara una
  API sin publicar, el build no compilaría y no se podría publicar el cambio. El DSL Kotlin se
  ejercita en los functional tests, que corren en un build aislado.

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