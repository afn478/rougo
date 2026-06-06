package com.selxo.rougo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReporter.install(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launch(Dispatchers.IO) {
            val engine = DictionaryEngine.getInstance(applicationContext)
            engine.loadDictionaries()
            val installed = engine.getInstalledDictionaries()
            if (installed.isEmpty() || installed.none { it.contains("JMdict", ignoreCase = true) }) {
                engine.downloadJmdict { }
            }
        }

        handleIntent(intent)

        setContent {
            val prefs = remember { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(
                    prefs.getString(PREF_THEME_MODE, null)
                        ?: if (prefs.getBoolean(PREF_LIGHT_MODE, false)) THEME_LIGHT else THEME_DARK
                )
            }
            var accentColor by remember { mutableStateOf(prefs.getString(PREF_ACCENT_COLOR, "purple") ?: "purple") }
            val systemDark = isSystemInDarkTheme()
            val usesDarkSystemBars = themeMode == THEME_DARK || themeMode == THEME_BLACK || (themeMode == THEME_SYSTEM && systemDark)
            val systemBarStyle = if (usesDarkSystemBars) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle
                )
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !usesDarkSystemBars
                    isAppearanceLightNavigationBars = !usesDarkSystemBars
                }
            }

            MaterialTheme(colorScheme = rougoColorScheme(themeMode, accentColor, systemDark)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.systemBarsPadding()) {
                        MainAppFlow(
                            sharedUrl = sharedUrlState.value,
                            onSharedUrlProcessed = { sharedUrlState.value = null },
                            themeMode = themeMode,
                            onThemeModeChanged = { mode ->
                                themeMode = mode
                                prefs.edit {
                                    putString(PREF_THEME_MODE, mode)
                                    putBoolean(PREF_LIGHT_MODE, mode == THEME_LIGHT)
                                }
                            },
                            accentColor = accentColor,
                            onAccentColorChanged = { accent ->
                                accentColor = accent
                                prefs.edit { putString(PREF_ACCENT_COLOR, accent) }
                            },
                            systemDark = systemDark
                        )
                        CrashReportDialog()
                        UpdateNotificationDialog()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = extractSharedVideoUrl(intent)

        if (url != null) {
            sharedUrlState.value = url
        }

        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW) {
            clearConsumedShareIntent()
        }
    }

    private fun clearConsumedShareIntent() {
        setIntent(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
        })
    }

    private fun extractSharedVideoUrl(intent: Intent?): String? {
        if (intent == null) return null

        val candidates = mutableListOf<String>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                candidates += listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                    intent.getStringExtra(Intent.EXTRA_TITLE)
                )

                intent.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i)
                            ?.coerceToText(this)
                            ?.toString()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { candidates += it }
                    }
                }
            }

            Intent.ACTION_VIEW -> {
                candidates += listOfNotNull(
                    intent.dataString,
                    intent.data?.toString()
                )
            }

            else -> return null
        }

        val urls = candidates
            .flatMap { extractAllUrls(it) }
            .map { normalizeVideoUrlCandidate(it) }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()

        return urls.firstOrNull { isSupportedVideoLink(it) }
            ?: urls.firstOrNull()
    }
}
