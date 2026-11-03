package com.leandrolcd.bundleartifacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NativeSymbolsLayoutTest {

    @TempDir
    lateinit var merged: File

    @Test
    fun `the lib folder of agp is not part of the entries`() {
        // Play Console rechaza el zip con "invalid directory lib": las ABI van en la raíz.
        so("lib", "arm64-v8a", "libfoo.so")
        so("lib", "x86_64", "libfoo.so")

        val names = NativeSymbolsLayout.entries(merged).map { it.name }

        assertEquals(listOf("arm64-v8a/libfoo.so", "x86_64/libfoo.so"), names)
    }

    @Test
    fun `every abi of the variant gets its own folder`() {
        so("lib", "armeabi-v7a", "libfoo.so")
        so("lib", "arm64-v8a", "libfoo.so")
        so("lib", "x86", "libfoo.so")
        so("lib", "x86_64", "libfoo.so")

        val names = NativeSymbolsLayout.entries(merged).map { it.name }

        assertEquals(
            listOf(
                "arm64-v8a/libfoo.so",
                "armeabi-v7a/libfoo.so",
                "x86/libfoo.so",
                "x86_64/libfoo.so"
            ),
            names
        )
    }

    @Test
    fun `several libraries of the same abi are kept`() {
        so("lib", "arm64-v8a", "libfoo.so")
        so("lib", "arm64-v8a", "libbar.so")

        val names = NativeSymbolsLayout.entries(merged).map { it.name }

        assertEquals(listOf("arm64-v8a/libbar.so", "arm64-v8a/libfoo.so"), names)
    }

    @Test
    fun `an unknown abi is not dropped`() {
        so("lib", "riscv64", "libfoo.so")

        val names = NativeSymbolsLayout.entries(merged).map { it.name }

        assertEquals(listOf("riscv64/libfoo.so"), names)
    }

    @Test
    fun `the merged folder is used when there is no lib folder`() {
        so("arm64-v8a", "libfoo.so")

        val names = NativeSymbolsLayout.entries(merged).map { it.name }

        assertEquals(listOf("arm64-v8a/libfoo.so"), names)
    }

    @Test
    fun `there are no entries without native libraries`() {
        File(merged, "lib/arm64-v8a").mkdirs()

        assertTrue(NativeSymbolsLayout.entries(merged).isEmpty())
    }

    @Test
    fun `the abi root is the folder that holds the abis`() {
        so("lib", "arm64-v8a", "libfoo.so")

        val root = NativeSymbolsLayout.abiRoot(merged)

        assertEquals("lib", root?.name)
    }

    private fun so(vararg path: String) {
        val file = File(merged, path.joinToString(File.separator))
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(8))
    }
}
