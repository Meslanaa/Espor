package org.mesos.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.rememberHaptics
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora

/** MesOS Phone: keypad, recent calls and contacts. With the phone role, also the call screen. */
class PhoneActivity : ComponentActivity() {

    private val prefill = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Phone opened")
        if (savedInstanceState == null) prefill.value = numberFrom(intent)
        setContent {
            MesOSUserTheme {
                val number by prefill.collectAsState()
                PhoneApp(prefill = number, onPrefillUsed = { prefill.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberFrom(intent)?.let { prefill.value = it }
    }

    private fun numberFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_DIAL && intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return if (data.scheme == "tel") Dialpad.sanitize(data.schemeSpecificPart.orEmpty()) else null
    }
}

private enum class PhoneTab(val label: Int, val icon: ImageVector) {
    KEYPAD(R.string.phone_tab_keypad, MesOSGlyphs.Keypad),
    RECENTS(R.string.phone_tab_recents, MesOSGlyphs.History),
    CONTACTS(R.string.phone_tab_contacts, MesOSGlyphs.Person),
}

/**
 * Places a call through Android's Telecom, which uses MesOS's call screen when MesOS
 * is the phone app. Callers check CALL_PHONE first; a SecurityException falls back to the dialer.
 */
@SuppressLint("MissingPermission")
internal fun placeCall(context: Context, number: String) {
    val uri = Uri.fromParts("tel", number, null)
    try {
        context.getSystemService(TelecomManager::class.java)?.placeCall(uri, Bundle())
            ?: context.startActivitySafely(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: SecurityException) {
        context.startActivitySafely(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun PhoneApp(prefill: String?, onPrefillUsed: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(if (prefill != null) PhoneTab.KEYPAD else PhoneTab.RECENTS) }
    var number by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefill) {
        if (prefill != null) {
            number = prefill
            tab = PhoneTab.KEYPAD
            onPrefillUsed()
        }
    }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingCall
        pendingCall = null
        if (target != null) {
            if (granted) {
                placeCall(context, target)
            } else {
                context.startActivitySafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", target, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    val call: (String) -> Unit = { target ->
        val clean = Dialpad.sanitize(target)
        if (clean.isNotEmpty()) {
            if (context.hasPermission(Manifest.permission.CALL_PHONE)) {
                placeCall(context, clean)
            } else {
                pendingCall = clean
                callPermission.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MesOSTheme.colors.card) {
                PhoneTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            when (tab) {
                PhoneTab.KEYPAD -> KeypadScreen(number = number, onNumber = { number = it }, onCall = call)
                PhoneTab.RECENTS -> RecentsScreen(onCall = call)
                PhoneTab.CONTACTS -> ContactsScreen(onCall = call)
            }
        }
    }
}

// ---- Keypad ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadScreen(number: String, onNumber: (String) -> Unit, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var phones by remember { mutableStateOf<List<Pair<ContactSummary, ContactPhone>>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (context.hasPermission(Manifest.permission.READ_CONTACTS)) {
            phones = withContext(Dispatchers.IO) { ContactsRepository.phones(context) }
        }
    }
    val matches = if (number.length < 2) {
        emptyList()
    } else {
        phones.filter { (contact, phone) -> Dialpad.matchesNumber(number, phone.number) || Dialpad.matchesName(number, contact.name) }
            .distinctBy { it.second.number }
            .take(3)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DefaultPhoneBanner()
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            matches.forEach { (contact, phone) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onNumber(Dialpad.sanitize(phone.number)) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(contact.name, photo = contact.photo, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(phone.number, style = MaterialTheme.typography.labelMedium, color = MesOSTheme.colors.dim)
                    }
                }
            }
        }
        Text(
            number.ifEmpty { " " },
            style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = if (number.length > 12) 28.sp else 36.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (number.isNotEmpty() && matches.none { ContactsRepository.same(it.second.number, number) }) {
            TextButton(onClick = {
                context.startActivitySafely(
                    Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text(stringResource(R.string.phone_add_contact)) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Dialpad.keypad.chunked(3).forEach { row ->
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { (digit, letters) ->
                    Column(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MesOSTheme.colors.card)
                            .combinedClickable(
                                onClick = {
                                    haptics.key()
                                    onNumber(number + digit)
                                },
                                onLongClick = if (digit == '0') {
                                    {
                                        haptics.longPress()
                                        onNumber("$number+")
                                    }
                                } else {
                                    null
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(digit.toString(), style = TextStyle(fontFamily = Sora, fontSize = 30.sp))
                        if (letters.isNotEmpty()) {
                            Text(letters, style = MaterialTheme.typography.labelSmall, color = MesOSTheme.colors.dim)
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(56.dp))
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .clickable(enabled = number.isNotEmpty()) { onCall(number) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MesOSGlyphs.Phone, contentDescription = stringResource(R.string.phone_call), tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = number.isNotEmpty(),
                        onClick = { onNumber(number.dropLast(1)) },
                        onLongClick = { onNumber("") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (number.isNotEmpty()) Icon(MesOSGlyphs.Backspace, contentDescription = stringResource(R.string.phone_delete_digit))
            }
        }
    }
}

/** Offers to make MesOS the phone app (its call screen) while another app is. */
@Composable
private fun DefaultPhoneBanner() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultDialer(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultDialer(context)
    }
    OnResume { isDefault = isDefaultDialer(context) }
    if (isDefault) return
    MesOSCard(modifier = Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.phone_make_default), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.phone_make_default_text), style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim)
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
            if (intent != null) {
                try {
                    request.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.phone_not_available, Toast.LENGTH_SHORT).show()
                }
            }
        }) { Text(stringResource(R.string.phone_make_default_action)) }
    }
}

private fun isDefaultDialer(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    }

// ---- Recents ----

@Composable
private fun RecentsScreen(onCall: (String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CALL_LOG),
        rationale = stringResource(R.string.phone_call_log_rationale),
        icon = MesOSGlyphs.History,
    ) {
        RecentsList(onCall)
    }
}

@Composable
private fun RecentsList(onCall: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by remember { CallLogRepository.changes(context) }.collectAsState(Unit)
    var groups by remember { mutableStateOf<List<CallGroup>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(changes) { groups = withContext(Dispatchers.IO) { CallLogRepository.recent(context) } }
    val list = groups

    MesOSListScreen(
        title = stringResource(R.string.phone_tab_recents),
        actions = {
            if (!list.isNullOrEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.phone_clear)) }
            }
        },
    ) {
        item(key = "banner") { DefaultPhoneBanner() }
        if (list != null && list.isEmpty()) {
            item(key = "empty") { EmptyState(MesOSGlyphs.Phone, stringResource(R.string.phone_recents_empty)) }
        }
        if (!list.isNullOrEmpty()) {
            item(key = "list") {
                ListGroup {
                    list.forEachIndexed { index, group ->
                        if (index > 0) GroupDivider(inset = 72.dp)
                        RecentRow(
                            group = group,
                            onCall = { onCall(group.number) },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { CallLogRepository.delete(context, group) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.phone_clear_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { withContext(Dispatchers.IO) { CallLogRepository.clear(context) } }
                }) { Text(stringResource(R.string.phone_clear), color = MesOSTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
            },
        )
    }
}

@Composable
private fun RecentRow(group: CallGroup, onCall: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val missed = group.kind == CallKind.MISSED || group.kind == CallKind.REJECTED
    val title = (group.name ?: group.number.ifBlank { stringResource(R.string.phone_unknown) }) +
        if (group.count > 1) " (${group.count})" else ""
    val kindLabel = stringResource(
        when (group.kind) {
            CallKind.INCOMING -> R.string.phone_kind_incoming
            CallKind.OUTGOING -> R.string.phone_kind_outgoing
            CallKind.MISSED -> R.string.phone_kind_missed
            CallKind.REJECTED -> R.string.phone_kind_rejected
            CallKind.BLOCKED -> R.string.phone_kind_blocked
            CallKind.VOICEMAIL -> R.string.phone_kind_voicemail
        },
    )
    val time = DateUtils.getRelativeTimeSpanString(group.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    ListRow(
        title = title,
        subtitle = "$kindLabel · $time",
        titleColor = if (missed) MesOSTheme.colors.danger else Color.Unspecified,
        leading = { Avatar(group.name ?: group.number, photo = group.photo, size = 40.dp) },
        onClick = if (group.number.isNotBlank()) onCall else null,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(MesOSGlyphs.More, contentDescription = stringResource(org.mesos.core.R.string.mesos_more), tint = MesOSTheme.colors.dim)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (group.number.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.phone_message)) },
                            leadingIcon = { Icon(MesOSGlyphs.Message, contentDescription = null) },
                            onClick = {
                                menu = false
                                openMessages(context, group.number)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(org.mesos.core.R.string.mesos_copy)) },
                            leadingIcon = { Icon(MesOSGlyphs.Copy, contentDescription = null) },
                            onClick = {
                                menu = false
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("MesOS", group.number))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(org.mesos.core.R.string.mesos_delete), color = MesOSTheme.colors.danger) },
                        leadingIcon = { Icon(MesOSGlyphs.Trash, contentDescription = null, tint = MesOSTheme.colors.danger) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

private fun openMessages(context: Context, number: String) {
    if (!context.startActivitySafely(MesOSApps.message(context, number))) {
        context.startActivitySafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---- Contacts ----

@Composable
private fun ContactsScreen(onCall: (String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS),
        rationale = stringResource(R.string.phone_contacts_rationale),
        icon = MesOSGlyphs.Person,
    ) {
        ContactsList(onCall)
    }
}

@Composable
private fun ContactsList(onCall: (String) -> Unit) {
    val context = LocalContext.current
    val changes by remember { ContactsRepository.changes(context) }.collectAsState(Unit)
    var phones by remember { mutableStateOf<List<Pair<ContactSummary, ContactPhone>>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf<List<Pair<ContactSummary, ContactPhone>>?>(null) }
    LaunchedEffect(changes) { phones = withContext(Dispatchers.IO) { ContactsRepository.phones(context) } }
    val byContact = phones.groupBy { it.first.id }.values.toList()
    val filtered = if (query.isBlank()) {
        byContact
    } else {
        byContact.filter { entries -> SearchText.matches(query, entries.first().first.name) || entries.any { Dialpad.matchesNumber(query, it.second.number) } }
    }
    val favorites = byContact.filter { it.first().first.starred }

    MesOSListScreen(
        title = stringResource(R.string.phone_tab_contacts),
        actions = {
            IconButton(onClick = { context.startActivitySafely(MesOSApps.launchIntent(context, MesOSApps.CONTACTS)) }) {
                Icon(MesOSGlyphs.PersonAdd, contentDescription = stringResource(R.string.phone_open_contacts))
            }
        },
    ) {
        item(key = "search") {
            MesOSSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.phone_search_contacts), modifier = Modifier.fillMaxWidth())
        }
        if (query.isBlank() && favorites.isNotEmpty()) {
            item(key = "favorites") {
                ContactGroup(stringResource(R.string.phone_favorites), favorites, onCall, onChoose = { choosing = it })
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") { EmptyState(MesOSGlyphs.Person, stringResource(R.string.phone_contacts_empty)) }
        } else {
            item(key = "all") {
                ContactGroup(null, filtered, onCall, onChoose = { choosing = it })
            }
        }
    }

    choosing?.let { options ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(options.first().first.name) },
            text = {
                Column {
                    options.forEach { (_, phone) ->
                        Text(
                            phone.number,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choosing = null
                                    onCall(phone.number)
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = null }) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
            },
        )
    }
}

@Composable
private fun ContactGroup(
    label: String?,
    contacts: List<List<Pair<ContactSummary, ContactPhone>>>,
    onCall: (String) -> Unit,
    onChoose: (List<Pair<ContactSummary, ContactPhone>>) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) GroupLabel(label)
        ListGroup {
            contacts.forEachIndexed { index, entries ->
                if (index > 0) GroupDivider(inset = 72.dp)
                val contact = entries.first().first
                ListRow(
                    title = contact.name,
                    subtitle = entries.first().second.number + if (entries.size > 1) " +${entries.size - 1}" else "",
                    leading = { Avatar(contact.name, photo = contact.photo, size = 40.dp) },
                    onClick = { if (entries.size == 1) onCall(entries.first().second.number) else onChoose(entries) },
                    trailing = {
                        IconButton(onClick = { openMessages(context, entries.first().second.number) }) {
                            Icon(MesOSGlyphs.Message, contentDescription = stringResource(R.string.phone_message), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }
    }
}
