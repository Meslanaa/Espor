package org.mesos.notes

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Identity of the note being edited, shared with [NotesStore] so repeated saves update one row. */
internal class Draft(initialId: Long?) {
    @Volatile
    var id: Long? = initialId
}

/**
 * Notes storage with writes serialized in the store's own scope: a save started when
 * the editor closes still completes, and a new note is inserted exactly once.
 */
internal class NotesStore private constructor(context: Context) {

    private val database = NotesDatabase.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _changes = MutableStateFlow(0L)

    /** Increments after every write; the note list reloads when it changes. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    suspend fun list(query: String): List<Note> = withContext(Dispatchers.IO) { database.list(query) }

    suspend fun get(id: Long): Note? = withContext(Dispatchers.IO) { database.get(id) }

    /** Saves [draft]; a note emptied of title and body is deleted instead. */
    fun save(draft: Draft, title: String, body: String) {
        scope.launch {
            mutex.withLock {
                val existing = draft.id
                if (title.isBlank() && body.isBlank()) {
                    if (existing != null) database.delete(existing)
                    draft.id = null
                } else {
                    draft.id = database.save(existing, title, body)
                }
                _changes.update { it + 1 }
            }
        }
    }

    fun delete(draft: Draft) {
        scope.launch {
            mutex.withLock {
                draft.id?.let(database::delete)
                draft.id = null
                _changes.update { it + 1 }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: NotesStore? = null

        fun get(context: Context): NotesStore =
            instance ?: synchronized(this) {
                instance ?: NotesStore(context.applicationContext).also { instance = it }
            }
    }
}
