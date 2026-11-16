package io.github.thatsfguy.meshcore.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Selectable colour palettes (MeshCore Custom, Phase 3).
 *
 * The stored preference `theme` stays a plain string so nothing that already reads or
 * backs it up changes: "system" / "light" / "dark" keep their old meaning, and a palette is
 * stored as `palette:<id>` (see [PREFIX]). An unknown id resolves to null, which makes the
 * caller fall back to "follow the system" — a restored backup naming a palette this build
 * lacks therefore never breaks the UI.
 *
 * Every colour here is an original value chosen for a mood. No artwork or trademarked
 * asset is bundled; characters are evoked by palette only, and users import their own art.
 */
data class ThemePalette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color = Color(0xFFF87171),
) {
    fun toColorScheme(): ColorScheme =
        if (dark) {
            darkColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
                secondary = secondary, onSecondary = onPrimary,
                secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary, onTertiary = onPrimary,
                background = background, onBackground = onSurface,
                surface = surface, onSurface = onSurface,
                surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
                outline = outline, outlineVariant = outlineVariant, error = error,
            )
        } else {
            lightColorScheme(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
                secondary = secondary, onSecondary = onPrimary,
                secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary, onTertiary = onPrimary,
                background = background, onBackground = onSurface,
                surface = surface, onSurface = onSurface,
                surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
                outline = outline, outlineVariant = outlineVariant, error = error,
            )
        }

    companion object {
        const val PREFIX = "palette:"
    }
}

object ThemePalettes {

    private fun c(argb: Long) = Color(argb)

    // ── Classic ──

    val MaterialDark = ThemePalette(
        "material_dark", "Material Dark", true,
        primary = c(0xFFD0BCFF), onPrimary = c(0xFF381E72),
        primaryContainer = c(0xFF4F378B), onPrimaryContainer = c(0xFFEADDFF),
        secondary = c(0xFFCCC2DC), secondaryContainer = c(0xFF4A4458), onSecondaryContainer = c(0xFFE8DEF8),
        tertiary = c(0xFFEFB8C8),
        background = c(0xFF141218), surface = c(0xFF1D1B20), surfaceVariant = c(0xFF49454F),
        onSurface = c(0xFFE6E0E9), onSurfaceVariant = c(0xFFCAC4D0),
        outline = c(0xFF938F99), outlineVariant = c(0xFF49454F),
    )

    val MaterialLight = ThemePalette(
        "material_light", "Material Light", false,
        primary = c(0xFF6750A4), onPrimary = c(0xFFFFFFFF),
        primaryContainer = c(0xFFEADDFF), onPrimaryContainer = c(0xFF21005D),
        secondary = c(0xFF625B71), secondaryContainer = c(0xFFE8DEF8), onSecondaryContainer = c(0xFF1D192B),
        tertiary = c(0xFF7D5260),
        background = c(0xFFFEF7FF), surface = c(0xFFFFFFFF), surfaceVariant = c(0xFFE7E0EC),
        onSurface = c(0xFF1D1B20), onSurfaceVariant = c(0xFF49454F),
        outline = c(0xFF79747E), outlineVariant = c(0xFFCAC4D0),
        error = c(0xFFB3261E),
    )

    val AmoledBlack = ThemePalette(
        "amoled", "AMOLED Black", true,
        primary = c(0xFF5EB0FF), onPrimary = c(0xFF0F1115),
        primaryContainer = c(0xFF1A3A5C), onPrimaryContainer = c(0xFFA8D0FF),
        secondary = c(0xFF8AA6C4), secondaryContainer = c(0xFF26313F), onSecondaryContainer = c(0xFFCBD9EA),
        tertiary = c(0xFFE0A33A),
        background = c(0xFF000000), surface = c(0xFF0A0A0C), surfaceVariant = c(0xFF15161B),
        onSurface = c(0xFFE6E8EE), onSurfaceVariant = c(0xFFA6ADBC),
        outline = c(0xFF6B7283), outlineVariant = c(0xFF2E343F),
    )

    val MidnightBlue = ThemePalette(
        "midnight", "Midnight Blue", true,
        primary = c(0xFF7FB2FF), onPrimary = c(0xFF08142B),
        primaryContainer = c(0xFF1B3A73), onPrimaryContainer = c(0xFFD2E3FF),
        secondary = c(0xFF9DB4D6), secondaryContainer = c(0xFF1F2F4D), onSecondaryContainer = c(0xFFD6E3F7),
        tertiary = c(0xFFF2C66B),
        background = c(0xFF08122B), surface = c(0xFF0E1B3D), surfaceVariant = c(0xFF18274D),
        onSurface = c(0xFFE4EAF7), onSurfaceVariant = c(0xFFA9B6D1),
        outline = c(0xFF6879A0), outlineVariant = c(0xFF26365E),
    )

    // ── Character-inspired (palettes only, original colours) ──

    /** Dark navy, blue and cyan, warm yellow highlights. */
    val Niko = ThemePalette(
        "niko", "Niko", true,
        primary = c(0xFF4FD8F0), onPrimary = c(0xFF04202A),
        primaryContainer = c(0xFF123B5E), onPrimaryContainer = c(0xFFBDEFFA),
        secondary = c(0xFF5B8CFF), secondaryContainer = c(0xFF1B2D5C), onSecondaryContainer = c(0xFFD3E0FF),
        tertiary = c(0xFFFFD25A),
        background = c(0xFF0A1024), surface = c(0xFF111A36), surfaceVariant = c(0xFF1A2548),
        onSurface = c(0xFFE8EEFF), onSurfaceVariant = c(0xFFA7B4D6),
        outline = c(0xFF6377A8), outlineVariant = c(0xFF26335C),
    )

    /** Forest green and lime on charcoal. */
    val Chaz = ThemePalette(
        "chaz", "Chaz", true,
        primary = c(0xFF8BE04E), onPrimary = c(0xFF10200A),
        primaryContainer = c(0xFF24451A), onPrimaryContainer = c(0xFFD2F7B2),
        secondary = c(0xFF4FB3A0), secondaryContainer = c(0xFF173B36), onSecondaryContainer = c(0xFFBDEBE2),
        tertiary = c(0xFFF2B84B),
        background = c(0xFF101411), surface = c(0xFF171D18), surfaceVariant = c(0xFF212A22),
        onSurface = c(0xFFE5EDE4), onSurfaceVariant = c(0xFFA8B5A7),
        outline = c(0xFF6F7F6E), outlineVariant = c(0xFF2C382D),
    )

    /** Crimson and rose on a warm dark base. */
    val Riley = ThemePalette(
        "riley", "Riley", true,
        primary = c(0xFFFF6B81), onPrimary = c(0xFF2B0710),
        primaryContainer = c(0xFF5A1426), onPrimaryContainer = c(0xFFFFD3DA),
        secondary = c(0xFFE08AA0), secondaryContainer = c(0xFF4A2230), onSecondaryContainer = c(0xFFF8D9E2),
        tertiary = c(0xFFFFB86B),
        background = c(0xFF160B10), surface = c(0xFF1F1117), surfaceVariant = c(0xFF2C1A22),
        onSurface = c(0xFFF3E6EA), onSurfaceVariant = c(0xFFC4A9B2),
        outline = c(0xFF8A6B76), outlineVariant = c(0xFF3D262F),
    )

    /** Dark purple and violet, soft and sleepy. */
    val CatNap = ThemePalette(
        "catnap", "CatNap", true,
        primary = c(0xFFB894FF), onPrimary = c(0xFF1F0B45),
        primaryContainer = c(0xFF3F2478), onPrimaryContainer = c(0xFFE6D8FF),
        secondary = c(0xFF9C8AD6), secondaryContainer = c(0xFF2D2352), onSecondaryContainer = c(0xFFDCD3F5),
        tertiary = c(0xFFF0A8D8),
        background = c(0xFF140D26), surface = c(0xFF1C1333), surfaceVariant = c(0xFF282045),
        onSurface = c(0xFFEDE7FA), onSurfaceVariant = c(0xFFB5ACD0),
        outline = c(0xFF7C7399), outlineVariant = c(0xFF372E58),
    )

    /** Warm orange and yellow, bright and friendly (light). */
    val DogDay = ThemePalette(
        "dogday", "DogDay", false,
        primary = c(0xFFE8710A), onPrimary = c(0xFFFFFFFF),
        primaryContainer = c(0xFFFFDDB8), onPrimaryContainer = c(0xFF4A2200),
        secondary = c(0xFFB8860B), secondaryContainer = c(0xFFFFEFB0), onSecondaryContainer = c(0xFF3D2E00),
        tertiary = c(0xFF2E8B57),
        background = c(0xFFFFF6E5), surface = c(0xFFFFFFFF), surfaceVariant = c(0xFFFBE9C8),
        onSurface = c(0xFF2A1D0A), onSurfaceVariant = c(0xFF63503A),
        outline = c(0xFF9C8A70), outlineVariant = c(0xFFE8D6B4),
        error = c(0xFFB3261E),
    )

    val all: List<ThemePalette> = listOf(
        Niko, Chaz, Riley, CatNap, DogDay,
        MaterialDark, MaterialLight, AmoledBlack, MidnightBlue,
    )

    /** The stored preference value for [palette]. */
    fun prefValue(palette: ThemePalette): String = ThemePalette.PREFIX + palette.id

    /** The palette named by a stored `theme` value, or null for system/light/dark/unknown. */
    fun fromPref(value: String): ThemePalette? {
        if (!value.startsWith(ThemePalette.PREFIX)) return null
        val id = value.removePrefix(ThemePalette.PREFIX)
        return all.firstOrNull { it.id == id }
    }
}
