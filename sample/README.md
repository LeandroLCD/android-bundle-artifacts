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
./gradlew :sample:bundleRelease          # desde la raíz del repo
```

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
./gradlew sampleSync                    # publica + purga el jar cacheado
./gradlew :sample:bundleRelease         # ahora sí corre contra el código recién publicado
```

Son dos invocaciones porque la classpath del script de `:sample` se resuelve en la fase de
configuración, antes de que corra ninguna tarea: publicar y consuming en el mismo comando usaría
el jar viejo.

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
