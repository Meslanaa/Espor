package org.mesos.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.core.R as CoreR
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.IconBadge
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.MesOSTopBar
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import java.io.File

/** A listed file with the details the row shows (read off the main thread). */
private data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int,
)

/** A file waiting to be pasted: copied, or moved when [move] is true. */
private data class Clip(val file: File, val move: Boolean)

@Composable
internal fun FilesApp(topDirectory: File?, onExit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            StorageAccessGate {
                FilesContent(topDirectory, onExit)
            }
        }
    }
}

@Composable
private fun StorageAccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        PermissionGate(
            permissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            rationale = stringResource(R.string.files_access_rationale),
            content = content,
        )
        return
    }
    var granted by remember { mutableStateOf(Storage.hasAccess(context)) }
    OnResume { granted = Storage.hasAccess(context) }
    if (granted) {
        content()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.files_access_rationale), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = { Storage.openAllFilesAccessSettings(context) }) {
            Text(stringResource(R.string.files_access_action))
        }
    }
}

@Composable
private fun FilesContent(topDirectory: File?, onExit: () -> Unit) {
    val root = remember { Storage.root }
    var path by rememberSaveable { mutableStateOf(topDirectory?.path) }
    var clip by remember { mutableStateOf<Clip?>(null) }

    BackHandler {
        val current = path
        path = when {
            current == null || current == topDirectory?.path -> {
                onExit()
                current
            }
            File(current) == root -> null
            else -> File(current).parent
        }
    }

    val current = path
    if (current == null) {
        FilesHome(root = root, open = { path = it.path })
    } else {
        FolderBrowser(
            dir = File(current),
            root = root,
            topDirectory = topDirectory,
            clip = clip,
            onClipChange = { clip = it },
            open = { path = it.path },
            onBack = {
                path = when {
                    current == topDirectory?.path -> { onExit(); current }
                    File(current) == root -> null
                    else -> File(current).parent
                }
            },
        )
    }
}

@Composable
private fun FilesHome(root: File, open: (File) -> Unit) {
    val shortcuts = listOf(
        Triple(Environment.DIRECTORY_DOWNLOADS, R.string.files_downloads, MesOSGlyphs.Download to MesOSPalette.Green),
        Triple(Environment.DIRECTORY_DCIM, R.string.files_camera, MesOSGlyphs.Camera to MesOSPalette.Orange),
        Triple(Environment.DIRECTORY_PICTURES, R.string.files_pictures, MesOSGlyphs.Image to MesOSPalette.Pink),
        Triple(Environment.DIRECTORY_DOCUMENTS, R.string.files_documents, MesOSGlyphs.Folder to MesOSPalette.Blue),
        Triple(Environment.DIRECTORY_MUSIC, R.string.files_music, MesOSGlyphs.Music to MesOSPalette.Rose),
        Triple(Environment.DIRECTORY_MOVIES, R.string.files_movies, MesOSGlyphs.Video to MesOSPalette.Violet),
    )
    var usage by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(root) {
        usage = withContext(Dispatchers.IO) {
            runCatching { StatFs(root.path).let { it.totalBytes to it.availableBytes } }.getOrNull()
        }
    }
    val context = LocalContext.current

    MesOSListScreen(title = stringResource(R.string.files_app_name)) {
        item(key = "storage") {
            MesOSCard(onClick = { open(root) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(MesOSGlyphs.Storage, MesOSPalette.Indigo, size = 44.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.files_internal_storage), style = MaterialTheme.typography.titleMedium)
                        usage?.let { (total, free) ->
                            val used = total - free
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(MesOSTheme.colors.separator),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.files_storage_used,
                                    Formatter.formatShortFileSize(context, used),
                                    Formatter.formatShortFileSize(context, total),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MesOSTheme.colors.dim,
                            )
                        }
                    }
                }
            }
        }
        item(key = "folders") {
            ListGroup {
                shortcuts.forEachIndexed { index, (type, label, look) ->
                    if (index > 0) GroupDivider()
                    ListRow(
                        title = stringResource(label),
                        icon = look.first,
                        iconColor = look.second,
                        onClick = { open(Storage.publicDirectory(type)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderIcon() {
    IconBadge(MesOSGlyphs.Folder, MesOSPalette.Blue, size = 40.dp)
}

@Composable
private fun FileTypeIcon(file: File) {
    val ext = file.extension.uppercase().take(4).ifEmpty { "•" }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(ext, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun FolderBrowser(
    dir: File,
    root: File,
    topDirectory: File?,
    clip: Clip?,
    onClipChange: (Clip?) -> Unit,
    open: (File) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var items by remember(dir) { mutableStateOf<List<FileItem>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(dir, refresh, showHidden) {
        items = withContext(Dispatchers.IO) { list(dir, showHidden) }
    }
    OnResume { refresh++ }

    /** Runs a file operation off the main thread, rescans affected media, then refreshes. */
    fun runFileOp(operation: () -> List<File>) {
        scope.launch {
            try {
                val touched = withContext(Dispatchers.IO) { operation() }
                Storage.rescan(context, touched)
            } catch (e: FileOpException) {
                Toast.makeText(context, errorMessage(context, e.error), Toast.LENGTH_SHORT).show()
            }
            refresh++
        }
    }

    val title = when (dir) {
        root -> stringResource(R.string.files_internal_storage)
        topDirectory -> stringResource(R.string.files_downloads)
        else -> dir.name
    }

    Column(Modifier.fillMaxSize()) {
        MesOSTopBar(
            title = title,
            onBack = onBack,
            actions = {
                IconButton(onClick = { newFolderDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.files_new_folder))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.files_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.files_show_hidden)) },
                            leadingIcon = { Checkbox(checked = showHidden, onCheckedChange = null) },
                            onClick = {
                                showHidden = !showHidden
                                menuOpen = false
                            },
                        )
                    }
                }
            },
        )
        Text(
            text = breadcrumb(dir, root, stringResource(R.string.files_internal_storage)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val list = items
        Box(Modifier.weight(1f)) {
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    icon = if (!dir.exists() || dir.canRead()) MesOSGlyphs.Folder else MesOSGlyphs.Lock,
                    title = stringResource(if (!dir.exists() || dir.canRead()) R.string.files_empty_folder else R.string.files_cannot_read),
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(list, key = { it.file.path }) { item ->
                        FileRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    open(item.file)
                                } else if (!context.startActivitySafely(Storage.viewIntent(context, item.file))) {
                                    Toast.makeText(context, CoreR.string.mesos_open_failed, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAction = { action ->
                                when (action) {
                                    FileAction.OPEN_WITH -> {
                                        val chooser = Intent.createChooser(
                                            Storage.viewIntent(context, item.file).setComponent(null).setPackage(null),
                                            context.getString(R.string.files_open_with),
                                        )
                                        context.startActivitySafely(chooser)
                                    }
                                    FileAction.SHARE -> context.startActivitySafely(
                                        Storage.shareIntent(context, item.file, context.getString(CoreR.string.mesos_share)),
                                    )
                                    FileAction.RENAME -> renameTarget = item.file
                                    FileAction.COPY -> onClipChange(Clip(item.file, move = false))
                                    FileAction.MOVE -> onClipChange(Clip(item.file, move = true))
                                    FileAction.DELETE -> deleteTarget = item.file
                                    FileAction.DETAILS -> detailsTarget = item
                                }
                            },
                        )
                    }
                }
            }
        }

        if (clip != null) {
            PasteBar(
                clip = clip,
                onPaste = {
                    onClipChange(null)
                    runFileOp {
                        if (clip.move) {
                            val before = FileOps.filesBelow(clip.file)
                            before + FileOps.filesBelow(FileOps.move(clip.file, dir))
                        } else {
                            FileOps.filesBelow(FileOps.copy(clip.file, dir))
                        }
                    }
                },
                onCancel = { onClipChange(null) },
            )
        }
    }

    if (newFolderDialog) {
        NameDialog(
            title = stringResource(R.string.files_new_folder),
            initial = "",
            onDismiss = { newFolderDialog = false },
            onConfirm = { name ->
                newFolderDialog = false
                runFileOp {
                    FileOps.createFolder(dir, name)
                    emptyList()
                }
            },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = stringResource(R.string.files_rename),
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                runFileOp {
                    val before = FileOps.filesBelow(target)
                    before + FileOps.filesBelow(FileOps.rename(target, name))
                }
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.files_delete_title, target.name)) },
            text = {
                Text(stringResource(if (target.isDirectory) R.string.files_delete_folder_message else R.string.files_delete_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    runFileOp {
                        val before = FileOps.filesBelow(target)
                        FileOps.delete(target)
                        before
                    }
                }) { Text(stringResource(CoreR.string.mesos_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(CoreR.string.mesos_cancel)) }
            },
        )
    }
    detailsTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text(item.file.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.files_details_location, item.file.parent.orEmpty()))
                    if (item.isDirectory) {
                        Text(stringResource(R.string.files_details_items, item.childCount))
                    } else {
                        Text(stringResource(R.string.files_details_size, Formatter.formatFileSize(context, item.size)))
                        Text(stringResource(R.string.files_details_type, Storage.mimeType(item.file)))
                    }
                    Text(stringResource(R.string.files_details_modified, formatDate(context, item.modified)))
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) { Text(stringResource(CoreR.string.mesos_ok)) }
            },
        )
    }
}

private enum class FileAction { OPEN_WITH, SHARE, RENAME, COPY, MOVE, DELETE, DETAILS }

@Composable
private fun FileRow(item: FileItem, onClick: () -> Unit, onAction: (FileAction) -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isDirectory) FolderIcon() else FileTypeIcon(item.file)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = if (item.isDirectory) {
                context.resources.getQuantityString(R.plurals.files_item_count, item.childCount, item.childCount)
            } else {
                Formatter.formatShortFileSize(context, item.size)
            }
            Text(
                "$detail · ${formatDate(context, item.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.files_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val actions = buildList {
                    if (!item.isDirectory) {
                        add(FileAction.OPEN_WITH to R.string.files_open_with)
                        add(FileAction.SHARE to CoreR.string.mesos_share)
                    }
                    add(FileAction.RENAME to R.string.files_rename)
                    add(FileAction.COPY to R.string.files_copy)
                    add(FileAction.MOVE to R.string.files_move)
                    add(FileAction.DELETE to CoreR.string.mesos_delete)
                    add(FileAction.DETAILS to R.string.files_details)
                }
                actions.forEach { (action, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            menuOpen = false
                            onAction(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PasteBar(clip: Clip, onPaste: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (clip.move) R.string.files_moving else R.string.files_copying, clip.file.name),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(CoreR.string.mesos_cancel)) }
            Button(onClick = onPaste) { Text(stringResource(R.string.files_paste_here)) }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val error = FileOps.validateName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = error == FileError.NAME_INVALID,
                supportingText = if (error == FileError.NAME_INVALID) {
                    { Text(stringResource(R.string.files_error_name_invalid)) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = error == null) { Text(stringResource(CoreR.string.mesos_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.mesos_cancel)) }
        },
    )
}

private fun list(dir: File, showHidden: Boolean): List<FileItem> {
    val files = dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") } ?: return emptyList()
    return FileOps.sort(files).map { file ->
        val isDirectory = file.isDirectory
        FileItem(
            file = file,
            isDirectory = isDirectory,
            size = if (isDirectory) 0 else file.length(),
            modified = file.lastModified(),
            childCount = if (isDirectory) file.list()?.count { showHidden || !it.startsWith(".") } ?: 0 else 0,
        )
    }
}

private fun breadcrumb(dir: File, root: File, rootLabel: String): String {
    val relative = dir.path.removePrefix(root.path).trim('/')
    return if (relative.isEmpty()) rootLabel else "$rootLabel / " + relative.replace("/", " / ")
}

private fun formatDate(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

private fun errorMessage(context: Context, error: FileError): String = context.getString(
    when (error) {
        FileError.NAME_EMPTY, FileError.NAME_INVALID -> R.string.files_error_name_invalid
        FileError.EXISTS -> R.string.files_error_exists
        FileError.INTO_ITSELF -> R.string.files_error_into_itself
        FileError.FAILED -> R.string.files_error_failed
    },
)
