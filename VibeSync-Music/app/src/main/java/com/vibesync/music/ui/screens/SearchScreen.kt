package com.vibesync.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.ui.Loadable
import com.vibesync.music.ui.LocalAppContainer
import com.vibesync.music.ui.SearchViewModel
import com.vibesync.music.ui.appViewModel
import com.vibesync.music.ui.components.MessageCard
import com.vibesync.music.ui.components.SectionHeader
import com.vibesync.music.ui.components.SkeletonList
import com.vibesync.music.ui.components.TrackRow
import androidx.compose.runtime.remember

@Composable
fun SearchScreen(
    initialQuery: String,
    initialType: SearchType,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm = appViewModel<SearchViewModel>()
    val st by vm.state.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val player by c.player.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }

    LaunchedEffect(initialQuery, initialType) {
        if (initialQuery.isNotBlank()) { text = initialQuery; vm.setType(initialType); vm.setQuery(initialQuery) }
        else { vm.setQuery(text) }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; vm.setQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(requester),
                singleLine = true,
                placeholder = { Text("Songs, artists, albums, playlists") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = ""; vm.setQuery("") }) { Icon(Icons.Default.Clear, "Clear search") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            )
        }

        if (text.isBlank()) {
            item {
                SectionHeader("Recent searches") {
                    if (recent.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("Clear all") }
                }
            }
            if (recent.isEmpty()) item { MessageCard("Your recent searches will appear here.") }
            items(recent, key = { it }) { term ->
                ListItem(
                    headlineContent = { Text(term) },
                    leadingContent = { Icon(Icons.Default.History, null) },
                    trailingContent = { IconButton(onClick = { vm.deleteHistory(term) }) { Icon(Icons.Default.Clear, "Remove $term") } },
                    modifier = Modifier.clickable { text = term; vm.setQuery(term) },
                )
            }
            return@LazyColumn
        }

        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchType.values().toList()) { t ->
                    FilterChip(selected = st.type == t, onClick = { vm.setType(t) }, label = { Text(t.label) })
                }
                val suggestions = recent.filter { it.contains(text.trim(), true) && !it.equals(text.trim(), true) }.take(4)
                items(suggestions) { s ->
                    FilterChip(selected = false, onClick = { text = s; vm.setQuery(s) }, label = { Text(s) },
                        leadingIcon = { Icon(Icons.Default.History, null) })
                }
            }
        }

        if (st.playlists.isNotEmpty()) {
            item { SectionHeader("Your playlists") }
            items(st.playlists, key = { "pl" + it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${p.trackCount} tracks") },
                    modifier = Modifier.clickable { onOpenPlaylist(p.id) },
                )
            }
        }

        if (st.local.isNotEmpty()) {
            item { SectionHeader("On this device") }
            items(st.local, key = { "l" + it.key }) { t ->
                TrackRow(t, onClick = { c.player.playTracks(st.local, st.local.indexOf(t)) }, isCurrent = player.track?.key == t.key)
            }
        }

        item { SectionHeader("Online • ${st.type.label}") }
        when (val online = st.online) {
            null, Loadable.Loading -> item { SkeletonList() }
            is Loadable.Failed -> item {
                val notConfigured = online.error is com.vibesync.music.data.remote.AppError.NotConfigured
                MessageCard(
                    text = if (notConfigured) "${online.error.message} Local results above still work." else online.error.message ?: "Search failed.",
                    isError = !notConfigured,
                    primaryLabel = if (notConfigured) "Open settings" else "Retry",
                    onPrimary = if (notConfigured) onOpenSettings else vm::retry,
                )
            }
            is Loadable.Ready -> {
                val r = online.value
                if (r.items.isEmpty()) item {
                    MessageCard(r.notices.joinToString("\n").ifBlank { "No online results for \"${text.trim()}\"." })
                } else {
                    if (r.notices.isNotEmpty()) item {
                        androidx.compose.material3.Text(
                            r.notices.joinToString("\n"), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    items(r.items, key = { "o" + it.key }) { t ->
                        TrackRow(
                            t, onClick = { c.player.playTracks(r.items, r.items.indexOf(t)) }, isCurrent = player.track?.key == t.key,
                            trailing = {
                                if (t.canPlayInApp) IconButton(onClick = { c.player.addToQueue(t) }) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add ${t.title} to queue")
                                }
                            },
                        )
                    }
                    if (r.hasMore) item(key = "more") {
                        LaunchedEffect(r.items.size) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
