package dev.khayin.app.domain.model

private val supporterThemes = linkedMapOf(
    AppTheme.GOLD to CosmeticEntitlement.GOLD_THEME,
    AppTheme.JADE to CosmeticEntitlement.JADE_THEME,
    AppTheme.ROSE_GOLD to CosmeticEntitlement.ROSE_GOLD_THEME,
    AppTheme.ARCTIC_BLUE to CosmeticEntitlement.ARCTIC_BLUE_THEME,
    AppTheme.GRAPHITE to CosmeticEntitlement.GRAPHITE_THEME
)

private val standardThemes = listOf(AppTheme.KHAYIN, AppTheme.DARK_INDIGO, AppTheme.WHITE) + AppTheme.entries.filterNot {
    it == AppTheme.KHAYIN || it == AppTheme.DARK_INDIGO || it == AppTheme.WHITE || it in supporterThemes
}

fun availableAppThemes(entitlements: CosmeticEntitlements): List<AppTheme> {
    val unlockedSupporterThemes = supporterThemes
        .filterValues(entitlements::includes)
        .keys
        .toList()
    return standardThemes + unlockedSupporterThemes
}

fun resolveAppTheme(
    selectedTheme: AppTheme?,
    entitlements: CosmeticEntitlements
): AppTheme {
    if (selectedTheme == null) {
        return AppTheme.KHAYIN
    }
    return if (selectedTheme in availableAppThemes(entitlements)) {
        selectedTheme
    } else {
        AppTheme.KHAYIN
    }
}
