package com.leandrolcd.bundleartifacts

/**
 * Helpers de layout para el recuadro de la consola.
 *
 * Las consolas monoespaciadas no tratan todos los caracteres igual: los emojis ocupan dos
 * columnas y los selectores de variación / ZWJ no ocupan ninguna. Sin esto el recuadro
 * queda torcido.
 */
object TextLayout {

    /**
     * Cantidad de columnas que ocupa [text] en una terminal monoespaciada.
     */
    fun displayWidth(text: String): Int {
        var width = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            width += when {
                codePoint == ZERO_WIDTH_JOINER ||
                    codePoint == VARIATION_SELECTOR_15 ||
                    codePoint == VARIATION_SELECTOR_16 -> 0

                codePoint in 0x1F000..0x1FAFF -> 2
                codePoint in 0x2B00..0x2BFF -> 2
                else -> 1
            }
        }
        return width
    }

    /** [text] seguido de los espacios necesarios para alcanzar [width] columnas. */
    fun padEndToWidth(text: String, width: Int): String =
        text + " ".repeat((width - displayWidth(text)).coerceAtLeast(0))

    /** [text] envuelto en espacios para quedar centrado en [width] columnas. */
    fun centerToWidth(text: String, width: Int): String {
        val padding = (width - displayWidth(text)).coerceAtLeast(0)
        val left = padding / 2
        return " ".repeat(left) + text + " ".repeat(padding - left)
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_15 = 0xFE0E
    private const val VARIATION_SELECTOR_16 = 0xFE0F
}