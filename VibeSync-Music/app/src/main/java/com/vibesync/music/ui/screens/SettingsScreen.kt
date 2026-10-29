package com.vibesync.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibesync.music.BuildConfig
import com.vibesync.music.data.model.AppSettings
import com.vibesync.music.data.model.ThemePref
import com.vibesync.music.ui.Loadable
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.SettingsViewModel
import com.vibesync.music.ui.appViewModel
import com.vibesync.music.ui.components.MessageCard
import com.vibesync.music.ui.components.SectionHeader
import com.vibesync.music.ui.theme.AccentOptions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpenDownloads: () -> Unit) {
    val c = LocalAppContainer.current
    val vm = appViewModel<SettingsViewModel>()
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsStateWithLifecycle(AppSettings())
    val providers by vm.providers.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var url by remember(s.backendUrl) { mutableStateOf(s.backendUrl) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    LaunchedEffect(s.backendUrl) { vm.loadProviders() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Settings", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineLarge)

        SectionHeader("Appearance")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePref.values().forEach { t ->
                FilterChip(selected = s.theme == t, onClick = { scope.launch { vm.repo.setTheme(t) } }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentOptions.forEachIndexed { i, a ->
                Spacer(
                    Modifier.size(36.dp).clip(CircleShape).background(a.primary)
                        .border(if (i == s.accentIndex) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { scope.launch { vm.repo.setAccent(i) } },
                )
            }
        }

        SectionHeader("Audio quality")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Auto", "high" to "High", "low" to "Data saver").forEach { (k, label) ->
                FilterChip(selected = s.audioQuality == k, onClick = { scope.launch { vm.repo.setQuality(k) } }, label = { Text(label) })
            }
        }
        Text(
            "Applied only where a provider offers several qualities (for example Jamendo and Internet Archive). Local files always play as-is, and VibeSync never upscales or transcodes audio.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader("Downloads")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Download over Wi-Fi only", Modifier.weight(1f))
            Switch(checked = s.wifiOnlyDownloads, onCheckedChange = { scope.launch { vm.repo.setWifiOnly(it) } })
        }
        Text("Folder: ${c.downloads.directory.absolutePath}", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenDownloads, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Manage downloads") }

        SectionHeader("Backend & providers")
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true, label = { Text("Backend URL") },
            placeholder = { Text("https://your-backend.example.com") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveBackend(url) }) { Text("Save") }
            OutlinedButton(onClick = { vm.testConnection() }, enabled = s.backendUrl.isNotBlank() || s.demoMode) { Text("Test connection") }
        }
        status?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Demo mode")
                Text("Shows clearly labelled placeholder tracks (not playable) when no backend is set. For screenshots only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.demoMode, onCheckedChange = { scope.launch { vm.repo.setDemoMode(it) } })
        }
        when (val p = providers) {
            is Loadable.Loading -> Text("Checking providers…", Modifier.padding(16.dp))
            is Loadable.Failed -> MessageCard(p.error.message ?: "Couldn't load providers.", isError = p.error !is com.vibesync.music.data.remote.AppError.NotConfigured)
            is Loadable.Ready -> p.value.forEach { info ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("${info.displayName} — ${if (info.configured) "available" else "not configured"}", style = MaterialTheme.typography.titleSmall)
                    info.setupHint?.takeIf { !info.configured }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    info.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        SectionHeader("Data")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { c.clearCaches(); c.actions.say("Cache cleared") } }) { Text("Clear cache") }
            OutlinedButton(onClick = { vm.clearSearches() }) { Text("Clear search history") }
            OutlinedButton(onClick = { vm.clearHistory() }) { Text("Clear listening history") }
        }

        SectionHeader("About")
        Text("VibeSync Music ${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { showPrivacy = true }) { Text("Privacy") }
            TextButton(onClick = { showLicenses = true }) { Text("Open-source licenses") }
        }
    }

    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false }, title = { Text("Privacy") },
        text = {
            Text(
                "• Favorites, playlists and listening history are stored only on this device.\n" +
                    "• There are no accounts, ads or analytics.\n" +
                    "• Search text is sent only to the backend URL you configure, and the backend sends it to the music providers you enabled.\n" +
                    "• Audio file access is requested only when you open device music and is read-only.\n" +
                    "• Provider API keys live on your backend, never in this app.",
            )
        },
        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } },
    )
    if (showLicenses) AlertDialog(
        onDismissRequest = { showLicenses = false }, title = { Text("Open-source licenses") },
        text = {
            Text(
                "Apache License 2.0: AndroidX (Compose, Room, DataStore, Navigation, Media3, Palette, Core), Kotlin and kotlinx libraries, Coil, OkHttp, Retrofit.\n\n" +
                    "Full license texts are available in each project's repository.",
            )
        },
        confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("OK") } },
    )
}
