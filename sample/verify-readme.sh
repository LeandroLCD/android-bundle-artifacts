#!/usr/bin/env bash
# Verifica, uno por uno, cada comportamiento que promete el README del plugin.
#
#   ./sample/verify-readme.sh
#
# Corre builds reales del módulo `:sample`. Antes de la primera vez, y cada vez que
# cambies el plugin, hay que sincronizarlo (ver el README del repo):
#
#   ./gradlew sampleSync
#
# Tarda varios minutos: cada caso corre R8 de verdad.
set -uo pipefail

cd "$(dirname "$0")/.."
GRADLE=./gradlew
OUT=sample/build/outputs/bundle
PASS=0
FAIL=0

check() { # check <descripción> <resultado 0/1> <detalle>
  if [ "$2" -eq 0 ]; then
    printf '  \033[32mPASS\033[0m  %s\n' "$1"
    PASS=$((PASS + 1))
  else
    printf '  \033[31mFAIL\033[0m  %s — %s\n' "$1" "${3:-}"
    FAIL=$((FAIL + 1))
  fi
}

run() { $GRADLE "$@" >/tmp/sample-gradle.log 2>&1; }
# El plugin borra lo que genera (bundleArtifacts.clean), pero no los .aab viejos: para
# comprobar que un archivo "no aparece" hay que limpiar la carpeta antes.
clean_outputs() { rm -rf "$OUT/$1"; }

# ¿El build de `variant` registró la tarea <variant>BundleArtifacts?
registered() {
  $GRADLE ":sample:bundle$1" --dry-run 2>/dev/null | grep -q ":sample:$1BundleArtifacts"
}

has_file() { [ -f "$1" ]; }

echo "▸ 1. Default: release genera mapping.txt + native-debug-symbols"
run :sample:bundleRelease
check "mapping.txt junto al .aab" \
  "$(has_file $OUT/release/mapping.txt; echo $?)" "$OUT/release/mapping.txt"
check "native-debug-symbols-release.zip" \
  "$(has_file $OUT/release/native-debug-symbols-release.zip; echo $?)"

echo "▸ 2. Default: debug queda excluido"
registered debug && r=1 || r=0
check "debugBundleArtifacts NO registrado" "$r" "apareció en el plan de build"

echo "▸ 3. Un build type propio y debuggable sí entra"
check "qaDevice genera mapping.txt" \
  "$(has_file $OUT/qaDevice/mapping.txt; echo $?)" "$OUT/qaDevice/mapping.txt"

echo "▸ 4. bundleArtifacts.enabled=false"
e=$($GRADLE -q :sample:bundleRelease --dry-run -PbundleArtifacts.enabled=false 2>/dev/null | grep -c releaseBundleArtifacts)
[ "$e" -eq 0 ]
check "releaseBundleArtifacts NO registrado" "$?" "apareció con enabled=false"

echo "▸ 5. bundleArtifacts.excludeVariants=release"
e=$($GRADLE -q :sample:bundleRelease --dry-run -PbundleArtifacts.excludeVariants=release 2>/dev/null | grep -c releaseBundleArtifacts)
[ "$e" -eq 0 ]
check "release excluido por property" "$?" "el override por property no se aplicó"

echo "▸ 6. bundleArtifacts.includeVariants=qaDevice (allow-list)"
i=$($GRADLE -q :sample:bundleQaDevice --dry-run -PbundleArtifacts.includeVariants=qaDevice 2>/dev/null | grep -c qaDeviceBundleArtifacts)
[ "$i" -gt 0 ]
check "qaDevice sí aparece con la allow-list" "$?" "la allow-list no lo incluyó"

echo "▸ 7. bundleArtifacts.mapping=never"
clean_outputs release
run :sample:bundleRelease -PbundleArtifacts.mapping=never
[ ! -f $OUT/release/mapping.txt ]
check "no copia mapping.txt" "$?" "el mapping quedó del build anterior o se generó"

echo "▸ 8. bundleArtifacts.mapping=always (debug no ofusca)"
clean_outputs debug
run :sample:bundleDebug -PbundleArtifacts.mapping=always
[ ! -f $OUT/debug/mapping.txt ]
check "no inventa un mapping inexistente" "$?" "apareció un mapping.txt en debug"

echo "▸ 9. bundleArtifacts.symbols=never"
clean_outputs release
run :sample:bundleRelease -PbundleArtifacts.symbols=never
[ ! -f $OUT/release/native-debug-symbols-release.zip ]
check "no copia el zip de símbolos" "$?" "el zip quedó del build anterior o se generó"

echo "▸ 10. bundleArtifacts.symbols=always incluye las excluidas"
clean_outputs debug
run :sample:bundleDebug -PbundleArtifacts.symbols=always -PbundleArtifacts.excludeVariants=
check "debug recibe native-debug-symbols-debug.zip" \
  "$(has_file $OUT/debug/native-debug-symbols-debug.zip; echo $?)"

echo "▸ 11. bundleArtifacts.locale=en"
run :sample:bundleRelease -PbundleArtifacts.locale=en
grep -q "Bundle ready" /tmp/sample-gradle.log
check "bloque en inglés" "$?" "no apareció 'Bundle ready' en la salida"

echo "▸ 12. BUNDLE_ARTIFACTS_LOCALE=en (variable de entorno)"
BUNDLE_ARTIFACTS_LOCALE=en run :sample:bundleRelease
grep -q "Bundle ready" /tmp/sample-gradle.log
check "bloque en inglés por env var" "$?" "la env var no tuvo efecto"

echo "▸ 13. bundleArtifacts.clean=true borra lo de corridas anteriores"
clean_outputs release
run :sample:bundleRelease
check "primer build deja mapping.txt" \
  "$(has_file $OUT/release/mapping.txt; echo $?)"
run :sample:bundleRelease -PbundleArtifacts.mapping=never
[ ! -f $OUT/release/mapping.txt ]
check "mapping=never borra el mapping viejo" "$?" "el mapping de la corrida anterior quedó"

echo "▸ 14. bundleArtifacts.clean=false deja los archivos previos"
clean_outputs release
run :sample:bundleRelease
run :sample:bundleRelease -PbundleArtifacts.mapping=never -PbundleArtifacts.clean=false
check "el mapping viejo sobrevive con clean=false" \
  "$(has_file $OUT/release/mapping.txt; echo $?)"

echo
printf 'resultado: %d PASS / %d FAIL\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
