package org.mesos.notes

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mesos.core.R as CoreR
import org.mesos.core.ui.MesOSTopBar
import org.mesos.core.ui.OnPause
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Notes. */
class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MesOSUserTheme { NotesApp() }
        }
    }
}

/** Editor target: null shows the list, [NEW_NOTE] a new note, otherwise a note id. */
private const val NEW_NOTE = 0L

@Composable
private fun NotesApp() {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MesOSTopBar(title = stringResource(R.string.notes_app_name))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.notes_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            val list = notes
            if (list != null && list.isEmpty()) {
                Text(
                    text = stringResource(if (query.isBlank()) R.string.notes_empty else R.string.notes_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list.orEmpty(), key = { it.id }) { note -> NoteCard(note) { onOpen(note.id) } }
            }
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.notes_new))
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = DateUtils.formatDateTime(
                    context,
                    note.updatedAt,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
