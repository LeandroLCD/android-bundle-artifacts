package com.leandrolcd.bundleartifacts

import java.io.File

/**
 * Layout del zip de native debug symbols que espera Play Console.
 *
 * Play Console solo acepta carpetas de ABI Android en el primer nivel del zip:
 *
 *     arm64-v8a/libfoo.so
 *     x86_64/libfoo.so
 *
 * Si el zip trae una carpeta `lib/` arriba, lo rechaza con
 * "The native debug symbols contain an invalid directory lib. Only Android ABIs are supported".
 * Del `lib/` de AGP hay que copiar su contenido, no la carpeta.
 */
internal object NativeSymbolsLayout {

    /** ABIs que Play Console acepta como carpetas de primer nivel. */
    private val ANDROID_ABIS = setOf(
        "armeabi-v7a",
        "arm64-v8a",
        "x86",
        "x86_64",
        "riscv64"
    )

    /**
     * Las `.so` mergeadas por AGP viven en `<...>/merge<Variant>NativeLibs/out/lib/<abi>/`.
     * Devuelve la raíz que contiene las carpetas de ABI, es decir, el `lib/` sin incluir.
     */
    fun abiRoot(merged: File): File? {
        if (!merged.isDirectory) return null
        val lib = File(merged, "lib")
        if (lib.isDirectory && abiDirs(lib).isNotEmpty()) return lib
        return merged.takeIf { abiDirs(it).isNotEmpty() }
    }

    /**
     * Entradas del zip: cada `.so` con el nombre relativo a la raíz del zip (`<abi>/<archivo>.so`).
     */
    fun entries(merged: File): List<SymbolEntry> {
        val root = abiRoot(merged) ?: return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".so") }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                SymbolEntry(file = file, name = relative.removePrefix("lib/"))
            }
            .sortedBy { it.name }
            .toList()
    }

    /**
     * Carpetas ABI de una raíz: las subcarpetas que tienen `.so` adentro.
     * Si ninguna se llama como un ABI conocido se usan todas, para no perder una ABI nueva.
     */
    private fun abiDirs(root: File): List<File> {
        val dirs = root.listFiles { file -> file.isDirectory && file.name != "lib" } ?: return emptyList()
        val withLibs = dirs.filter { dir ->
            dir.listFiles { file -> file.isFile && file.name.endsWith(".so") }?.isNotEmpty() == true
        }
        val known = withLibs.filter { it.name in ANDROID_ABIS }
        return if (known.isNotEmpty()) known else withLibs
    }
}

/** Una `.so` y el nombre con el que entra al zip. */
internal data class SymbolEntry(val file: File, val name: String)
