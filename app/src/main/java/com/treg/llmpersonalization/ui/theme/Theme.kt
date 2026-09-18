package com.treg.llmpersonalization.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme is the only theme — this is a fixed-identity app.
// Light mode falls back to the same palette so system switching
// has no visible effect.
private val DarkColors = darkColorScheme(
    primary             = BloodRedBright,
    onPrimary           = BoneWhite,
    primaryContainer    = BloodRedDim,
    onPrimaryContainer  = BoneWhite,

    secondary           = MutedLilac,
    onSecondary         = DeepVoid,
    secondaryContainer  = ShadowCourt,
    onSecondaryContainer= BoneWhite,

    tertiary            = Candlelight,
    onTertiary          = DeepVoid,

    background          = DeepVoid,
    onBackground        = BoneWhite,

    surface             = CryptFloor,
    onSurface           = BoneWhite,
    surfaceVariant      = ShadowCourt,
    onSurfaceVariant    = MutedLilac,

    outline             = BorderWine,
    outlineVariant      = Onyx,

    error               = ErrorScarlet,
    onError             = BoneWhite,
    errorContainer      = BloodRedDim,
    onErrorContainer    = BoneWhite,
)

@Composable
fun LLMPersonalizationTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = AppTypography,
        content     = content
    )
}