package org.mesos.notes

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mesos.core.R as CoreR
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.MesOSTopBar
import org.mesos.core.ui.OnPause
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Notes. */
class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requested = intent.getLongExtra(NotesFeed.EXTRA_NOTE_ID, -1L)
        val initial = when {
            intent.getBooleanExtra(NotesFeed.EXTRA_NEW_NOTE, false) -> NEW_NOTE
            requested > 0 -> requested
            else -> null
        }
        setContent {
            MesOSUserTheme { NotesApp(initial) }
        }
    }
}

/** Editor target: null shows the list, [NEW_NOTE] a new note, otherwise a note id. */
private const val NEW_NOTE = 0L

@Composable
private fun NotesApp(initial: Long?) {
    var editing by rememberSaveable { mutableStateOf(initial) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            when (val id = editing) {
                null -> NoteList(onOpen = { editing = it }, onCreate = { editing = NEW_NOTE })
                else -> NoteEditor(noteId = id, onClose = { editing = null })
            }
        }
    }
}

@Composable
private fun NoteList(onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { NotesStore.get(context) }
    val changes by store.changes.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    LaunchedEffect(query, changes) {
        notes = store.list(query)
    }
    val list = notes

    MesOSListScreen(
        title = stringResource(R.string.notes_app_name),
        itemSpacing = 10.dp,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(MesOSGlyphs.Plus, contentDescription = stringResource(R.string.notes_new))
            }
        },
    ) {
        item(key = "search") {
            MesOSSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.notes_search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = if (query.isBlank()) MesOSGlyphs.Edit else MesOSGlyphs.Search,
                    title = stringResource(if (query.isBlank()) R.string.notes_empty else R.string.notes_no_results),
                )
            }
        }
        items(list.orEmpty(), key = { it.id }) { note -> NoteCard(note) { onOpen(note.id) } }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val title = note.title.ifBlank { stringResource(R.string.notes_untitled) }
    MesOSCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(width = 4.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFBBF24)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.body.isNotBlank()) {
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MesOSTheme.colors.dim,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = DateUtils.getRelativeTimeSpanString(note.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MesOSTheme.colors.dim,
                )
            }
        }
    }
}

@Composable
private fun NoteEditor(noteId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { NotesStore.get(context) }
    val draft = remember(noteId) { Draft(noteId.takeIf { it != NEW_NOTE }) }

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf("" to "") }
    var loaded by rememberSaveable { mutableStateOf(noteId == NEW_NOTE) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (!loaded) {
            store.get(noteId)?.let { note ->
                title = note.title
                body = note.body
                saved = note.title to note.body
            }
            loaded = true
        }
    }

    // Saves only when something changed; an emptied note is deleted by the store.
    fun persist() {
        if (!loaded || (title to body) == saved) return
        saved = title to body
        store.save(draft, title, body)
    }

    fun close() {
        persist()
        onClose()
    }

    BackHandler(onBack = ::close)
    OnPause(::persist)

    Column(Modifier.fillMaxSize()) {
        MesOSTopBar(
            title = stringResource(if (noteId == NEW_NOTE) R.string.notes_new else R.string.notes_edit),
            onBack = ::close,
            actions = {
                if (noteId != NEW_NOTE) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(CoreR.string.mesos_delete))
                    }
                }
            },
        )
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
        TextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.notes_title_hint)) },
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = body,
            onValueChange = { body = it },
            placeholder = { Text(stringResource(R.string.notes_body_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saved = title to body
                    store.delete(draft)
                    onClose()
                }) { Text(stringResource(CoreR.string.mesos_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreR.string.mesos_cancel)) }
            },
        )
    }
}
