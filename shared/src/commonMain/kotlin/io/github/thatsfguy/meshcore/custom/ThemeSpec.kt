package io.github.thatsfguy.meshcore.custom

import kotlin.math.pow

/**
 * A user-editable theme in a documented, versioned JSON file.
 *
 * ```json
 * {"format":"meshcore-custom-theme","version":1,"name":"My theme","dark":true,
 *  "primary":"#4FD8F0", "onPrimary":"#04202A", ... ,
 *  "cardRadius":16,"buttonRadius":20,"panelOpacity":1.0,"fontScale":1.0}
 * ```
 * Colours are `#RRGGBB`. Every colour in [COLOR_KEYS] except `error` is required; numeric
 * appearance fields are optional and clamped to their range. Unknown keys are ignored.
 */
data class ThemeSpec(
    val name: String,
    val dark: Boolean,
    /** Opaque colours as 0xRRGGBB, keyed by [COLOR_KEYS]. */
    val colors: Map<String, Int>,
    val cardRadius: Int = 12,
    val buttonRadius: Int = 20,
    val panelOpacity: Double = 1.0,
    val fontScale: Double = 1.0,
) {
    companion object {
        const val FORMAT = "meshcore-custom-theme"
        const val VERSION = 1
        const val MAX_NAME = 40

        val REQUIRED_COLORS = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "secondary", "secondaryContainer", "onSecondaryContainer", "tertiary",
            "background", "surface", "surfaceVariant", "onSurface", "onSurfaceVariant",
            "outline", "outlineVariant",
        )
        val COLOR_KEYS = REQUIRED_COLORS + "error"
    }
}

sealed class ThemeImport {
    data class Ok(val theme: ThemeSpec) : ThemeImport()
    data class Rejected(val reason: String) : ThemeImport()
}

object ThemeSpecCodec {

    fun parseHex(s: String): Int? {
        if (s.length != 7 || s[0] != '#') return null
        return s.substring(1).toIntOrNull(16)?.takeIf { s.substring(1).all { c -> c in "0123456789abcdefABCDEF" } }
    }

    fun hex(rgb: Int): String = "#" + (rgb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

    fun encode(t: ThemeSpec): String = buildString {
        append("{\n")
        append("  \"format\": ").append(MiniJson.quote(ThemeSpec.FORMAT)).append(",\n")
        append("  \"version\": ").append(ThemeSpec.VERSION).append(",\n")
        append("  \"name\": ").append(MiniJson.quote(t.name)).append(",\n")
        append("  \"dark\": ").append(t.dark).append(",\n")
        for (k in ThemeSpec.COLOR_KEYS) {
            val v = t.colors[k] ?: continue
            append("  ").append(MiniJson.quote(k)).append(": ").append(MiniJson.quote(hex(v))).append(",\n")
        }
        append("  \"cardRadius\": ").append(t.cardRadius).append(",\n")
        append("  \"buttonRadius\": ").append(t.buttonRadius).append(",\n")
        append("  \"panelOpacity\": ").append(t.panelOpacity).append(",\n")
        append("  \"fontScale\": ").append(t.fontScale).append("\n")
        append("}")
    }

    /** Validates untrusted [json]. Never throws; never executes anything. */
    fun decode(json: String): ThemeImport {
        val m = try { MiniJson.parseFlatObject(json) } catch (e: MiniJson.ParseError) {
            return ThemeImport.Rejected("Not a valid theme file: ${e.message}")
        }
        if (m["format"] != ThemeSpec.FORMAT) return ThemeImport.Rejected("Not a MeshCore Custom theme")
        val version = (m["version"] as? Double)?.toInt()
        if (version == null || version < 1 || version > ThemeSpec.VERSION) {
            return ThemeImport.Rejected("Unsupported theme version")
        }
        val name = (m["name"] as? String)?.trim()?.filter { it.code >= 0x20 }?.take(ThemeSpec.MAX_NAME)
        if (name.isNullOrEmpty()) return ThemeImport.Rejected("Theme has no name")
        val dark = m["dark"] as? Boolean ?: return ThemeImport.Rejected("Missing \"dark\" true/false")
        val colors = LinkedHashMap<String, Int>()
        for (k in ThemeSpec.COLOR_KEYS) {
            val raw = m[k]
            if (raw == null) {
                if (k in ThemeSpec.REQUIRED_COLORS) return ThemeImport.Rejected("Missing colour \"$k\"")
                continue
            }
            colors[k] = parseHex(raw as? String ?: "") ?: return ThemeImport.Rejected("Bad colour for \"$k\" (use #RRGGBB)")
        }
        fun num(key: String, default: Double, lo: Double, hi: Double): Double =
            (m[key] as? Double)?.coerceIn(lo, hi) ?: default
        return ThemeImport.Ok(
            ThemeSpec(
                name = name, dark = dark, colors = colors,
                cardRadius = num("cardRadius", 12.0, 0.0, 32.0).toInt(),
                buttonRadius = num("buttonRadius", 20.0, 0.0, 32.0).toInt(),
                panelOpacity = num("panelOpacity", 1.0, 0.3, 1.0),
                fontScale = num("fontScale", 1.0, 0.8, 1.4),
            ),
        )
    }

    /** WCAG contrast ratio between two 0xRRGGBB colours (1.0 – 21.0). */
    fun contrast(a: Int, b: Int): Double {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        fun lum(rgb: Int) = 0.2126 * lin((rgb shr 16) and 255) + 0.7152 * lin((rgb shr 8) and 255) + 0.0722 * lin(rgb and 255)
        val la = lum(a); val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
