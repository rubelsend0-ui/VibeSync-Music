package com.vibesync.music

import com.vibesync.music.data.model.SearchPage
import com.vibesync.music.data.model.SearchType
import com.vibesync.music.data.model.Track
import com.vibesync.music.data.remote.AppError
import com.vibesync.music.data.repo.LocalSearch
import com.vibesync.music.data.repo.SearchBackend
import com.vibesync.music.data.repo.SearchHistory
import com.vibesync.music.ui.Loadable
import com.vibesync.music.ui.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeBackend(var pages: List<Result<SearchPage>>) : SearchBackend {
        val calls = mutableListOf<Triple<String, SearchType, Int>>()
        override suspend fun search(query: String, type: SearchType, page: Int): Result<SearchPage> {
            calls += Triple(query, type, page)
            return pages.getOrElse(calls.size - 1) { pages.last() }
        }
    }

    private class FakeHistory : SearchHistory {
        val terms = MutableStateFlow<List<String>>(emptyList())
        override val recentSearches = terms
        override suspend fun recordSearch(term: String) { terms.value = listOf(term) + terms.value }
        override suspend fun clearSearches() { terms.value = emptyList() }
        override suspend fun deleteSearch(term: String) { terms.value = terms.value - term }
    }

    private object NoLocal : LocalSearch { override suspend fun search(query: String) = emptyList<Track>() }

    private fun page(vararg titles: String, more: Boolean = false, p: Int = 1) = SearchPage(
        items = titles.map { Track(id = it, provider = "p", title = it) }, page = p, hasMore = more,
    )

    private fun vm(b: SearchBackend, h: FakeHistory = FakeHistory()) =
        SearchViewModel(b, h, NoLocal, flowOf(emptyList()), debounceMs = 300)

    @Test fun debouncesTypingAndSearchesOnce() = runTest(dispatcher) {
        val backend = FakeBackend(listOf(Result.success(page("Song"))))
        val history = FakeHistory()
        val vm = vm(backend, history)
        vm.setQuery("a"); vm.setQuery("ab"); vm.setQuery("abc")
        advanceTimeBy(299); runCurrent()
        assertTrue(backend.calls.isEmpty())
        advanceTimeBy(5); advanceUntilIdle()
        assertEquals(1, backend.calls.size)
        assertEquals("abc", backend.calls[0].first)
        val online = vm.state.value.online as Loadable.Ready
        assertEquals("Song", online.value.items.single().title)
        assertEquals(listOf("abc"), history.terms.value)
    }

    @Test fun blankQueryClearsResults() = runTest(dispatcher) {
        val vm = vm(FakeBackend(listOf(Result.success(page("Song")))))
        vm.setQuery("abc"); advanceTimeBy(400); advanceUntilIdle()
        vm.setQuery(""); advanceTimeBy(400); advanceUntilIdle()
        assertNull(vm.state.value.online)
    }

    @Test fun surfacesBackendErrorsInsteadOfFakeData() = runTest(dispatcher) {
        val vm = vm(FakeBackend(listOf(Result.failure(AppError.NotConfigured()))))
        vm.setQuery("abc"); advanceTimeBy(400); advanceUntilIdle()
        val online = vm.state.value.online
        assertTrue(online is Loadable.Failed && online.error is AppError.NotConfigured)
    }

    @Test fun loadMoreAppendsWithoutDuplicates() = runTest(dispatcher) {
        val backend = FakeBackend(listOf(Result.success(page("A", "B", more = true)), Result.success(page("B", "C", more = false, p = 2))))
        val vm = vm(backend)
        vm.setQuery("abc"); advanceTimeBy(400); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val online = vm.state.value.online as Loadable.Ready
        assertEquals(listOf("A", "B", "C"), online.value.items.map { it.title })
        assertEquals(false, online.value.hasMore)
        assertEquals(2, backend.calls[1].third)
    }
}
