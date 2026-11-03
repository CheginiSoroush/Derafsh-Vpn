package com.v2ray.ang.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Derafsh Kaviani design tokens.
 *
 * Dark theme: near-black violet night (#100B1D) with champagne-gold light (#D8B66A / #F3DDA4)
 * and a controlled burgundy accent (#8F2849). Connected state is gold — the standard is raised.
 *
 * Light theme is an independent design (not an inversion): ivory ground (#FAF5E9),
 * deep-violet text (#241733), dark gold with sufficient contrast (#8F6A2A).
 */
private val LightColor = lightColorScheme(
    primary = Color(0xFF8F6A2A), // Kaviani Gold, dark — readable on ivory
    onPrimary = Color(0xFFFFF8E7), // Warm Ivory
    primaryContainer = Color(0xFFF3DDA4), // Light Gold
    onPrimaryContainer = Color(0xFF3A2B0E), // Deep Bronze
    secondary = Color(0xFF6E5420), // Aged Gold
    onSecondary = Color(0xFFFFF8E7), // Warm Ivory
    secondaryContainer = Color(0xFFEBDCB4), // Pale Straw
    onSecondaryContainer = Color(0xFF2E220A), // Dark Bronze
    tertiary = Color(0xFF77621F), // Raised-Standard Gold
    onTertiary = Color(0xFFFFF8E7), // Warm Ivory
    tertiaryContainer = Color(0xFFF3DDA4), // Light Gold
    onTertiaryContainer = Color(0xFF3A2B0E), // Deep Bronze
    error = Color(0xFFA33B52), // Kaviani Burgundy Red
    errorContainer = Color(0xFFF9D9DE), // Pale Rose
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF41121C), // Deep Burgundy
    background = Color(0xFFFAF5E9), // Ivory
    onBackground = Color(0xFF241733), // Deep Violet
    surface = Color(0xFFFBF7EE), // Warm Ivory
    onSurface = Color(0xFF241733), // Deep Violet
    surfaceVariant = Color(0xFFEFE5CC), // Parchment
    onSurfaceVariant = Color(0xFF574A33), // Dark Umber
    outline = Color(0xFF857A5F), // Olive Gray
    outlineVariant = Color(0xFFDCD2B8), // Light Parchment
    inverseSurface = Color(0xFF241733), // Deep Violet
    inverseOnSurface = Color(0xFFFAF5E9), // Ivory
    inversePrimary = Color(0xFFF3DDA4), // Light Gold
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF8F6A2A), // Kaviani Gold
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF6F0E0), // Light Ivory
    surfaceContainer = Color(0xFFF1E9D5), // Ivory Card
    surfaceContainerHigh = Color(0xFFEAE1CA), // Deeper Ivory
    surfaceContainerHighest = Color(0xFFE3D9C0), // Deepest Ivory
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFD8B66A), // Kaviani Champagne Gold
    onPrimary = Color(0xFF2A1E10), // Deep Bronze
    primaryContainer = Color(0xFF5C4A24), // Shadow Gold
    onPrimaryContainer = Color(0xFFF3DDA4), // Light Gold
    secondary = Color(0xFFF3DDA4), // Light Gold
    onSecondary = Color(0xFF2A1E10), // Deep Bronze
    secondaryContainer = Color(0xFF3A2E14), // Bronze Night
    onSecondaryContainer = Color(0xFFF3DDA4), // Light Gold
    tertiary = Color(0xFFE9CF8F), // Raised-Standard Gold
    onTertiary = Color(0xFF2A1E10), // Deep Bronze
    tertiaryContainer = Color(0xFF4A3A18), // Deep Gold Night
    onTertiaryContainer = Color(0xFFF3DDA4), // Light Gold
    error = Color(0xFFE58B9B), // Soft Burgundy Rose
    errorContainer = Color(0xFF4A1524), // Burgundy Night
    onError = Color(0xFF2A0A12), // Deep Burgundy
    onErrorContainer = Color(0xFFF5C6CF), // Pale Rose
    background = Color(0xFF100B1D), // Kaviani Night
    onBackground = Color(0xFFF5F0E8), // Parchment White
    surface = Color(0xFF21152F), // Derafsh Violet
    onSurface = Color(0xFFF5F0E8), // Parchment White
    surfaceVariant = Color(0xFF2B1F3D), // Violet Dusk
    onSurfaceVariant = Color(0xFFC9BFA8), // Warm Gray Gold
    outline = Color(0xFF6F6272), // Violet Gray
    outlineVariant = Color(0xFF35294A), // Deep Violet
    inverseSurface = Color(0xFFF5F0E8), // Parchment White
    inverseOnSurface = Color(0xFF21152F), // Derafsh Violet
    inversePrimary = Color(0xFF8F6A2A), // Dark Gold
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFD8B66A), // Kaviani Gold
    surfaceContainerLowest = Color(0xFF0B0716), // Abyss
    surfaceContainerLow = Color(0xFF191226), // Night Low
    surfaceContainer = Color(0xFF21152F), // Derafsh Violet
    surfaceContainerHigh = Color(0xFF2A1D3B), // Dusk High
    surfaceContainerHighest = Color(0xFF332447), // Dusk Highest
)

// Semantic Colors
val colorPing = Color(0xFF5FA97D) // Kaviani Jade — readable on ivory and violet
val colorPingRed = Color(0xFFC74B62) // Kaviani Burgundy Red
val colorConfigType = Color(0xFFD8B66A) // Kaviani Gold
val colorFabActive = Color(0xFFD8B66A) // Kaviani Gold
val colorFabInactiveLight = Color(0xFF9C9486) // Warm Gray
val colorFabInactiveDark = Color(0xFF645E52) // Dark Warm Gray
val dividerColorLight = Color(0xFFDCD2B8) // Light Parchment
val dividerColorDark = Color(0xFF35294A) // Deep Violet

// Toast semantic accents, tuned per theme against the inverse toast surface — the
// parchment pill in dark mode carries deep accents, the deep-violet pill in light
// mode carries light ones — so every badge stays readable on the brand surface.
val toastSuccessAccentDark = Color(0xFF2F7D51) // Deep Jade on Parchment
val toastSuccessAccentLight = Color(0xFF7CC79B) // Light Jade on Deep Violet
val toastErrorAccentDark = Color(0xFFB0364E) // Kaviani Burgundy on Parchment
val toastErrorAccentLight = Color(0xFFF095A5) // Soft Rose on Deep Violet
val toastInfoAccentDark = Color(0xFF8F6A2A) // Aged Gold on Parchment
val toastInfoAccentLight = Color(0xFFE9CF8F) // Champagne on Deep Violet

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic (Material You) color is intentionally unsupported: it clashes with the
    // Derafsh brand palette, so the scheme is always the fixed light/dark brand theme.
    val colorScheme = if (darkTheme) DarkColor else LightColor
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
