package org.mesos.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.contacts.ContactPhone
import org.mesos.contacts.ContactSummary
import org.mesos.contacts.ContactsRepository
import org.mesos.core.MesOSApps
import org.mesos.core.log.MesOSLog
import org.mesos.core.text.SearchText
import org.mesos.core.ui.Avatar
import org.mesos.core.ui.BackButton
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.rememberContentThumbnail
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Messages: SMS conversations. With the SMS role it also receives and stores messages. */
class MessagesActivity : ComponentActivity() {

    private val requests = MutableStateFlow<MessagesScreen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Messages opened")
        if (savedInstanceState == null) requests.value = screenFor(intent)
        setContent {
            MesOSUserTheme {
                val request by requests.collectAsState()
                PermissionGate(
                    permissions = listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS),
                    rationale = stringResource(R.string.messages_rationale),
                    isGranted = { it.hasPermission(Manifest.permission.READ_SMS) },
                    icon = MesOSGlyphs.Message,
                ) {
                    MessagesApp(request = request, onRequestHandled = { requests.value = null }, onClose = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screenFor(intent)?.let { requests.value = it }
    }

    private fun screenFor(intent: Intent?): MessagesScreen? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data = intent.data ?: return null
                if (data.scheme !in setOf("sms", "smsto", "mms", "mmsto")) return null
                val address = data.schemeSpecificPart.orEmpty().substringBefore('?').substringBefore(',').trim()
                val body = intent.getStringExtra("sms_body")
                    ?: data.schemeSpecificPart.orEmpty().substringAfter("?body=", "").takeIf { it.isNotEmpty() }?.let(Uri::decode)
                if (address.isBlank()) MessagesScreen.New(body.orEmpty()) else MessagesScreen.Chat(listOf(address), body.orEmpty())
            }
            Intent.ACTION_SEND -> MessagesScreen.New(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            else -> null
        }
    }

    companion object {
        fun conversationIntent(context: Context, address: String): Intent =
            Intent(context, MessagesActivity::class.java)
                .setAction(Intent.ACTION_SENDTO)
                .setData(Uri.fromParts("smsto", address, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal sealed interface MessagesScreen {
    data object Threads : MessagesScreen
    data class Chat(val addresses: List<String>, val draft: String = "") : MessagesScreen
    data class New(val draft: String = "") : MessagesScreen
}

@Composable
private fun MessagesApp(request: MessagesScreen?, onRequestHandled: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<MessagesScreen>(MessagesScreen.Threads) }
    LaunchedEffect(request) {
        if (request != null) {
            stack.add(request)
            onRequestHandled()
        }
    }
    val changes by remember { SmsRepository.changes(context) }.collectAsState(0)
    val names = remember { mutableStateMapOf<String, ContactSummary?>() }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    BackHandler(enabled = stack.size > 1) { back() }

    when (val screen = stack.last()) {
        MessagesScreen.Threads -> ThreadList(
            changes = changes,
            names = names,
            onOpen = { stack.add(MessagesScreen.Chat(it.addresses)) },
            onNew = { stack.add(MessagesScreen.New()) },
            onBack = onClose,
        )
        is MessagesScreen.Chat -> ChatScreen(
            addresses = screen.addresses,
            initialDraft = screen.draft,
            changes = changes,
            names = names,
            onBack = back,
        )
        is MessagesScreen.New -> NewMessage(
            onPick = { address ->
                stack.removeAt(stack.lastIndex)
                stack.add(MessagesScreen.Chat(listOf(address), screen.draft))
            },
            onBack = back,
        )
    }
}

/** Looks up contact names for [addresses] that are not known yet. */
@Composable
private fun ResolveNames(addresses: List<String>, names: MutableMap<String, ContactSummary?>) {
    val context = LocalContext.current
    LaunchedEffect(addresses) {
        val missing = addresses.filter { it !in names }
        if (missing.isEmpty() || !context.hasPermission(Manifest.permission.READ_CONTACTS)) return@LaunchedEffect
        val found = withContext(Dispatchers.IO) { missing.associateWith { ContactsRepository.lookupByNumber(context, it) } }
        names.putAll(found)
    }
}

private fun displayName(address: String, names: Map<String, ContactSummary?>): String =
    names[address]?.name?.takeIf { it.isNotBlank() } ?: address

@Composable
private fun ThreadList(
    changes: Int,
    names: MutableMap<String, ContactSummary?>,
    onOpen: (Conversation) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<Conversation>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(SmsRepository.isDefaultSmsApp(context)) }
    var version by remember { mutableStateOf(0) }
    OnResume { isDefault = SmsRepository.isDefaultSmsApp(context) }
    LaunchedEffect(changes, version) { conversations = withContext(Dispatchers.IO) { SmsRepository.conversations(context) } }
    val all = conversations.orEmpty()
    ResolveNames(all.flatMap { it.addresses }.distinct(), names)
    val shown = if (query.isBlank()) {
        all
    } else {
        all.filter { c -> c.addresses.any { SearchText.matches(query, displayName(it, names)) || it.contains(query) } || SearchText.matches(query, c.snippet) }
    }

    MesOSListScreen(
        title = stringResource(R.string.messages_app_name),
        onBack = onBack,
        itemSpacing = 10.dp,
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(MesOSGlyphs.Edit, contentDescription = stringResource(R.string.messages_new))
            }
        },
    ) {
        if (!isDefault) item(key = "default") { DefaultSmsBanner(onChanged = { isDefault = SmsRepository.isDefaultSmsApp(context) }) }
        item(key = "search") {
            MesOSSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.messages_search), modifier = Modifier.fillMaxWidth())
        }
        if (conversations != null && shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = MesOSGlyphs.Message,
                    title = stringResource(if (query.isBlank()) R.string.messages_empty else R.string.messages_no_results),
                    message = if (query.isBlank()) stringResource(R.string.messages_empty_text) else null,
                )
            }
        }
        if (shown.isNotEmpty()) {
            item(key = "list") {
                ListGroup {
                    shown.forEachIndexed { index, conversation ->
                        if (index > 0) GroupDivider(inset = 76.dp)
                        ThreadRow(
                            conversation = conversation,
                            names = names,
                            canDelete = isDefault,
                            onOpen = { onOpen(conversation) },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { SmsRepository.deleteConversation(context, conversation.threadId) }
                                    version++
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    conversation: Conversation,
    names: Map<String, ContactSummary?>,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val title = conversation.addresses.joinToString(", ") { displayName(it, names) }.ifBlank { stringResource(R.string.messages_unknown) }
    val first = conversation.addresses.firstOrNull().orEmpty()
    ListRow(
        title = title,
        subtitle = conversation.snippet.ifBlank { stringResource(R.string.messages_attachment) },
        titleColor = Color.Unspecified,
        leading = { Avatar(title, photo = names[first]?.photo, size = 46.dp) },
        onClick = onOpen,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    timeLabel(context, conversation.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (conversation.unread) MaterialTheme.colorScheme.primary else MesOSTheme.colors.dim,
                    fontWeight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.unread) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    if (canDelete) {
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(MesOSGlyphs.More, contentDescription = stringResource(org.mesos.core.R.string.mesos_more), tint = MesOSTheme.colors.dim, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(org.mesos.core.R.string.mesos_delete), color = MesOSTheme.colors.danger) },
                                    onClick = {
                                        menu = false
                                        confirm = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.messages_delete_title, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text(stringResource(org.mesos.core.R.string.mesos_delete), color = MesOSTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
            },
        )
    }
}

private fun timeLabel(context: Context, millis: Long): String =
    if (DateUtils.isToday(millis)) {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }

/** Offers to make MesOS the SMS app, needed to receive messages in MesOS. */
@Composable
private fun DefaultSmsBanner(onChanged: () -> Unit) {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    MesOSCard {
        Text(stringResource(R.string.messages_make_default), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.messages_make_default_text), style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim)
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            if (intent != null) {
                try {
                    request.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.messages_not_available, Toast.LENGTH_SHORT).show()
                }
            }
        }) { Text(stringResource(R.string.messages_make_default_action)) }
    }
}

@Composable
private fun ChatScreen(
    addresses: List<String>,
    initialDraft: String,
    changes: Int,
    names: MutableMap<String, ContactSummary?>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var draft by rememberSaveable { mutableStateOf(initialDraft) }
    var threadId by remember { mutableStateOf<Long?>(null) }
    val address = addresses.firstOrNull().orEmpty()
    ResolveNames(addresses, names)
    val title = addresses.joinToString(", ") { displayName(it, names) }

    LaunchedEffect(addresses, changes) {
        val loaded = withContext(Dispatchers.IO) {
            val thread = SmsRepository.conversations(context)
                .firstOrNull { c -> c.addresses.size == addresses.size && addresses.all { a -> c.addresses.any { ContactsRepository.same(it, a) } } }
                ?.threadId
            thread to (thread?.let { SmsRepository.messages(context, it) } ?: emptyList())
        }
        threadId = loaded.first
        messages = loaded.second
        loaded.first?.let { id ->
            withContext(Dispatchers.IO) { SmsRepository.markRead(context, id) }
            MessageNotifications.cancel(context, id)
        }
    }

    val sendPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.messages_send_permission, Toast.LENGTH_LONG).show()
    }
    val send: (String) -> Unit = { text ->
        if (!context.hasPermission(Manifest.permission.SEND_SMS)) {
            sendPermission.launch(Manifest.permission.SEND_SMS)
        } else {
            draft = ""
            scope.launch {
                val ok = withContext(Dispatchers.IO) { SmsSender.send(context, address, text) }
                if (!ok) {
                    draft = text
                    Toast.makeText(context, R.string.messages_send_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Avatar(title, photo = names[address]?.photo, size = 38.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (names[address] != null) Text(address, style = MaterialTheme.typography.labelMedium, color = MesOSTheme.colors.dim)
            }
            if (addresses.size == 1) {
                IconButton(onClick = {
                    if (!context.startActivitySafely(MesOSApps.dial(context, address))) {
                        context.startActivitySafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Icon(MesOSGlyphs.Phone, contentDescription = stringResource(R.string.messages_call)) }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val ordered = messages.asReversed()
            itemsIndexed(ordered, key = { _, message -> message.key }) { index, message ->
                val older = ordered.getOrNull(index + 1)
                Column {
                    if (older == null || !sameDay(older.date, message.date)) {
                        Text(
                            DateUtils.formatDateTime(context, message.date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY),
                            style = MaterialTheme.typography.labelMedium,
                            color = MesOSTheme.colors.dim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                        )
                    }
                    Bubble(message, onRetry = { send(message.body) })
                }
            }
            if (messages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.messages_chat_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MesOSTheme.colors.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            }
        }
        if (addresses.size > 1) {
            Text(
                stringResource(R.string.messages_group_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MesOSTheme.colors.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        } else {
            Composer(draft = draft, onDraft = { draft = it }, onSend = { if (draft.isNotBlank()) send(draft) })
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val day = 24 * 60 * 60 * 1000L
    val offset = java.util.TimeZone.getDefault().getOffset(a).toLong()
    return (a + offset) / day == (b + offset) / day
}

@Composable
private fun Bubble(message: Message, onRetry: () -> Unit) {
    val context = LocalContext.current
    val outgoing = message.outgoing
    val failed = message.box == MessageBox.FAILED
    val background = if (outgoing) MaterialTheme.colorScheme.primary else MesOSTheme.colors.card
    val foreground = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (outgoing) 20.dp else 6.dp,
                        bottomEnd = if (outgoing) 6.dp else 20.dp,
                    ),
                )
                .background(if (failed) MesOSTheme.colors.danger.copy(alpha = 0.18f) else background)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            message.images.forEach { uri -> MmsImage(uri) }
            val text = when {
                message.pendingMms -> stringResource(R.string.messages_mms_not_downloaded)
                message.body.isNotEmpty() -> message.body
                message.images.isEmpty() -> stringResource(R.string.messages_attachment)
                else -> null
            }
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = if (failed) MaterialTheme.colorScheme.onSurface else foreground)
            }
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    failed -> stringResource(R.string.messages_failed_tap_retry)
                    message.box == MessageBox.OUTBOX -> stringResource(R.string.messages_sending)
                    else -> DateUtils.formatDateTime(context, message.date, DateUtils.FORMAT_SHOW_TIME)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MesOSTheme.colors.danger else MesOSTheme.colors.dim,
                modifier = if (failed) Modifier.clickable(onClick = onRetry) else Modifier,
            )
        }
    }
}

@Composable
private fun MmsImage(uri: Uri) {
    val bitmap = rememberContentThumbnail(uri, 240.dp)
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MesOSTheme.colors.card)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) Text(stringResource(R.string.messages_type), color = MesOSTheme.colors.dim, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(8.dp))
        val enabled = draft.isNotBlank()
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MesOSTheme.colors.separator)
                .clickable(enabled = enabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MesOSGlyphs.Send,
                contentDescription = stringResource(R.string.messages_send),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MesOSTheme.colors.dim,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun NewMessage(onPick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var phones by remember { mutableStateOf<List<Pair<ContactSummary, ContactPhone>>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (context.hasPermission(Manifest.permission.READ_CONTACTS)) {
            phones = withContext(Dispatchers.IO) { ContactsRepository.phones(context) }
        }
    }
    val digits = query.filter { it.isDigit() || it == '+' }
    val matches = if (query.isBlank()) {
        phones
    } else {
        phones.filter { (contact, phone) ->
            SearchText.matches(query, contact.name) || (digits.length >= 2 && phone.number.filter { it.isDigit() || it == '+' }.contains(digits))
        }
    }

    MesOSListScreen(title = stringResource(R.string.messages_new), onBack = onBack) {
        item(key = "to") {
            MesOSSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.messages_to),
                modifier = Modifier.fillMaxWidth(),
                onSearch = { if (digits.length >= 3) onPick(digits) },
            )
        }
        if (digits.length >= 3 && digits.length == query.filterNot { it == ' ' || it == '-' }.length) {
            item(key = "number") {
                ListGroup {
                    ListRow(
                        title = stringResource(R.string.messages_send_to, digits),
                        icon = MesOSGlyphs.Send,
                        onClick = { onPick(digits) },
                    )
                }
            }
        }
        if (matches.isNotEmpty()) {
            item(key = "contacts") {
                ListGroup {
                    matches.take(60).forEachIndexed { index, (contact, phone) ->
                        if (index > 0) GroupDivider(inset = 72.dp)
                        ListRow(
                            title = contact.name,
                            subtitle = phone.number,
                            leading = { Avatar(contact.name, photo = contact.photo, size = 40.dp) },
                            onClick = { onPick(phone.number) },
                            showChevron = false,
                        )
                    }
                }
            }
        }
    }
}
