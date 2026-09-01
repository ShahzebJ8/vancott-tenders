package pk.vancott.tenders.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.vancott.tenders.data.SavedSearch
import pk.vancott.tenders.data.Tender
import pk.vancott.tenders.data.TenderFeed
import pk.vancott.tenders.data.TenderNote
import pk.vancott.tenders.data.AwardFeed
import pk.vancott.tenders.data.NewsFeed
import pk.vancott.tenders.data.TenderRepository
import pk.vancott.tenders.data.UserDataStore
import pk.vancott.tenders.data.matches

enum class Scope(val label: String) {
    ALL("All"),
    SMD("SMD"),
    CLOSING_SOON("Closing"),
    STARRED("Shortlist"),
}

/** How the list is ordered. Labels are what the user sees in the filter panel. */
enum class SortBy(val label: String) {
    CLOSING_SOONEST("Closing soonest"),
    NEWEST("Recently published"),
    DEPARTMENT("Department A-Z"),
    TITLE("Title A-Z"),
}

data class UiState(
    val starting: Boolean = true,
    val loading: Boolean = false,
    val feed: TenderFeed? = null,
    val error: String? = null,
    val query: String = "",
    val scope: Scope = Scope.ALL,
    val province: String? = null,
    val source: String? = null,
    // Category and department come from the source websites verbatim.
    val category: String? = null,
    val department: String? = null,
    val assignedTo: String? = null,
    val city: String? = null,
    val withDocsOnly: Boolean = false,
    val sortBy: SortBy = SortBy.CLOSING_SOONEST,
    // Which kind of notice is being shown. Comes from the portals, not from us.
    val stage: Stage = Stage.ACTIVE,
    val news: NewsFeed? = null,
    val awards: AwardFeed? = null,
    val includeClosed: Boolean = true,
    val lastUpdated: Long = 0L,
    // User's own data, kept here so the list can show stars without a lookup.
    val notes: Map<String, TenderNote> = emptyMap(),
    val searches: List<SavedSearch> = emptyList(),
    val people: List<String> = emptyList(),
)

@OptIn(FlowPreview::class)
class TenderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TenderRepository(app)
    private val store = UserDataStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * The visible list.
     *
     * Filtering and sorting 3,000 tenders is real work, so it happens on a
     * background thread, and typing is debounced. Doing it inline in the UI -
     * re-sorting the whole list on every keystroke - is what made typing lag.
     */
    val results: StateFlow<List<Tender>> = _state
        .debounce { if (it.query.isBlank()) 0L else 120L }
        .distinctUntilChanged { a, b -> a.filterKey() == b.filterKey() }
        .map { s -> applyFilters(s.feed?.tenders.orEmpty(), s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // Parsing 3 MB of JSON is far too slow for the main thread; it runs
            // off-thread while the splash is up.
            val cached = withContext(Dispatchers.Default) {
                repo.cachedFeed()?.also { warmCaches(it) }
            }
            val news = withContext(Dispatchers.IO) { repo.cachedNews() }
            val awards = withContext(Dispatchers.IO) { repo.cachedAwards() }
            val data = withContext(Dispatchers.IO) { store.load() }
            _state.update {
                it.copy(
                    starting = false, feed = cached, news = news, awards = awards,
                    lastUpdated = repo.lastUpdated,
                    notes = data.notes, searches = data.searches, people = data.people,
                )
            }
            refresh()
        }
    }

    /**
     * Builds each tender's search text and date maths up front, in the
     * background. Left lazy, this work lands on the first keystroke and the
     * first scroll - exactly when it is most visible.
     */
    private fun warmCaches(feed: TenderFeed) {
        feed.tenders.forEach {
            it.haystack
            it.daysLeft
            it.isClosed
        }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val feed = repo.refresh()
                withContext(Dispatchers.Default) { warmCaches(feed) }
                feed
            }
                .onSuccess { feed ->
                    _state.update {
                        it.copy(loading = false, feed = feed, error = null,
                                lastUpdated = repo.lastUpdated)
                    }
                    // News is a separate, smaller file; a failure here must not
                    // affect the tender list.
                    runCatching { repo.refreshNews() }
                        .onSuccess { n -> _state.update { it.copy(news = n) } }
                    runCatching { repo.refreshAwards() }
                        .onSuccess { a -> _state.update { it.copy(awards = a) } }
                }
                .onFailure { e ->
                    // "Nothing changed" is a success: the feed had no new tenders.
                    if (e is TenderRepository.NotModified) {
                        _state.update { it.copy(loading = false, error = null) }
                    } else {
                        _state.update {
                            it.copy(loading = false, error = e.message ?: "No connection")
                        }
                    }
                }
        }
    }

    // --- filters ---------------------------------------------------------

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setScope(s: Scope) = _state.update { it.copy(scope = s) }
    fun setProvince(p: String?) = _state.update { it.copy(province = p) }
    fun setSource(s: String?) = _state.update { it.copy(source = s) }
    fun setCategory(c: String?) = _state.update { it.copy(category = c) }
    fun setDepartment(d: String?) = _state.update { it.copy(department = d) }
    fun setCity(c: String?) = _state.update { it.copy(city = c) }
    fun setAssignedTo(p: String?) = _state.update { it.copy(assignedTo = p) }
    fun setSortBy(s: SortBy) = _state.update { it.copy(sortBy = s) }
    fun setStage(st: Stage) = _state.update { it.copy(stage = st) }
    fun toggleClosed() = _state.update { it.copy(includeClosed = !it.includeClosed) }
    fun toggleDocsOnly() = _state.update { it.copy(withDocsOnly = !it.withDocsOnly) }
    fun clearFilters() = _state.update {
        it.copy(query = "", province = null, source = null, category = null,
                department = null, city = null, assignedTo = null,
                withDocsOnly = false, scope = Scope.ALL, includeClosed = true,
                sortBy = SortBy.CLOSING_SOONEST)
    }

    // --- user data -------------------------------------------------------

    private fun reloadUserData() {
        val d = store.load()
        _state.update { it.copy(notes = d.notes, searches = d.searches, people = d.people) }
    }

    fun toggleStar(uid: String) { store.toggleStar(uid); reloadUserData() }
    fun setNote(uid: String, text: String) { store.setNote(uid, text); reloadUserData() }
    fun assign(uid: String, person: String) { store.assign(uid, person); reloadUserData() }
    fun setRemind(uid: String, days: Int) { store.setRemind(uid, days); reloadUserData() }
    fun addSearch(name: String, query: String) { store.addSearch(name, query); reloadUserData() }
    fun removeSearch(id: String) { store.removeSearch(id); reloadUserData() }
    fun toggleSearchNotify(id: String) { store.toggleSearchNotify(id); reloadUserData() }

    fun noteFor(uid: String): TenderNote = _state.value.notes[uid] ?: TenderNote()

    fun tenderByUid(uid: String): Tender? =
        _state.value.feed?.tenders?.firstOrNull { it.uid == uid }

    /** Live count for a saved search, so the list shows what each one finds. */
    fun countFor(search: SavedSearch): Int =
        _state.value.feed?.tenders?.count { !it.isClosed && it.matches(search.query) } ?: 0
}

/** Only the fields that change what is displayed. Prevents a rebuild of the
 *  whole list when something unrelated (a note, a refresh flag) changes. */
private fun UiState.filterKey() = listOf(
    feed?.generated, query, scope, province, source, category, department,
    city, assignedTo, withDocsOnly, includeClosed, sortBy, stage,
    if (scope == Scope.STARRED) notes.filterValues { it.starred }.keys else null,
    if (assignedTo != null) notes else null,
)

fun applyFilters(all: List<Tender>, s: UiState): List<Tender> {
    val words = s.query.trim().lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }

    return all.asSequence()
        // The stage bar decides what kind of notice is listed, so it replaces
        // the old "include closed" question entirely.
        .filter { stageOf(it) == s.stage }
        .filter {
            when (s.scope) {
                Scope.ALL -> true
                Scope.SMD -> it.isSmd
                Scope.CLOSING_SOON -> it.daysLeft?.let { d -> d in 0L..7L } ?: false
                Scope.STARRED -> s.notes[it.uid]?.starred == true
            }
        }
        .filter { s.province == null || it.province == s.province }
        .filter { s.source == null || it.source == s.source }
        .filter { s.category == null || it.category == s.category }
        .filter { s.department == null || it.organisation == s.department }
        .filter { s.city == null || it.city == s.city }
        .filter { s.assignedTo == null || s.notes[it.uid]?.assignedTo == s.assignedTo }
        .filter { !s.withDocsOnly || it.docUrls.isNotEmpty() }
        .filter { t -> words.all { w -> t.haystack.contains(w) } }
        .toList()
        .sortedWith(sorter(s.sortBy))
}

/** Closed tenders always sink to the bottom, whichever order is chosen. */
private fun sorter(by: SortBy): Comparator<Tender> = when (by) {
    SortBy.CLOSING_SOONEST ->
        compareBy<Tender> { it.isClosed }
            .thenBy { it.closingSortKey }
            .thenBy { it.title }
    SortBy.NEWEST ->
        compareBy<Tender> { it.isClosed }
            .thenByDescending { it.published ?: "" }
            .thenBy { it.title }
    SortBy.DEPARTMENT ->
        compareBy<Tender> { it.isClosed }
            .thenBy { it.organisation ?: "￿" }
            .thenBy { it.title }
    SortBy.TITLE ->
        compareBy<Tender> { it.isClosed }
            .thenBy { it.title }
}
