package pk.vancott.tenders.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.data.TenderFeed
import pk.vancott.tenders.data.TenderRepository

enum class Scope { ALL, SMD, CLOSING_SOON }

data class UiState(
    val loading: Boolean = false,
    val feed: TenderFeed? = null,
    val error: String? = null,
    val query: String = "",
    val scope: Scope = Scope.ALL,
    val province: String? = null,
    val category: String? = null,
    val source: String? = null,
    val includeClosed: Boolean = false,
    val lastUpdated: Long = 0L,
)

class TenderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TenderRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Show the cache immediately, then refresh behind it.
        repo.cachedFeed()?.let { cached ->
            _state.update { it.copy(feed = cached, lastUpdated = repo.lastUpdated) }
        }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.refresh() }
                .onSuccess { feed ->
                    _state.update {
                        it.copy(loading = false, feed = feed, error = null,
                                lastUpdated = repo.lastUpdated)
                    }
                }
                .onFailure { e ->
                    // Keep whatever we already had on screen; just say what broke.
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Could not reach the feed")
                    }
                }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setScope(s: Scope) = _state.update { it.copy(scope = s) }
    fun setProvince(p: String?) = _state.update { it.copy(province = p) }
    fun setCategory(c: String?) = _state.update { it.copy(category = c) }
    fun setSource(s: String?) = _state.update { it.copy(source = s) }
    fun toggleClosed() = _state.update { it.copy(includeClosed = !it.includeClosed) }
    fun clearFilters() = _state.update {
        it.copy(query = "", province = null, category = null, source = null,
                scope = Scope.ALL, includeClosed = false)
    }

    fun markSmdSeen() {
        _state.value.feed?.let { repo.markSeen(repo.unseenSmd(it)) }
    }

    fun tenderByUid(uid: String): Tender? =
        _state.value.feed?.tenders?.firstOrNull { it.uid == uid }
}

/**
 * Filtering, kept out of the ViewModel so it can be reasoned about (and tested)
 * on its own.
 *
 * Search deliberately requires ALL words to match, in any order and any field.
 * "led karachi" should mean led AND karachi - an OR search over 3,000 tenders
 * returns everything and is useless.
 */
fun applyFilters(all: List<Tender>, s: UiState): List<Tender> {
    val words = s.query.trim().lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }

    return all.asSequence()
        .filter { s.includeClosed || !it.isClosed }
        .filter {
            when (s.scope) {
                Scope.ALL -> true
                Scope.SMD -> it.isSmd
                Scope.CLOSING_SOON -> it.daysLeft?.let { d -> d in 0L..7L } ?: false
            }
        }
        .filter { s.province == null || it.province == s.province }
        .filter { s.category == null || it.category == s.category }
        .filter { s.source == null || it.source == s.source }
        .filter { t -> words.all { w -> t.haystack.contains(w) } }
        .sortedWith(
            // Soonest real deadline first; undated and closed sink to the bottom.
            compareBy(
                { it.isClosed },
                { it.daysLeft ?: Long.MAX_VALUE },
                { it.title },
            )
        )
        .toList()
}
