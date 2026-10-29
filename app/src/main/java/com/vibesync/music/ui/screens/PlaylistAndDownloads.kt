package com.vibesync.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibesync.music.download.DownloadItem
import com.vibesync.music.download.DownloadState
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.components.ArtworkImage
import com.vibesync.music.ui.components.MessageCard
import com.vibesync.music.ui.components.SectionHeader
import com.vibesync.music.ui.components.TrackRow
import com.vibesync.music.util.formatBytes
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(id: Long, onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val nameFlow = remember(id) { c.library.playlistName(id) }
    val itemsFlow = remember(id) { c.library.playlistItems(id) }
    val name by nameFlow.collectAsStateWithLifecycle(null)
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList())
    val player by c.player.state.collectAsStateWithLifecycle()
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tracks = items.map { it.track }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp, end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(name ?: "Playlist", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Rename playlist") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete playlist") }
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.player.playTracks(tracks, 0) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text(" Play") }
                OutlinedButton(onClick = { c.player.playTracks(tracks, 0, shuffle = true) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.Shuffle, null); Text(" Shuffle") }
            }
        }
        if (items.isEmpty()) item { MessageCard("This playlist is empty. Add tracks from the \"more\" menu on any track.") }
        items(items.size, key = { items[it].entryId }) { i ->
            val e = items[i]
            TrackRow(
                e.track, onClick = { c.player.playTracks(tracks, i) }, isCurrent = player.track?.key == e.track.key, showMenu = false,
                trailing = {
                    IconButton(onClick = { scope.launch { c.library.movePlaylistItem(id, i, i - 1) } }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { scope.launch { c.library.movePlaylistItem(id, i, i + 1) } }, enabled = i < items.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { scope.launch { c.library.removeFromPlaylist(e.entryId) } }) { Icon(Icons.Default.Close, "Remove from playlist") }
                },
            )
        }
    }
    if (rename) NamePlaylistDialog("Rename playlist", name.orEmpty(), { rename = false }) {
        scope.launch { runCatching { c.library.renamePlaylist(id, it) }.onFailure { e -> c.actions.say(e.message ?: "Couldn't rename") } }
        rename = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Delete playlist?") },
        text = { Text("The playlist is removed. Your tracks and files are not deleted.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { c.library.deletePlaylist(id); onBack() } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val items by c.downloads.items.collectAsStateWithLifecycle(emptyList())
    var storage by remember { mutableStateOf(0L) }
    LaunchedEffect(items) { storage = c.downloads.storageUsedBytes() }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Downloads", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "Storage used: ${formatBytes(storage)}  •  ${c.downloads.directory.absolutePath}",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Only tracks whose source explicitly allows downloading (for example Creative Commons music) can be saved.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.any { it.state == DownloadState.FAILED }) {
                TextButton(onClick = { scope.launch { c.downloads.clearFinishedHistory() } }, Modifier.padding(horizontal = 8.dp)) { Text("Clear failed downloads") }
            }
        }
        if (items.isEmpty()) item { SectionHeader("Queue"); MessageCard("No downloads. Use \"Download\" in a track's menu when a source allows it.") }
        items(items, key = { it.track.key }) { d -> DownloadRow(d, scope) }
    }
}

@Composable
private fun DownloadRow(d: DownloadItem, scope: kotlinx.coroutines.CoroutineScope) {
    val c = LocalAppContainer.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ArtworkImage(d.track.artworkUrl, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                val detail = listOfNotNull(
                    d.state.name.lowercase().replaceFirstChar { it.uppercase() },
                    d.format, if (d.total > 0) formatBytes(d.total) else null, d.track.license,
                ).joinToString(" • ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (d.state) {
                DownloadState.RUNNING, DownloadState.QUEUED ->
                    IconButton(onClick = { scope.launch { c.downloads.pause(d.track.key) } }) { Icon(Icons.Default.Pause, "Pause download") }
                DownloadState.PAUSED ->
                    IconButton(onClick = { scope.launch { c.downloads.resume(d.track.key) } }) { Icon(Icons.Default.PlayArrow, "Resume download") }
                DownloadState.FAILED ->
                    IconButton(onClick = { scope.launch { c.downloads.retry(d.track.key) } }) { Icon(Icons.Default.Refresh, "Retry download") }
                DownloadState.COMPLETED ->
                    IconButton(onClick = { c.player.playTracks(listOf(d.track.copy(streamUrl = android.net.Uri.fromFile(java.io.File(d.filePath)).toString(), playable = true)), 0) }) {
                        Icon(Icons.Default.PlayArrow, "Play offline copy")
                    }
            }
            IconButton(onClick = { scope.launch { c.downloads.remove(d.track.key) } }) {
                Icon(if (d.state == DownloadState.COMPLETED) Icons.Default.Delete else Icons.Default.Close, if (d.state == DownloadState.COMPLETED) "Delete file" else "Cancel download")
            }
        }
        if (d.state == DownloadState.RUNNING || d.state == DownloadState.PAUSED || d.state == DownloadState.QUEUED) {
            val p = d.progress
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            else if (d.state == DownloadState.RUNNING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            if (d.bytes > 0) Text("${formatBytes(d.bytes)} downloaded", style = MaterialTheme.typography.labelMedium)
        }
        d.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }
}
