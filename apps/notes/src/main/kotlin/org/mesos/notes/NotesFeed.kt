package org.mesos.notes

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import org.mesos.core.MesOSApps

/** What other MesOS components (Home widgets, search) may read from MesOS Notes. */
object NotesFeed {
    const val EXTRA_NOTE_ID = "org.mesos.notes.extra.NOTE_ID"
    const val EXTRA_NEW_NOTE = "org.mesos.notes.extra.NEW_NOTE"

    /** Changes whenever a note is saved or deleted. */
    fun changes(context: Context): StateFlow<Long> = NotesStore.get(context).changes

    /** Most recently edited notes first. */
    suspend fun recent(context: Context, limit: Int): List<Note> = NotesStore.get(context).list("").take(limit)

    suspend fun search(context: Context, query: String, limit: Int): List<Note> =
        NotesStore.get(context).list(query).take(limit)

    /** Opens note [id] in MesOS Notes. */
    fun openIntent(context: Context, id: Long): Intent =
        MesOSApps.launchIntent(context, MesOSApps.NOTES)
            .putExtra(EXTRA_NOTE_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** Opens MesOS Notes with a new, empty note. */
    fun newNoteIntent(context: Context): Intent =
        MesOSApps.launchIntent(context, MesOSApps.NOTES)
            .putExtra(EXTRA_NEW_NOTE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
