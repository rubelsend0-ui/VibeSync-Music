package com.vibesync.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibesync.music.data.model.Track
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.components.MessageCard
import com.vibesync.music.ui.components.SectionHeader
import com.vibesync.music.ui.components.TrackRow
import com.vibesync.music.util.formatBytes
import kotlinx.coroutines.launch

private val tabs = listOf("Downloads", "Device", "Favorites", "Playlists", "Albums", "Artists", "History")
private enum class Sort(val label: String) { RECENT("Recently added"), TITLE("Title"), ARTIST("Artist") }

private fun List<Track>.sorted(sort: Sort): List<Track> = when (sort) {
    Sort.RECENT -> this
    Sort.TITLE -> sortedBy { it.title.lowercase() }
    Sort.ARTIST -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
}

@Composable
fun LibraryScreen(onOpenPlaylist: (Long) -> Unit, onOpenDownloads: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(Sort.RECENT.name) }
    var sortMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var hasPerm by remember { mutableStateOf(c.localMedia.hasPermission()) }
    var storage by remember { mutableStateOf(0L) }
    val askAudio = rememberAudioPermissionRequest { g -> hasPerm = g; if (g) scope.launch { c.localMedia.refresh() } }

    val offline by c.downloads.offlineTracks.collectAsStateWithLifecycle(emptyList())
    val local by c.localMedia.tracks.collectAsStateWithLifecycle()
    val favorites by c.library.favorites.collectAsStateWithLifecycle(emptyList())
    val historyFlow = remember { c.library.history(200) }
    val history by historyFlow.collectAsStateWithLifecycle(emptyList())
    val playlists by c.library.playlists.collectAsStateWithLifecycle(emptyList())
    val player by c.player.state.collectAsStateWithLifecycle()
    val sort = Sort.valueOf(sortName)

    LaunchedEffect(Unit) {
        if (c.localMedia.hasPermission()) c.localMedia.refresh()
        c.downloads.reconcile()
        storage = c.downloads.storageUsedBytes()
    }

    fun filter(l: List<Track>): List<Track> {
        val q = query.trim().lowercase()
        val f = if (q.isEmpty()) l else l.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || (it.album ?: "").lowercase().contains(q) }
        return f.sorted(sort)
    }
    val localKeys = remember(local) { local.map { it.key }.toSet() }
    val unavailable = remember(favorites, localKeys, hasPerm) {
        if (!hasPerm) emptySet() else favorites.filter { it.provider == "local" && it.key !in localKeys }.map { it.key }.toSet()
    }
    val all = remember(offline, local, favorites) { (offline + local + favorites.filter { it.key !in unavailable }).distinctBy { it.key } }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Text("Your library", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineLarge) }
        item {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i; expanded = null }, text = { Text(t) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("Search ${tabs[tab].lowercase()}") },
                )
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { sortMenu = true }) { Text(sort.label) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        Sort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sortName = s.name; sortMenu = false }) }
                    }
                }
            }
        }

        when (tab) {
            0 -> {
                item {
                    ListItem(
                        headlineContent = { Text("Storage used: ${formatBytes(storage)}") },
                        supportingContent = { Text("Manage the download queue, retry failures and free up space") },
                        trailingContent = { Icon(Icons.Default.Download, null) },
                        modifier = Modifier.clickable(onClick = onOpenDownloads),
                    )
                }
                trackItems(filter(offline), "No offline music yet. Tracks from sources that allow downloading (open licenses) can be saved from the track menu.", player.track?.key) { l, i -> c.player.playTracks(l, i) }
            }
            1 -> {
                if (!hasPerm) item {
                    MessageCard("Allow access to audio files to show music stored on this device.", primaryLabel = "Allow access", onPrimary = askAudio)
                } else trackItems(filter(local), "No music files found on this device.", player.track?.key) { l, i -> c.player.playTracks(l, i) }
            }
            2 -> trackItems(filter(favorites), "No favorites yet. Tap the heart in the player or the track menu.", player.track?.key, unavailable) { l, i -> c.player.playTracks(l, i) }
            3 -> {
                item {
                    SectionHeader("Playlists") { IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Create playlist") } }
                }
                val shown = playlists.filter { query.isBlank() || it.name.contains(query.trim(), true) }
                if (shown.isEmpty()) item { MessageCard("No playlists yet. Create one and add tracks from any track menu.", primaryLabel = "Create playlist", onPrimary = { showCreate = true }) }
                items(shown, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) }, supportingContent = { Text("${p.trackCount} tracks") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) },
                        modifier = Modifier.clickable { onOpenPlaylist(p.id) },
                    )
                }
            }
            4, 5 -> {
                val groups = filter(all).groupBy { if (tab == 4) (it.album ?: "Unknown album") else it.artist }.entries.toList()
                if (groups.isEmpty()) item { MessageCard("Nothing to group yet. Add music to your library first.") }
                groups.forEach { g ->
                    item(key = "g" + g.key) {
                        ListItem(
                            headlineContent = { Text(g.key) }, supportingContent = { Text("${g.value.size} tracks") },
                            trailingContent = { IconButton(onClick = { c.player.playTracks(g.value, 0, shuffle = true) }) { Icon(Icons.Default.Shuffle, "Shuffle ${g.key}") } },
                            modifier = Modifier.clickable { expanded = if (expanded == g.key) null else g.key },
                        )
                    }
                    if (expanded == g.key) items(g.value, key = { "gt" + g.key + it.key }) { t ->
                        TrackRow(t, onClick = { c.player.playTracks(g.value, g.value.indexOf(t)) }, isCurrent = player.track?.key == t.key)
                    }
                }
            }
            else -> trackItems(filter(history), "Your listening history is empty.", player.track?.key) { l, i -> c.player.playTracks(l, i) }
        }
    }

    if (showCreate) NamePlaylistDialog(title = "New playlist", initial = "", onDismiss = { showCreate = false }) {
        c.actions.createPlaylistAndAdd(it, null); showCreate = false
    }
}

private fun LazyListScope.trackItems(
    tracks: List<Track>, emptyText: String, currentKey: String?, unavailable: Set<String> = emptySet(),
    onPlay: (List<Track>, Int) -> Unit,
) {
    if (tracks.isEmpty()) { item { MessageCard(emptyText) }; return }
    items(tracks.size, key = { "t$it" + tracks[it].key }) { i ->
        val t = tracks[i]
        val gone = t.key in unavailable
        TrackRow(
            t, onClick = { if (!gone) onPlay(tracks.filter { it.key !in unavailable }, tracks.filter { it.key !in unavailable }.indexOf(t)) },
            isCurrent = currentKey == t.key, dimmed = gone,
            trailing = { if (gone) Text("File missing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) },
        )
    }
}

@Composable
fun NamePlaylistDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 60) name = it }, singleLine = true, label = { Text("Playlist name") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
