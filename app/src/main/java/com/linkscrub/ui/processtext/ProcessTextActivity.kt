package com.linkscrub.ui.processtext

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.linkscrub.cleaner.UrlCleaner
import com.linkscrub.data.history.AppDatabase
import com.linkscrub.data.history.HistoryEntity
import com.linkscrub.data.settings.SettingsRepository
import com.linkscrub.data.shlink.ShlinkClient
import com.linkscrub.data.shlink.ShlinkResult
import com.linkscrub.ui.result.ResultBottomSheet
import com.linkscrub.ui.theme.LinkScrubTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ProcessTextActivity : ComponentActivity() {

    private val cleaner = UrlCleaner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)
            ?: ""

        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        val isShortenAlias = checkIsShortenAlias()

        if (selectedText.isBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val textString = selectedText.toString()

        if (isShortenAlias) {
            handleCleanAndShortenAlias(textString, isReadOnly)
            return
        }

        if (!isReadOnly) {
            val cleanAllResult = cleaner.cleanAll(textString)
            if (cleanAllResult.totalParamsRemoved == 0) {
                Toast.makeText(this, "No trackers removed", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
            } else {
                val replaceIntent = Intent().apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, cleanAllResult.cleanedText)
                }
                setResult(RESULT_OK, replaceIntent)
                Toast.makeText(
                    this,
                    "Removed ${cleanAllResult.totalParamsRemoved} tracker${if (cleanAllResult.totalParamsRemoved > 1) "s" else ""}",
                    Toast.LENGTH_SHORT
                ).show()

                lifecycleScope.launch {
                    val settingsRepo = SettingsRepository(this@ProcessTextActivity)
                    if (settingsRepo.historyEnabled.first()) {
                        val db = AppDatabase.getDatabase(this@ProcessTextActivity)
                        cleanAllResult.cleanResults.forEach { cleanRes ->
                            db.historyDao().insertHistory(
                                HistoryEntity(
                                    originalUrl = cleanRes.originalUrl,
                                    cleanedUrl = cleanRes.cleanedUrl
                                )
                            )
                        }
                    }
                }
                finish()
            }
        } else {
            val cleanResult = cleaner.clean(textString)
            setContent {
                LinkScrubTheme {
                    ResultBottomSheet(
                        cleanResult = cleanResult,
                        onDismiss = { finish() },
                        onSaveHistory = { orig, cleaned, shortUrl ->
                            lifecycleScope.launch {
                                val settingsRepo = SettingsRepository(this@ProcessTextActivity)
                                if (settingsRepo.historyEnabled.first()) {
                                    val db = AppDatabase.getDatabase(this@ProcessTextActivity)
                                    db.historyDao().insertHistory(
                                        HistoryEntity(
                                            originalUrl = orig,
                                            cleanedUrl = cleaned,
                                            shortUrl = shortUrl
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkIsShortenAlias(): Boolean {
        return try {
            val component = intent.component ?: return false
            val activityInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
            }
            activityInfo.metaData?.getBoolean("IS_SHORTEN_ALIAS", false) ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun handleCleanAndShortenAlias(text: String, isReadOnly: Boolean) {
        lifecycleScope.launch {
            val cleanAllResult = cleaner.cleanAll(text)
            val settingsRepo = SettingsRepository(this@ProcessTextActivity)
            val baseUrl = settingsRepo.serverBaseUrl.first()
            val apiKey = settingsRepo.getApiKey()
            val allowCleartext = settingsRepo.allowCleartext.first()
            val defaultTagsStr = settingsRepo.defaultTags.first()
            val tagsList = defaultTagsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val firstUrl = UrlCleaner.extractFirstUrl(text)
            var replacementText = cleanAllResult.cleanedText

            if (firstUrl != null && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                val cleanedFirstUrl = cleaner.clean(firstUrl).cleanedUrl
                val shlinkClient = ShlinkClient()

                val shortUrl = withTimeoutOrNull(5000L) {
                    val res = shlinkClient.createShortUrl(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        longUrl = cleanedFirstUrl,
                        tags = tagsList,
                        allowCleartext = allowCleartext
                    )
                    if (res is ShlinkResult.Success) res.data else null
                }

                if (shortUrl != null) {
                    replacementText = text.replace(firstUrl, shortUrl)
                }
            }

            if (!isReadOnly) {
                val replaceIntent = Intent().apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, replacementText)
                }
                setResult(RESULT_OK, replaceIntent)
                Toast.makeText(this@ProcessTextActivity, "Cleaned & shortened link", Toast.LENGTH_SHORT).show()
            } else {
                setResult(RESULT_CANCELED)
                Toast.makeText(this@ProcessTextActivity, "Cleaned: $replacementText", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}
