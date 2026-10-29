package com.vibesync.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibesync.music.AppContainer
import com.vibesync.music.data.model.PlaylistSummary
import com.vibesync.music.data.model.ProviderInfo
import com.vibesync.music.data.model.SearchPage
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.remote.AppError
import com.vibesync.music.data.remote.toAppError
import com.vibesync.music.data.repo.CatalogRepository
import com.vibesync.music.data.repo.LibraryRepository
import com.vibesync.music.data.repo.LocalMediaRepository
import com.vibesync.music.data.repo.LocalSearch
import com.vibesync.music.data.repo.SearchBackend
import com.vibesync.music.data.repo.SearchHistory
import com.vibesync.music.data.repo.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val error: AppError) : Loadable<Nothing>
}

private fun <T> Result<T>.toLoadable(): Loadable<T> =
    fold({ Loadable.Ready(it) }, { Loadable.Failed(it.toAppError()) })

class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(c.catalog, c.library, c.localMedia)
        modelClass.isAssignableFrom(SearchViewModel::class.java) ->
            SearchViewModel(c.catalog, c.library, c.localMedia, c.library.playlists)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(c.settings, c.catalog, c.library)
        else -> error("Unknown ViewModel ${modelClass.name}")
    } as T
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM = viewModel(factory = VmFactory(LocalAppContainer.current))

// ------------------------------------------------------------------ Home
val HomeCategories = listOf("trending", "new_releases", "popular", "focus", "chill", "workout", "romantic")

data class Personalized(val artist: String?, val data: Loadable<SearchPage>)

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val local: LocalMediaRepository,
) : ViewModel() {
    private val _sections = MutableStateFlow<Map<String, Loadable<SearchPage>>>(HomeCategories.associateWith { Loadable.Loading })
    val sections: StateFlow<Map<String, Loadable<SearchPage>>> = _sections.asStateFlow()
    private val _personal = MutableStateFlow(Personalized(null, Loadable.Loading))
    val personal: StateFlow<Personalized> = _personal.asStateFlow()

    val recent: StateFlow<List<Track>> = library.history(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localTracks: StateFlow<List<Track>> = local.tracks

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        _sections.value = HomeCategories.associateWith { Loadable.Loading }
        _personal.value = Personalized(null, Loadable.Loading)
        viewModelScope.launch {
            coroutineScope {
                HomeCategories.forEach { cat ->
                    launch { val r = catalog.recommendations(cat).toLoadable(); _sections.update { it + (cat to r) } }
                }
                launch { loadPersonal() }
            }
        }
        viewModelScope.launch { local.refresh() }
    }

    private suspend fun loadPersonal() {
        val top = library.historyOnce(200).filter { it.artist != "Unknown artist" }
            .groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key
        if (top == null) { _personal.value = Personalized(null, Loadable.Ready(SearchPage())); return }
        _personal.value = Personalized(top, catalog.search(top, SearchType.ARTIST, 1).toLoadable())
    }

    fun refreshLocal() { viewModelScope.launch { local.refresh() } }
}

// ------------------------------------------------------------------ Search
data class OnlineResults(val items: List<Track>, val page: Int, val hasMore: Boolean, val notices: List<String>)

data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.TRACK,
    val online: Loadable<OnlineResults>? = null,
    val local: List<Track> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val loadingMore: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val backend: SearchBackend,
    private val history: SearchHistory,
    private val localSearch: LocalSearch,
    private val allPlaylists: Flow<List<PlaylistSummary>>,
    debounceMs: Long = 350,
) : ViewModel() {
    private val queryFlow = MutableStateFlow("")
    private val typeFlow = MutableStateFlow(SearchType.TRACK)
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    val recent: StateFlow<List<String>> = history.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(queryFlow, typeFlow) { q, t -> q.trim() to t }
                .debounce(debounceMs)
                .distinctUntilChanged()
                .collectLatest { (q, t) -> run(q, t) }
        }
    }

    fun setQuery(q: String) { _state.update { it.copy(query = q) }; queryFlow.value = q }
    fun setType(t: SearchType) { _state.update { it.copy(type = t) }; typeFlow.value = t }
    fun retry() { viewModelScope.launch { run(queryFlow.value.trim(), typeFlow.value) } }
    fun clearHistory() { viewModelScope.launch { history.clearSearches() } }
    fun deleteHistory(term: String) { viewModelScope.launch { history.deleteSearch(term) } }

    private suspend fun run(q: String, type: SearchType) {
        if (q.isEmpty()) { _state.update { it.copy(online = null, local = emptyList(), playlists = emptyList()) }; return }
        val lower = q.lowercase()
        val playlists = allPlaylists.first().filter { it.name.lowercase().contains(lower) }
        _state.update { it.copy(online = Loadable.Loading, local = emptyList(), playlists = playlists, loadingMore = false) }
        val local = localSearch.search(q)
        _state.update { it.copy(local = local) }
        val result = backend.search(q, type, 1)
        _state.update {
            it.copy(online = result.fold(
                { p -> Loadable.Ready(OnlineResults(p.items, p.page, p.hasMore, p.notices)) },
                { e -> Loadable.Failed(e.toAppError()) },
            ))
        }
        if (result.isSuccess) history.recordSearch(q)
    }

    fun loadMore() {
        val s = _state.value
        val cur = (s.online as? Loadable.Ready)?.value ?: return
        if (!cur.hasMore || s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = backend.search(s.query.trim(), s.type, cur.page + 1)
            _state.update { st ->
                val now = (st.online as? Loadable.Ready)?.value
                if (now == null) st.copy(loadingMore = false) else r.fold(
                    { p ->
                        val seen = now.items.map { it.key }.toSet()
                        st.copy(
                            loadingMore = false,
                            online = Loadable.Ready(now.copy(items = now.items + p.items.filter { it.key !in seen }, page = p.page, hasMore = p.hasMore)),
                        )
                    },
                    { st.copy(loadingMore = false) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Settings
class SettingsViewModel(
    val repo: SettingsRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val settings = repo.settings
    private val _providers = MutableStateFlow<Loadable<List<ProviderInfo>>>(Loadable.Loading)
    val providers: StateFlow<Loadable<List<ProviderInfo>>> = _providers.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun saveBackend(url: String) {
        viewModelScope.launch {
            repo.setBackendUrl(url).fold(
                { _status.value = if (it.isEmpty()) "Backend cleared." else "Saved."; loadProviders() },
                { _status.value = it.message },
            )
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _status.value = "Testing…"
            _status.value = catalog.health().fold(
                { "Backend reachable (status: $it)." },
                { it.toAppError().message },
            )
            loadProviders()
        }
    }

    fun loadProviders() {
        viewModelScope.launch {
            _providers.value = Loadable.Loading
            _providers.value = catalog.providers().toLoadable()
        }
    }

    fun clearHistory() { viewModelScope.launch { library.clearHistory(); _status.value = "Listening history cleared." } }
    fun clearSearches() { viewModelScope.launch { library.clearSearches(); _status.value = "Search history cleared." } }
    fun consumeStatus() { _status.value = null }
}
