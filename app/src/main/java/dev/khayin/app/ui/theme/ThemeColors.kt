package dev.khayin.app.ui.theme

import androidx.compose.ui.graphics.Color
import dev.khayin.app.domain.model.AppTheme

data class ThemeColorPalette(
    val secondary: Color,
    val secondaryVariant: Color,
    val onSecondary: Color = NuvioPrimitives.white,
    val onSecondaryVariant: Color = NuvioPrimitives.white,
    val accentGradient: List<Color> = listOf(secondary),
    val focusRing: Color,
    val focusRingGradient: List<Color> = listOf(focusRing),
    val focusBackground: Color,
    val background: Color = NuvioPrimitives.neutral950,
    val backgroundElevated: Color = NuvioPrimitives.neutral900,
    val backgroundCard: Color = NuvioPrimitives.neutral825,
    val surface: Color = NuvioPrimitives.neutral875,
    val surfaceVariant: Color = NuvioPrimitives.neutral800,
    val panel: Color = NuvioPrimitives.neutral900,
    val overlay: Color = Color(0xD9000000),
    val field: Color = NuvioPrimitives.neutral850,
    val menu: Color = NuvioPrimitives.neutral875,
    val modal: Color = NuvioPrimitives.neutral900,
    val playerOverlay: Color = Color(0xCC000000)
)

object ThemeColors {
    val KhaYin = ThemeColorPalette(
        secondary = Color(0xFF00E676),
        secondaryVariant = Color(0xFF00C853),
        onSecondary = Color.Black,
        onSecondaryVariant = Color.Black,
        accentGradient = listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
        focusRing = Color(0xFF69F0AE),
        focusBackground = Color(0xFF0A2E1A),
        background = Color(0xFF08090C),
        backgroundElevated = Color(0xFF101217),
        backgroundCard = Color(0xFF161920),
        surface = Color(0xFF161920),
        surfaceVariant = Color(0xFF1E222B)
    )

    val DarkIndigo = ThemeColorPalette(
        secondary = Color(0xFF6366F1),
        secondaryVariant = Color(0xFF4F46E5),
        onSecondary = Color.White,
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
        focusRing = Color(0xFFA5B4FC),
        focusBackground = Color(0xFF1E1B4B),
        background = Color(0xFF080811),
        backgroundElevated = Color(0xFF111122),
        backgroundCard = Color(0xFF18182E),
        surface = Color(0xFF16162A),
        surfaceVariant = Color(0xFF22223D)
    )

    val Crimson = ThemeColorPalette(
        secondary = NuvioPrimitives.red500,
        secondaryVariant = NuvioPrimitives.red600,
        focusRing = NuvioPrimitives.red300,
        focusBackground = Color(0xFF3D1A1A),
        backgroundCard = Color(0xFF241A1A)
    )

    val Ocean = ThemeColorPalette(
        secondary = NuvioPrimitives.blue500,
        secondaryVariant = NuvioPrimitives.blue700,
        focusRing = NuvioPrimitives.blue300,
        focusBackground = Color(0xFF1A2D3D),
        background = Color(0xFF0D0D0F),
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1A1F24)
    )

    val Violet = ThemeColorPalette(
        secondary = NuvioPrimitives.violet500,
        secondaryVariant = NuvioPrimitives.violet700,
        focusRing = NuvioPrimitives.violet300,
        focusBackground = Color(0xFF2D1A3D),
        background = Color(0xFF0D0D0F),
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1F1A24)
    )

    val Emerald = ThemeColorPalette(
        secondary = NuvioPrimitives.green500,
        secondaryVariant = NuvioPrimitives.green700,
        focusRing = NuvioPrimitives.green300,
        focusBackground = Color(0xFF1A3D1E),
        backgroundCard = Color(0xFF1A241A)
    )

    val Amber = ThemeColorPalette(
        secondary = NuvioPrimitives.amber500,
        secondaryVariant = NuvioPrimitives.amber700,
        focusRing = NuvioPrimitives.amber300,
        focusBackground = Color(0xFF3D2D1A),
        background = Color(0xFF0F0D0D),
        backgroundElevated = Color(0xFF1E1A1A),
        backgroundCard = Color(0xFF24201A)
    )

    val Rose = ThemeColorPalette(
        secondary = NuvioPrimitives.rose500,
        secondaryVariant = NuvioPrimitives.rose700,
        focusRing = NuvioPrimitives.rose300,
        focusBackground = Color(0xFF3D1A2D),
        backgroundCard = Color(0xFF241A1F)
    )

    val White = ThemeColorPalette(
        secondary = NuvioPrimitives.neutral100,
        secondaryVariant = NuvioPrimitives.neutral200,
        onSecondary = NuvioPrimitives.neutral925,
        onSecondaryVariant = NuvioPrimitives.neutral925,
        focusRing = NuvioPrimitives.white,
        focusBackground = Color(0xFF303030),
        backgroundCard = NuvioPrimitives.neutral850
    )

    val Cyberpunk = ThemeColorPalette(
        secondary = Color(0xFF00F0FF),
        secondaryVariant = Color(0xFF7000FF),
        onSecondary = Color(0xFF05101A),
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFF00F0FF), Color(0xFF7000FF), Color(0xFFFF007F)),
        focusRing = Color(0xFF00F0FF),
        focusBackground = Color(0xFF0A1C2A),
        background = Color(0xFF080811),
        backgroundElevated = Color(0xFF111122),
        backgroundCard = Color(0xFF16162C)
    )

    val Sunset = ThemeColorPalette(
        secondary = Color(0xFFFF6D00),
        secondaryVariant = Color(0xFFFF3D00),
        onSecondary = Color(0xFF1C0B02),
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFFFF9E80), Color(0xFFFF6D00), Color(0xFFFF3D00)),
        focusRing = Color(0xFFFF9E80),
        focusBackground = Color(0xFF3A1A0D),
        background = Color(0xFF100B09),
        backgroundElevated = Color(0xFF1A120E),
        backgroundCard = Color(0xFF241813)
    )

    val MidnightPurple = ThemeColorPalette(
        secondary = Color(0xFFA855F7),
        secondaryVariant = Color(0xFF7E22CE),
        onSecondary = Color(0xFF12051E),
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFFC084FC), Color(0xFFA855F7), Color(0xFF7E22CE)),
        focusRing = Color(0xFFC084FC),
        focusBackground = Color(0xFF2A123D),
        background = Color(0xFF0C0812),
        backgroundElevated = Color(0xFF150F20),
        backgroundCard = Color(0xFF1E152D)
    )

    val Ruby = ThemeColorPalette(
        secondary = Color(0xFFFF1744),
        secondaryVariant = Color(0xFFD50000),
        onSecondary = Color.White,
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFFD50000)),
        focusRing = Color(0xFFFF5252),
        focusBackground = Color(0xFF3D0A14),
        background = Color(0xFF10080A),
        backgroundElevated = Color(0xFF1C0F12),
        backgroundCard = Color(0xFF271419)
    )

    val Aquamarine = ThemeColorPalette(
        secondary = Color(0xFF00E5FF),
        secondaryVariant = Color(0xFF00BFA5),
        onSecondary = Color(0xFF03181C),
        onSecondaryVariant = Color(0xFF03181C),
        accentGradient = listOf(Color(0xFF18FFFF), Color(0xFF00E5FF), Color(0xFF00BFA5)),
        focusRing = Color(0xFF18FFFF),
        focusBackground = Color(0xFF0A282C),
        background = Color(0xFF070F11),
        backgroundElevated = Color(0xFF0E1B1E),
        backgroundCard = Color(0xFF13262A)
    )

    val Mint = ThemeColorPalette(
        secondary = Color(0xFF00F5A0),
        secondaryVariant = Color(0xFF00C896),
        onSecondary = Color(0xFF041C13),
        onSecondaryVariant = Color(0xFF041C13),
        accentGradient = listOf(Color(0xFF69F0AE), Color(0xFF00F5A0), Color(0xFF00C896)),
        focusRing = Color(0xFF69F0AE),
        focusBackground = Color(0xFF0A2E20),
        background = Color(0xFF07100B),
        backgroundElevated = Color(0xFF0E1C14),
        backgroundCard = Color(0xFF13271C)
    )

    val Coral = ThemeColorPalette(
        secondary = Color(0xFFFF6B6B),
        secondaryVariant = Color(0xFFEE5253),
        onSecondary = Color(0xFF1E0606),
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFFFF8E8E), Color(0xFFFF6B6B), Color(0xFFEE5253)),
        focusRing = Color(0xFFFF8E8E),
        focusBackground = Color(0xFF3A1717),
        background = Color(0xFF100909),
        backgroundElevated = Color(0xFF1B1010),
        backgroundCard = Color(0xFF261616)
    )

    val Titanium = ThemeColorPalette(
        secondary = Color(0xFF90A4AE),
        secondaryVariant = Color(0xFF607D8B),
        onSecondary = Color(0xFF0A0F12),
        onSecondaryVariant = Color.White,
        accentGradient = listOf(Color(0xFFCFD8DC), Color(0xFF90A4AE), Color(0xFF607D8B)),
        focusRing = Color(0xFFCFD8DC),
        focusBackground = Color(0xFF20292E),
        background = Color(0xFF0A0D0F),
        backgroundElevated = Color(0xFF12171A),
        backgroundCard = Color(0xFF1A2126)
    )

    fun getColorPalette(theme: AppTheme): ThemeColorPalette {
        return when (theme) {
            AppTheme.KHAYIN -> KhaYin
            AppTheme.DARK_INDIGO -> DarkIndigo
            AppTheme.GOLD -> SupporterThemeColors.Gold
            AppTheme.JADE -> SupporterThemeColors.Jade
            AppTheme.ROSE_GOLD -> SupporterThemeColors.RoseGold
            AppTheme.ARCTIC_BLUE -> SupporterThemeColors.ArcticBlue
            AppTheme.GRAPHITE -> SupporterThemeColors.Graphite
            AppTheme.CRIMSON -> Crimson
            AppTheme.OCEAN -> Ocean
            AppTheme.VIOLET -> Violet
            AppTheme.EMERALD -> Emerald
            AppTheme.AMBER -> Amber
            AppTheme.ROSE -> Rose
            AppTheme.WHITE -> White
            AppTheme.CYBERPUNK -> Cyberpunk
            AppTheme.SUNSET -> Sunset
            AppTheme.MIDNIGHT_PURPLE -> MidnightPurple
            AppTheme.RUBY -> Ruby
            AppTheme.AQUAMARINE -> Aquamarine
            AppTheme.MINT -> Mint
            AppTheme.CORAL -> Coral
            AppTheme.TITANIUM -> Titanium
        }
    }
}
