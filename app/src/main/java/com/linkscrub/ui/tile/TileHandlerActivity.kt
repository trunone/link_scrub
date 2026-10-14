package com.linkscrub.ui.tile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.linkscrub.cleaner.UrlCleaner
import com.linkscrub.data.settings.SettingsRepository
import com.linkscrub.data.shlink.ShlinkClient
import com.linkscrub.data.shlink.ShlinkResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TileHandlerActivity : ComponentActivity() {

    private val cleaner = UrlCleaner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip

        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val text = clipData.getItemAt(0).coerceToText(this)?.toString() ?: ""
        if (text.isBlank()) {
            Toast.makeText(this, "No text found in clipboard", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val extractedUrl = UrlCleaner.extractFirstUrl(text)
        if (extractedUrl == null) {
            Toast.makeText(this, "No URL found in clipboard", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cleanResult = cleaner.clean(extractedUrl)

        lifecycleScope.launch {
            val settingsRepo = SettingsRepository(this@TileHandlerActivity)
            val autoShorten = settingsRepo.autoShorten.first()
            var finalUrl = cleanResult.cleanedUrl
            var shortSuccess = false

            if (autoShorten) {
                val baseUrl = settingsRepo.serverBaseUrl.first()
                val apiKey = settingsRepo.getApiKey()
                val allowCleartext = settingsRepo.allowCleartext.first()
                val defaultTagsStr = settingsRepo.defaultTags.first()
                val tagsList = defaultTagsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                    val shlinkClient = ShlinkClient()
                    val res = shlinkClient.createShortUrl(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        longUrl = cleanResult.cleanedUrl,
                        tags = tagsList,
                        allowCleartext = allowCleartext
                    )
                    if (res is ShlinkResult.Success) {
                        finalUrl = res.data
                        shortSuccess = true
                    }
                }
            }

            val newClip = ClipData.newPlainText("Cleaned URL", finalUrl)
            clipboardManager.setPrimaryClip(newClip)

            val removedCount = cleanResult.removedParams.size
            val toastMsg = when {
                shortSuccess -> "Cleaned & shortened link copied!"
                removedCount > 0 -> "Cleaned link copied (removed $removedCount param${if (removedCount > 1) "s" else ""})"
                else -> "Link copied (no trackers found)"
            }
            Toast.makeText(this@TileHandlerActivity, toastMsg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
