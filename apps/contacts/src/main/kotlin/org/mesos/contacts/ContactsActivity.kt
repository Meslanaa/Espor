package org.mesos.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.core.MesOSApps
import org.mesos.core.log.MesOSLog
import org.mesos.core.text.SearchText
import org.mesos.core.ui.Avatar
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import java.text.Normalizer
import java.util.Locale

/** MesOS Contacts: browse, search, call, message, add and edit contacts. */
class ContactsActivity : ComponentActivity() {

    /** Screen asked for by the launching intent (view a contact, add one). */
    private val requests = MutableStateFlow<ContactsScreen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Contacts opened")
        if (savedInstanceState == null) requests.value = screenFor(intent)
        setContent {
            MesOSUserTheme {
                val request by requests.collectAsState()
                PermissionGate(
                    permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                    rationale = stringResource(R.string.contacts_rationale),
                    icon = MesOSGlyphs.Person,
                ) {
                    ContactsApp(request = request, onRequestHandled = { requests.value = null }, onClose = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requests.value = screenFor(intent)
    }

    private fun screenFor(intent: Intent): ContactsScreen? = when (intent.action) {
        Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT -> ContactsScreen.Editor(null, draftFrom(intent))
        Intent.ACTION_VIEW -> intent.data?.let { uri -> ContactsRepository.idOf(this, uri) }?.let { ContactsScreen.Detail(it) }
        else -> null
    }

    private fun draftFrom(intent: Intent): ContactDraft {
        val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME).orEmpty().trim()
        val given = name.substringBeforeLast(' ', name)
        val family = if (' ' in name) name.substringAfterLast(' ') else ""
        val phones = listOfNotNull(
            intent.getStringExtra(ContactsContract.Intents.Insert.PHONE),
            intent.getStringExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE),
        ).filter { it.isNotBlank() }.map { ContactPhone(it, PhoneKind.MOBILE) }
        return ContactDraft(
            givenName = given,
            familyName = family,
            phones = phones,
            emails = listOfNotNull(intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL)).filter { it.isNotBlank() },
            company = intent.getStringExtra(ContactsContract.Intents.Insert.COMPANY).orEmpty(),
        )
    }
}

internal sealed interface ContactsScreen {
    data object Index : ContactsScreen
    data class Detail(val id: Long) : ContactsScreen
    data class Editor(val id: Long?, val prefill: ContactDraft? = null) : ContactsScreen
}

@Composable
private fun ContactsApp(request: ContactsScreen?, onRequestHandled: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<ContactsScreen>(ContactsScreen.Index) }
    LaunchedEffect(request) {
        if (request != null) {
            stack.add(request)
            onRequestHandled()
        }
    }
    val changes by remember { ContactsRepository.changes(context) }.collectAsState(Unit)
    var contacts by remember { mutableStateOf<List<ContactSummary>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(changes, version) { contacts = withContext(Dispatchers.IO) { ContactsRepository.all(context) } }

    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    BackHandler(enabled = stack.size > 1) { back() }

    AnimatedContent(
        targetState = stack.toList(),
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            (slideInHorizontally(tween(300)) { if (forward) it / 3 else -it / 5 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(300)) { if (forward) -it / 5 else it / 3 } + fadeOut(tween(160)))
        },
        contentKey = { it.size.toString() + it.last().toString() },
        label = "contactsScreen",
    ) { screens ->
        when (val screen = screens.last()) {
            ContactsScreen.Index -> ContactList(
                contacts = contacts,
                onOpen = { stack.add(ContactsScreen.Detail(it.id)) },
                onAdd = { stack.add(ContactsScreen.Editor(null)) },
                onBack = onClose,
            )
            is ContactsScreen.Detail -> ContactDetailScreen(
                id = screen.id,
                version = version,
                onEdit = { stack.add(ContactsScreen.Editor(screen.id)) },
                onDeleted = {
                    version++
                    back()
                },
                onBack = back,
            )
            is ContactsScreen.Editor -> ContactEditor(
                id = screen.id,
                prefill = screen.prefill,
                onSaved = { id ->
                    version++
                    stack.removeAt(stack.lastIndex)
                    if (screen.id == null && id != null) stack.add(ContactsScreen.Detail(id))
                },
                onCancel = back,
            )
        }
    }
}

/** Letter a contact is filed under; Turkish letters keep their own sections. */
private fun sectionOf(name: String): String {
    val first = name.trimStart().firstOrNull() ?: return "#"
    if (!first.isLetter()) return "#"
    val upper = first.toString().uppercase(Locale.forLanguageTag("tr"))
    return if (upper in "ÇĞİÖŞÜ") upper else Normalizer.normalize(upper, Normalizer.Form.NFD).take(1)
}

@Composable
private fun ContactList(
    contacts: List<ContactSummary>,
    onOpen: (ContactSummary) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) contacts else contacts.filter { SearchText.matches(query, it.name) }
    val favorites = contacts.filter { it.starred }

    MesOSListScreen(
        title = stringResource(R.string.contacts_app_name),
        onBack = onBack,
        itemSpacing = 8.dp,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(MesOSGlyphs.PersonAdd, contentDescription = stringResource(R.string.contacts_add))
            }
        },
    ) {
        item(key = "search") {
            MesOSSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.contacts_search, contacts.size),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isBlank() && favorites.isNotEmpty()) {
            item(key = "favorites") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupLabel(stringResource(R.string.contacts_favorites))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(favorites, key = { "fav-${it.id}" }) { contact ->
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onOpen(contact) }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(contact.name, photo = contact.photo, size = 58.dp)
                                Text(
                                    contact.name.substringBefore(' '),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = if (query.isBlank()) MesOSGlyphs.Person else MesOSGlyphs.Search,
                    title = stringResource(if (query.isBlank()) R.string.contacts_empty else R.string.contacts_no_results),
                    message = if (query.isBlank()) stringResource(R.string.contacts_empty_text) else null,
                )
            }
        }
        filtered.groupBy { sectionOf(it.name) }.forEach { (section, members) ->
            item(key = "section-$section") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupLabel(section)
                    ListGroup {
                        members.forEachIndexed { index, contact ->
                            if (index > 0) GroupDivider(inset = 72.dp)
                            ListRow(
                                title = contact.name,
                                leading = { Avatar(contact.name, photo = contact.photo, size = 40.dp) },
                                trailing = if (contact.starred) {
                                    { Icon(MesOSGlyphs.StarFilled, contentDescription = null, tint = MesOSTheme.colors.warning, modifier = Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                                showChevron = false,
                                onClick = { onOpen(contact) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactDetailScreen(id: Long, version: Int, onEdit: () -> Unit, onDeleted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ContactDetail?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id, version) {
        detail = withContext(Dispatchers.IO) { ContactsRepository.detail(context, id) }
        loaded = true
    }
    val contact = detail

    MesOSListScreen(
        title = contact?.name ?: "",
        onBack = onBack,
        actions = {
            if (contact != null) {
                IconButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ContactsRepository.setStarred(context, contact.id, !contact.starred) }
                        detail = withContext(Dispatchers.IO) { ContactsRepository.detail(context, id) }
                    }
                }) {
                    Icon(
                        if (contact.starred) MesOSGlyphs.StarFilled else MesOSGlyphs.Star,
                        contentDescription = stringResource(R.string.contacts_favorite),
                        tint = if (contact.starred) MesOSTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onEdit) { Icon(MesOSGlyphs.Edit, contentDescription = stringResource(org.mesos.core.R.string.mesos_edit)) }
            }
        },
    ) {
        if (contact == null) {
            if (loaded) item(key = "missing") { EmptyState(MesOSGlyphs.Person, stringResource(R.string.contacts_missing)) }
            return@MesOSListScreen
        }
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Avatar(contact.name, photo = contact.photo, size = 104.dp)
                contact.company?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MesOSTheme.colors.dim) }
            }
        }
        item(key = "actions") {
            val phone = contact.phones.firstOrNull()?.number
            val email = contact.emails.firstOrNull()?.address
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(MesOSGlyphs.Phone, stringResource(R.string.contacts_call), phone != null, Modifier.weight(1f)) {
                    phone?.let { call(context, it) }
                }
                QuickAction(MesOSGlyphs.Message, stringResource(R.string.contacts_message), phone != null, Modifier.weight(1f)) {
                    phone?.let { message(context, it) }
                }
                QuickAction(MesOSGlyphs.Mail, stringResource(R.string.contacts_email), email != null, Modifier.weight(1f)) {
                    email?.let { mail(context, it) }
                }
                QuickAction(MesOSGlyphs.Share, stringResource(org.mesos.core.R.string.mesos_share), true, Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
                        .putExtra(Intent.EXTRA_STREAM, ContactsRepository.vcardUri(contact))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivitySafely(Intent.createChooser(send, contact.name))
                }
            }
        }
        if (contact.phones.isNotEmpty()) {
            item(key = "phones") {
                ListGroup {
                    contact.phones.forEachIndexed { index, phone ->
                        if (index > 0) GroupDivider(inset = 16.dp)
                        ListRow(
                            title = phone.number,
                            subtitle = stringResource(kindLabel(phone.kind)),
                            onClick = { call(context, phone.number) },
                            trailing = {
                                IconButton(onClick = { message(context, phone.number) }) {
                                    Icon(MesOSGlyphs.Message, contentDescription = stringResource(R.string.contacts_message), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (contact.emails.isNotEmpty()) {
            item(key = "emails") {
                ListGroup {
                    contact.emails.forEachIndexed { index, email ->
                        if (index > 0) GroupDivider(inset = 16.dp)
                        ListRow(
                            title = email.address,
                            subtitle = stringResource(R.string.contacts_email),
                            onClick = { mail(context, email.address) },
                            showChevron = false,
                        )
                    }
                }
            }
        }
        item(key = "delete") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.contacts_delete),
                    titleColor = MesOSTheme.colors.danger,
                    icon = MesOSGlyphs.Trash,
                    onClick = { confirmDelete = true },
                    showChevron = false,
                )
            }
        }
    }

    if (confirmDelete && contact != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contacts_delete_title, contact.name)) },
            text = { Text(stringResource(R.string.contacts_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { ContactsRepository.delete(context, contact) }
                        if (ok) onDeleted() else Toast.makeText(context, R.string.contacts_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(org.mesos.core.R.string.mesos_delete), color = MesOSTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
            },
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MesOSTheme.colors.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.primary else MesOSTheme.colors.dim.copy(alpha = 0.5f)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun kindLabel(kind: PhoneKind): Int = when (kind) {
    PhoneKind.MOBILE -> R.string.contacts_kind_mobile
    PhoneKind.HOME -> R.string.contacts_kind_home
    PhoneKind.WORK -> R.string.contacts_kind_work
    PhoneKind.OTHER -> R.string.contacts_kind_other
}

private fun call(context: Context, number: String) {
    if (!context.startActivitySafely(MesOSApps.dial(context, number))) {
        context.startActivitySafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun message(context: Context, number: String) {
    if (!context.startActivitySafely(MesOSApps.message(context, number))) {
        context.startActivitySafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun mail(context: Context, address: String) {
    context.startActivitySafely(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(address)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
private fun ContactEditor(id: Long?, prefill: ContactDraft?, onSaved: (Long?) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var existing by remember { mutableStateOf<ContactDetail?>(null) }
    var given by rememberSaveable { mutableStateOf(prefill?.givenName.orEmpty()) }
    var family by rememberSaveable { mutableStateOf(prefill?.familyName.orEmpty()) }
    var company by rememberSaveable { mutableStateOf(prefill?.company.orEmpty()) }
    val phones = remember { mutableStateListOf<String>().apply { addAll(prefill?.phones?.map { it.number }.orEmpty().ifEmpty { listOf("") }) } }
    val emails = remember { mutableStateListOf<String>().apply { addAll(prefill?.emails.orEmpty().ifEmpty { listOf("") }) } }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { ContactsRepository.detail(context, id) } ?: return@LaunchedEffect
        existing = loaded
        given = loaded.givenName.ifBlank { loaded.name }
        family = loaded.familyName
        company = loaded.company.orEmpty()
        phones.clear()
        phones.addAll(loaded.phones.map { it.number }.ifEmpty { listOf("") })
        emails.clear()
        emails.addAll(loaded.emails.map { it.address }.ifEmpty { listOf("") })
    }
    val draft = ContactDraft(
        givenName = given,
        familyName = family,
        phones = phones.map { ContactPhone(it, PhoneKind.MOBILE) },
        emails = emails.toList(),
        company = company,
    )

    MesOSListScreen(
        title = stringResource(if (id == null) R.string.contacts_new else R.string.contacts_edit),
        onBack = onCancel,
        actions = {
            TextButton(
                enabled = !draft.isEmpty && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { ContactsRepository.save(context, keepKinds(draft, existing), existing) }
                        saving = false
                        if (saved != null || existing != null) {
                            onSaved(saved)
                        } else {
                            Toast.makeText(context, R.string.contacts_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) { Text(stringResource(org.mesos.core.R.string.mesos_save)) }
        },
    ) {
        item(key = "avatar") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Avatar("$given $family".trim(), photo = existing?.photo, size = 88.dp)
            }
        }
        item(key = "name") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(given, { given = it }, stringResource(R.string.contacts_given_name))
                Field(family, { family = it }, stringResource(R.string.contacts_family_name))
                Field(company, { company = it }, stringResource(R.string.contacts_company))
            }
        }
        item(key = "phones") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.contacts_phones))
                phones.forEachIndexed { index, value ->
                    Field(value, { phones[index] = it }, stringResource(R.string.contacts_phone), KeyboardType.Phone)
                }
                TextButton(onClick = { phones.add("") }) { Text(stringResource(R.string.contacts_add_phone)) }
            }
        }
        item(key = "emails") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.contacts_emails))
                emails.forEachIndexed { index, value ->
                    Field(value, { emails[index] = it }, stringResource(R.string.contacts_email), KeyboardType.Email)
                }
                TextButton(onClick = { emails.add("") }) { Text(stringResource(R.string.contacts_add_email)) }
            }
        }
        item(key = "note") {
            Text(
                stringResource(R.string.contacts_saved_where),
                style = MaterialTheme.typography.bodySmall,
                color = MesOSTheme.colors.dim,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** Keeps the kind (mobile, work…) of numbers that were not changed. */
private fun keepKinds(draft: ContactDraft, existing: ContactDetail?): ContactDraft {
    if (existing == null) return draft
    return draft.copy(
        phones = draft.phones.map { phone ->
            existing.phones.firstOrNull { it.number == phone.number }?.let { phone.copy(kind = it.kind) } ?: phone
        },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
