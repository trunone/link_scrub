package com.linkscrub.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkscrub.cleaner.UrlCleaner
import com.linkscrub.cleaner.model.CleanResult
import com.linkscrub.data.history.AppDatabase
import com.linkscrub.data.history.HistoryEntity
import com.linkscrub.data.settings.SettingsRepository
import com.linkscrub.data.shlink.ShlinkClient
import com.linkscrub.data.shlink.ShlinkResult
import com.linkscrub.ui.history.HistoryScreen
import com.linkscrub.ui.result.ResultBottomSheet
import com.linkscrub.ui.settings.SettingsScreen
import com.linkscrub.ui.theme.LinkScrubTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val cleaner = UrlCleaner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var sharedCleanResult by mutableStateOf<CleanResult?>(null)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                sharedCleanResult = cleaner.clean(sharedText)
            }
        }

        setContent {
            LinkScrubTheme {
                MainApp(
                    initialCleanResult = sharedCleanResult,
                    onClearCleanResult = { sharedCleanResult = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                val cleanRes = cleaner.clean(sharedText)
                setContent {
                    LinkScrubTheme {
                        MainApp(
                            initialCleanResult = cleanRes,
                            onClearCleanResult = { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp(
    initialCleanResult: CleanResult?,
    onClearCleanResult: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var activeCleanResult by remember { mutableStateOf(initialCleanResult) }

    val db = remember { AppDatabase.getDatabase(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val shlinkClient = remember { ShlinkClient() }

    LaunchedEffect(initialCleanResult) {
        activeCleanResult = initialCleanResult
        if (initialCleanResult != null) {
            val autoShorten = settingsRepo.autoShorten.first()
            if (autoShorten) {
                val baseUrl = settingsRepo.serverBaseUrl.first()
                val apiKey = settingsRepo.getApiKey()
                val allowCleartext = settingsRepo.allowCleartext.first()
                val defaultTagsStr = settingsRepo.defaultTags.first()
                val tagsList = defaultTagsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                    val res = shlinkClient.createShortUrl(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        longUrl = initialCleanResult.cleanedUrl,
                        tags = tagsList,
                        allowCleartext = allowCleartext
                    )
                    val shortUrl = if (res is ShlinkResult.Success) res.data else null
                    saveHistoryIfEnabled(settingsRepo, db, initialCleanResult.originalUrl, initialCleanResult.cleanedUrl, shortUrl)
                } else {
                    saveHistoryIfEnabled(settingsRepo, db, initialCleanResult.originalUrl, initialCleanResult.cleanedUrl, null)
                }
            } else {
                saveHistoryIfEnabled(settingsRepo, db, initialCleanResult.originalUrl, initialCleanResult.cleanedUrl, null)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )

                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "History") },
                    label = { Text("History") },
                    selected = currentRoute == "history",
                    onClick = {
                        navController.navigate("history") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "settings",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("settings") {
                SettingsScreen()
            }
            composable("history") {
                HistoryScreen()
            }
        }

        activeCleanResult?.let { res ->
            ResultBottomSheet(
                cleanResult = res,
                onDismiss = {
                    activeCleanResult = null
                    onClearCleanResult()
                },
                onSaveHistory = { orig, cleaned, shortUrl ->
                    scope.launch {
                        saveHistoryIfEnabled(settingsRepo, db, orig, cleaned, shortUrl)
                    }
                }
            )
        }
    }
}

private suspend fun saveHistoryIfEnabled(
    settingsRepo: SettingsRepository,
    db: AppDatabase,
    original: String,
    cleaned: String,
    shortUrl: String?
) {
    if (settingsRepo.historyEnabled.first()) {
        db.historyDao().insertHistory(
            HistoryEntity(
                originalUrl = original,
                cleanedUrl = cleaned,
                shortUrl = shortUrl
            )
        )
        db.historyDao().trimOldEntries()
    }
}
