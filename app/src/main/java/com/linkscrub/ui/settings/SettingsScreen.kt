package com.linkscrub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.linkscrub.data.settings.SettingsRepository
import com.linkscrub.data.shlink.ShlinkClient
import com.linkscrub.data.shlink.ShlinkResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository = SettingsRepository(LocalContext.current)
) {
    val scope = rememberCoroutineScope()
    val baseUrl by repository.serverBaseUrl.collectAsState(initial = "")
    val allowCleartext by repository.allowCleartext.collectAsState(initial = false)
    val defaultTags by repository.defaultTags.collectAsState(initial = "")
    val autoShorten by repository.autoShorten.collectAsState(initial = false)
    val historyEnabled by repository.historyEnabled.collectAsState(initial = true)

    var apiKeyInput by remember { mutableStateOf("") }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiKeyInput = repository.getApiKey()
    }

    val shlinkClient = remember { ShlinkClient() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LinkScrub Settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Shlink Server Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { scope.launch { repository.updateServerBaseUrl(it) } },
                label = { Text("Server Base URL") },
                placeholder = { Text("https://s.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    repository.saveApiKey(it)
                },
                label = { Text("Shlink API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = defaultTags,
                onValueChange = { scope.launch { repository.updateDefaultTags(it) } },
                label = { Text("Default Tags (comma separated)") },
                placeholder = { Text("android, linkscrub") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow Cleartext HTTP", style = MaterialTheme.typography.bodyLarge)
                    Text("Enable non-HTTPS HTTP URLs to Shlink", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = allowCleartext,
                    onCheckedChange = { scope.launch { repository.updateAllowCleartext(it) } }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-shorten after cleaning", style = MaterialTheme.typography.bodyLarge)
                    Text("Automatically create short URL when cleaning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoShorten,
                    onCheckedChange = { scope.launch { repository.updateAutoShorten(it) } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        isTesting = true
                        testStatusMessage = null
                        val result = shlinkClient.createShortUrl(
                            baseUrl = baseUrl,
                            apiKey = apiKeyInput,
                            longUrl = "https://example.com/test-connection",
                            tags = listOf("test"),
                            allowCleartext = allowCleartext
                        )
                        isTesting = false
                        testStatusMessage = when (result) {
                            is ShlinkResult.Success -> "Connection successful! Created test link."
                            is ShlinkResult.Error -> "Connection failed: ${result.message}"
                        }
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Test Connection")
                }
            }

            testStatusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.startsWith("Connection successful")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "App Features",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Link History", style = MaterialTheme.typography.bodyLarge)
                    Text("Keep local history of cleaned and shortened links", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = historyEnabled,
                    onCheckedChange = { scope.launch { repository.updateHistoryEnabled(it) } }
                )
            }
        }
    }
}
