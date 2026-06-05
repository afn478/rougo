package com.selxo.rougo

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

internal data class YoutubeResolutionOption(val key: String, @param:StringRes val labelRes: Int)
internal data class AccentOption(val key: String, @param:StringRes val labelRes: Int, val darkColor: Color, val lightColor: Color)
enum class LibraryDownloadState { Idle, Loading, Complete }
internal const val PREF_YOUTUBE_RESOLUTION = "youtube_preferred_resolution"
internal const val PREF_YOUTUBE_AUTO_SUBTITLES = "youtube_auto_subtitles"
internal const val PREF_YOUTUBE_SUBTITLE_LANGUAGE = "youtube_subtitle_language"
internal const val PREF_SKIP_SECONDS = "player_skip_seconds"
internal const val PREF_SUBTITLE_OFFSET_MS = "subtitle_offset_ms"
internal const val PREF_SAVE_REPEAT_RECORDINGS = "save_repeat_recordings"
internal const val PREF_LIGHT_MODE = "app_light_mode"
internal const val PREF_THEME_MODE = "app_theme_mode"
internal const val PREF_ACCENT_COLOR = "app_accent_color"
internal const val DEFAULT_SKIP_SECONDS = 5
internal const val DEFAULT_SUBTITLE_OFFSET_MS = 0L
internal const val THEME_DARK = "dark"
internal const val THEME_BLACK = "black"
internal const val THEME_LIGHT = "light"
internal const val THEME_SYSTEM = "system"
internal const val YOUTUBE_RESOLUTION_ASK = "ask"
internal const val YOUTUBE_RESOLUTION_HIGHEST = "highest"
internal const val YOUTUBE_RESOLUTION_AUDIO = "audio"
internal const val DEFAULT_YOUTUBE_RESOLUTION = "720"
internal const val DEFAULT_YOUTUBE_SUBTITLE_LANGUAGE = "ja"
internal const val PLAYER_NOTIFICATION_CHANNEL_ID = "rougo_player_controls"
internal const val PLAYER_NOTIFICATION_ID = 2407
internal const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "rougo_downloads"
internal const val DOWNLOAD_NOTIFICATION_ID_BASE = 3400
internal const val ACTION_PLAYER_PLAY_PAUSE = "com.selxo.rougo.action.PLAY_PAUSE"
internal const val ACTION_PLAYER_REWIND = "com.selxo.rougo.action.REWIND"
internal const val ACTION_PLAYER_FORWARD = "com.selxo.rougo.action.FORWARD"
internal const val ACTION_PLAYER_STOP = "com.selxo.rougo.action.STOP"
internal val YOUTUBE_RESOLUTION_OPTIONS = listOf(
    YoutubeResolutionOption("720", R.string.option_youtube_quality_720p),
    YoutubeResolutionOption(YOUTUBE_RESOLUTION_ASK, R.string.option_youtube_quality_ask),
    YoutubeResolutionOption("480", R.string.option_youtube_quality_480p),
    YoutubeResolutionOption("1080", R.string.option_youtube_quality_1080p),
    YoutubeResolutionOption(YOUTUBE_RESOLUTION_HIGHEST, R.string.option_youtube_quality_highest),
    YoutubeResolutionOption(YOUTUBE_RESOLUTION_AUDIO, R.string.option_youtube_quality_audio_only)
)
internal val YOUTUBE_SUBTITLE_LANGUAGE_OPTIONS = listOf(
    YoutubeResolutionOption("ja", R.string.option_subtitle_language_japanese),
    YoutubeResolutionOption("en", R.string.option_subtitle_language_english),
    YoutubeResolutionOption("zh-Hant", R.string.option_subtitle_language_chinese_traditional),
    YoutubeResolutionOption("zh-Hans", R.string.option_subtitle_language_chinese_simplified),
    YoutubeResolutionOption("ko", R.string.option_subtitle_language_korean),
    YoutubeResolutionOption("es", R.string.option_subtitle_language_spanish),
    YoutubeResolutionOption("any", R.string.option_subtitle_language_best_available)
)
internal val THEME_MODE_OPTIONS = listOf(
    YoutubeResolutionOption(THEME_DARK, R.string.option_theme_dark),
    YoutubeResolutionOption(THEME_BLACK, R.string.option_theme_black),
    YoutubeResolutionOption(THEME_LIGHT, R.string.option_theme_light),
    YoutubeResolutionOption(THEME_SYSTEM, R.string.option_theme_system)
)
internal val ACCENT_OPTIONS = listOf(
    AccentOption("purple", R.string.option_accent_purple, Color(0xFFAEB2FF), Color(0xFF585DDB)),
    AccentOption("red", R.string.option_accent_red, Color(0xFFFF8A80), Color(0xFFC62828)),
    AccentOption("pink", R.string.option_accent_pink, Color(0xFFFF8FD8), Color(0xFFAD1457)),
    AccentOption("orange", R.string.option_accent_orange, Color(0xFFFFB86B), Color(0xFFBF5F00)),
    AccentOption("yellow", R.string.option_accent_yellow, Color(0xFFFFD75E), Color(0xFF8A6D00)),
    AccentOption("green", R.string.option_accent_green, Color(0xFF7EE787), Color(0xFF1B7F38)),
    AccentOption("teal", R.string.option_accent_teal, Color(0xFF64D8CB), Color(0xFF00796B)),
    AccentOption("blue", R.string.option_accent_blue, Color(0xFF8AB4FF), Color(0xFF1565C0)),
    AccentOption("indigo", R.string.option_accent_indigo, Color(0xFF9FA8FF), Color(0xFF3949AB)),
    AccentOption("gray", R.string.option_accent_gray, Color(0xFFCBD5E1), Color(0xFF475569))
)
internal val PLAYER_NOTIFICATION_ACTIONS = arrayOf(
    ACTION_PLAYER_PLAY_PAUSE,
    ACTION_PLAYER_REWIND,
    ACTION_PLAYER_FORWARD,
    ACTION_PLAYER_STOP
)
private val RougoDarkColorScheme = darkColorScheme(
    background = Color(0xFF141419),
    surface = Color(0xFF222228),
    surfaceVariant = Color(0xFF2D2D36),
    primary = Color(0xFFAEB2FF),
    secondary = Color(0xFF73D6C9),
    tertiary = Color(0xFFFFB08A),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFC7C7D1),
    onPrimary = Color(0xFF141419)
)
private val RougoLightColorScheme = lightColorScheme(
    background = Color(0xFFF7F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EAF4),
    primary = Color(0xFF585DDB),
    secondary = Color(0xFF067A71),
    tertiary = Color(0xFFB85F2E),
    onBackground = Color(0xFF171720),
    onSurface = Color(0xFF171720),
    onSurfaceVariant = Color(0xFF5D6070),
    onPrimary = Color.White
)
private val RougoBlackColorScheme = darkColorScheme(
    background = Color.Black,
    surface = Color(0xFF111116),
    surfaceVariant = Color(0xFF1C1C22),
    primary = Color(0xFFAEB2FF),
    secondary = Color(0xFF73D6C9),
    tertiary = Color(0xFFFFB08A),
    onBackground = Color(0xFFF7F7FB),
    onSurface = Color(0xFFF7F7FB),
    onSurfaceVariant = Color(0xFFC7C7D1),
    onPrimary = Color.Black
)
internal fun rougoColorScheme(themeMode: String, accentKey: String, systemDark: Boolean): androidx.compose.material3.ColorScheme {
    val usesDarkSurfaces = when (themeMode) {
        THEME_LIGHT -> false
        THEME_SYSTEM -> systemDark
        else -> true
    }
    val base = when (themeMode) {
        THEME_BLACK -> RougoBlackColorScheme
        THEME_LIGHT -> RougoLightColorScheme
        THEME_SYSTEM -> if (systemDark) RougoDarkColorScheme else RougoLightColorScheme
        else -> RougoDarkColorScheme
    }
    val accent = ACCENT_OPTIONS.firstOrNull { it.key == accentKey } ?: ACCENT_OPTIONS.first()
    val primary = if (usesDarkSurfaces) accent.darkColor else accent.lightColor
    return base.copy(
        primary = primary,
        onPrimary = if (usesDarkSurfaces) Color(0xFF111116) else Color.White
    )
}
