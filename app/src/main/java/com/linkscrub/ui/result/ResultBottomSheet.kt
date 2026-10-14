package com.linkscrub.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkscrub.cleaner.model.CleanResult
import com.linkscrub.data.settings.SettingsRepository
import com.linkscrub.data.shlink.ShlinkClient
import com.linkscrub.data.shlink.ShlinkResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultBottomSheet(
    cleanResult: CleanResult,
    onDismiss: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onSaveHistory: (original: String, cleaned: String, shortUrl: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var customSlug by remember { mutableStateOf("") }
    var isShortening by remember { mutableStateOf(false) }
    var shortUrlResult by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val settingsRepository = remember { SettingsRepository(context) }
    val shlinkClient = remember { ShlinkClient() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "LinkScrub Result",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Original Link",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = cleanResult.originalUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Text(
                text = "Cleaned Link",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = cleanResult.cleanedUrl,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Text(
                text = "Removed Parameters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            if (cleanResult.removedParams.isEmpty()) {
                Text(
                    text = "No tracking parameters were removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                ) {
                    cleanResult.removedParams.forEach { param ->
                        FilterChip(
                            selected = true,
                            onClick = { },
                            label = { Text("${param.name}=${param.value}") }
                        )
                    }
                }
            }

            shortUrlResult?.let { shortUrl ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Short Link Created:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = shortUrl,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = {
                                copyToClipboard(context, shortUrl, "Short link copied")
                            }) {
                                Text("Copy Short Link")
                            }
                        }
                    }
                }
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = customSlug,
                onValueChange = { customSlug = it },
                label = { Text("Custom Short Slug (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val targetUrl = shortUrlResult ?: cleanResult.cleanedUrl
                        copyToClipboard(context, targetUrl, "Link copied to clipboard")
                        onSaveHistory(cleanResult.originalUrl, cleanResult.cleanedUrl, shortUrlResult)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy")
                }

                OutlinedButton(
                    onClick = {
                        val targetUrl = shortUrlResult ?: cleanResult.cleanedUrl
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, targetUrl)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                        onSaveHistory(cleanResult.originalUrl, cleanResult.cleanedUrl, shortUrlResult)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share")
                }

                Button(
                    onClick = {
                        scope.launch {
                            isShortening = true
                            errorMessage = null
                            val baseUrl = settingsRepository.serverBaseUrl.first()
                            val apiKey = settingsRepository.getApiKey()
                            val allowCleartext = settingsRepository.allowCleartext.first()
                            val defaultTagsStr = settingsRepository.defaultTags.first()
                            val tagsList = defaultTagsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                            val res = shlinkClient.createShortUrl(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                longUrl = cleanResult.cleanedUrl,
                                customSlug = customSlug,
                                tags = tagsList,
                                allowCleartext = allowCleartext
                            )

                            isShortening = false
                            when (res) {
                                is ShlinkResult.Success -> {
                                    shortUrlResult = res.data
                                    Toast.makeText(context, "Shortened successfully!", Toast.LENGTH_SHORT).show()
                                    onSaveHistory(cleanResult.originalUrl, cleanResult.cleanedUrl, res.data)
                                }
                                is ShlinkResult.Error -> {
                                    errorMessage = res.message
                                }
                            }
                        }
                    },
                    enabled = !isShortening,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isShortening) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Shorten")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Cleaned Link", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
