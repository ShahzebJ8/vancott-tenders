package pk.vancott.tenders.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Everything the user adds themselves: shortlist, notes, who is handling what,
 * and their own saved keyword searches.
 *
 * Stored as one small JSON file on the phone. It is deliberately separate from
 * the scraped feed, which is replaced wholesale on every refresh - user work
 * must survive that.
 */
@Serializable
data class SavedSearch(
    val id: String,
    val name: String,
    val query: String,
    val notify: Boolean = true,
)

@Serializable
data class TenderNote(
    val starred: Boolean = false,
    val note: String = "",
    val assignedTo: String = "",
    /** Remind this many days before closing. 0 = no reminder. */
    val remindDaysBefore: Int = 0,
)

@Serializable
data class UserDataFile(
    val searches: List<SavedSearch> = emptyList(),
    val notes: Map<String, TenderNote> = emptyMap(),
    val people: List<String> = emptyList(),
)

class UserDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File get() = File(context.filesDir, "userdata.json")

    fun load(): UserDataFile =
        runCatching {
            if (file.exists()) json.decodeFromString<UserDataFile>(file.readText())
            else UserDataFile()
        }.getOrDefault(UserDataFile())

    fun save(data: UserDataFile) {
        runCatching { file.writeText(json.encodeToString(UserDataFile.serializer(), data)) }
    }

    // --- per-tender ------------------------------------------------------

    fun noteFor(uid: String): TenderNote = load().notes[uid] ?: TenderNote()

    private fun updateNote(uid: String, block: (TenderNote) -> TenderNote) {
        val data = load()
        val updated = block(data.notes[uid] ?: TenderNote())
        val notes = data.notes.toMutableMap()
        // An entry that holds nothing is removed rather than kept as clutter.
        if (updated == TenderNote()) notes.remove(uid) else notes[uid] = updated
        save(data.copy(notes = notes))
    }

    fun toggleStar(uid: String) = updateNote(uid) { it.copy(starred = !it.starred) }
    fun setNote(uid: String, text: String) = updateNote(uid) { it.copy(note = text) }
    fun setRemind(uid: String, days: Int) = updateNote(uid) { it.copy(remindDaysBefore = days) }

    fun assign(uid: String, person: String) {
        updateNote(uid) { it.copy(assignedTo = person) }
        if (person.isNotBlank()) {
            val data = load()
            if (person !in data.people) {
                // Remember the name so it can be picked from a list next time.
                save(data.copy(people = (data.people + person).sorted()))
            }
        }
    }

    fun people(): List<String> = load().people

    // --- saved searches --------------------------------------------------

    fun searches(): List<SavedSearch> = load().searches

    fun addSearch(name: String, query: String) {
        val data = load()
        val s = SavedSearch(
            id = System.currentTimeMillis().toString(),
            name = name.ifBlank { query },
            query = query,
        )
        save(data.copy(searches = data.searches + s))
    }

    fun removeSearch(id: String) {
        val data = load()
        save(data.copy(searches = data.searches.filterNot { it.id == id }))
    }

    fun toggleSearchNotify(id: String) {
        val data = load()
        save(data.copy(searches = data.searches.map {
            if (it.id == id) it.copy(notify = !it.notify) else it
        }))
    }
}

/** True when a tender matches a saved search - same AND-of-words rule as the
 *  main search bar, so a saved search behaves exactly like typing it. */
fun Tender.matches(query: String): Boolean {
    val words = query.trim().lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }
    if (words.isEmpty()) return false
    return words.all { haystack.contains(it) }
}
