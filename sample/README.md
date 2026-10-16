# sample

App Android mínima que aplica el plugin y sirve para comprobar, con builds reales, que lo que
promete el README del plugin funciona de verdad.

Variantes que define, a propósito:

| Variante | Minify | Debuggable | Para qué |
|---|---|---|---|
| `debug` | no | sí | debería quedar fuera por default (`excludeVariants=debug`) |
| `release` | sí | no | el camino principal: mapping + símbolos |
| `qaDevice` | sí | sí | build type propio debuggable: el README dice que **se incluye** |

Además tiene un `.so` sintético en `src/main/jniLibs/` para que las variantes tengan código
nativo, y un `MainActivity` para que R8 tenga algo que ofuscar.

## Correrla

```bash
./gradlew -PskipSample=true sampleBundle          # sincroniza el plugin y compila
./gradlew -PskipSample=true sampleBundle -Pargs=:sample:bundleQaDevice
```

El `-PskipSample=true` del comando de afuera es obligatorio mientras la versión que pide el
sample no esté publicada en `mavenLocal()`: sin él, la configuración de `:sample` falla antes de
que corra ninguna tarea.

Cualquier tarea `bundle*` del módulo depende de `sampleSync` (ver "El loop de desarrollo"), así
que `./gradlew :sample:bundleRelease` también publica el plugin antes de compilar. La diferencia
es sutil pero importa: en una invocación sola, el jar que ya estaba resuelto en configuración es
el que corre, y el recién publicado recién se ve en la **siguiente** invocación. Para probar
cambios del plugin, usá `sampleBundle`, que encadena las dos.

Los artefactos quedan en `sample/build/outputs/bundle/release/`.

## Verificar el README completo

```bash
./sample/verify-readme.sh
```

Un build real por cada propiedad documentada (`enabled`, `includeVariants`, `excludeVariants`,
`mapping`, `symbols`, `clean`, `locale` y env var). Tarda varios minutos.

## El loop de desarrollo

`:sample` es un módulo de este mismo build, y Gradle **no** deja que un submódulo use el plugin
que produce su propio build (rechaza las script classpath dependencies del mismo build). Por eso
el sample consume el plugin publicado en `mavenLocal()`, y eso obliga a dos pasos:

```bash
./gradlew -PskipSample=true sampleSync  # publica + purga el jar cacheado
```

Ojo con el detalle que hace que esto no sea trivial: la classpath del script de `:sample` se
resuelve en la fase de configuración, **antes** de que corra ninguna tarea. Publicar en el mismo
comando que consume no sirve, porque el jar ya está resuelto; y por eso `sampleBundle` lanza dos
procesos de Gradle, no una tarea con dos pasos.

**Regla del sample:** acá no se referencian clases del plugin (ni `import …BundleArtifactsExtension`,
ni `import …bundleArtifacts`). Si el build script usara una API que todavía no está publicada, el
sample no compilaría y entonces no se podría ni publicar el cambio — un deadlock. Para ejercitar
el DSL Kotlin usá los functional tests, que corren en un build aislado con TestKit:

```bash
./gradlew functionalTest
```

## Configuración

- Properties: las de `bundleArtifacts.*` se pasan por CLI o desde `~/.gradle/gradle.properties`,
  p. ej. `./gradlew :sample:bundleRelease -PbundleArtifacts.symbols=always`.
- La versión del plugin que consume el sample está en `gradle/libs.versions.toml`
  (`versions.bundleArtifacts`) y tiene que coincidir con la clave `version` de `gradle.properties`,
  que es la que publica el release pipeline.
