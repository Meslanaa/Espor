package org.mesos.scanner

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.core.MesOSApps
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.IconBadge
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.rememberHaptics
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.min

/** MesOS Scanner: QR codes and barcodes from the camera or from a picture. */
class ScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Scanner opened")
        setContent {
            MesOSTheme(darkTheme = true) {
                ScannerApp(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun ScannerApp(onClose: () -> Unit) {
    val context = LocalContext.current
    val history = remember(context) { ScanHistory.get(context) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<DecodedCode?>(null) }
    // What the result sheet shows, kept while it slides away after "Scan again".
    var lastResult by remember { mutableStateOf<DecodedCode?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var torch by rememberSaveable { mutableStateOf(false) }
    var decodingImage by remember { mutableStateOf(false) }
    val show: (DecodedCode) -> Unit = { code ->
        history.add(code)
        result = code
        lastResult = code
        haptics.confirm()
    }
    val noCode = stringResource(R.string.scanner_no_code_in_image)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                decodingImage = true
                val code = withContext(Dispatchers.Default) { ImageDecoderHelper.decode(context, uri) }
                decodingImage = false
                if (code != null) show(code) else Toast.makeText(context, noCode, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = result != null || showHistory) {
        if (showHistory) showHistory = false else result = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PermissionGate(
            permissions = listOf(Manifest.permission.CAMERA),
            rationale = stringResource(R.string.scanner_camera_rationale),
            icon = MesOSGlyphs.Qr,
        ) {
            Box(Modifier.fillMaxSize()) {
                CameraScanner(paused = result != null || showHistory || decodingImage, torch = torch, onCode = show)
                Viewfinder(scanning = result == null)
                Text(
                text = stringResource(R.string.scanner_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 32.dp, end = 32.dp, bottom = 120.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(MesOSGlyphs.Close, stringResource(R.string.scanner_close), onClose)
            Text(
                stringResource(R.string.scanner_app_name),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            RoundButton(MesOSGlyphs.Flashlight, stringResource(R.string.scanner_torch), { torch = !torch }, active = torch)
            RoundButton(MesOSGlyphs.Image, stringResource(R.string.scanner_from_image), {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            RoundButton(MesOSGlyphs.History, stringResource(R.string.scanner_history), { showHistory = true })
        }

        if (decodingImage) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        AnimatedVisibility(
            visible = result != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastResult?.let { code -> ResultSheet(code, onScanAgain = { result = null }) }
        }

        AnimatedVisibility(visible = showHistory, enter = fadeIn(), exit = fadeOut()) {
            HistoryPanel(
                history = history,
                onOpen = { record ->
                    showHistory = false
                    val code = DecodedCode(record.text, formatOf(record.format))
                    result = code
                    lastResult = code
                },
                onClose = { showHistory = false },
            )
        }
    }
}

private fun formatOf(name: String): BarcodeFormat =
    runCatching { BarcodeFormat.valueOf(name) }.getOrDefault(BarcodeFormat.QR_CODE)

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.Black.copy(alpha = 0.35f)),
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Color.Black else Color.White)
    }
}

@Composable
private fun CameraScanner(paused: Boolean, torch: Boolean, onCode: (DecodedCode) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val controller = remember(context) { LifecycleCameraController(context) }
    val analyzer = remember(context) {
        val main = ContextCompat.getMainExecutor(context)
        CodeAnalyzer { code -> main.execute { latestOnCode(code) } }
    }
    DisposableEffect(lifecycleOwner, controller) {
        val executor = Executors.newSingleThreadExecutor()
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(executor, analyzer)
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }
    LaunchedEffect(paused) { analyzer.paused = paused }
    LaunchedEffect(torch) { runCatching { controller.enableTorch(torch) } }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Dimmed surroundings, corner brackets and a sweeping line while scanning. */
@Composable
private fun Viewfinder(scanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val side = min(size.width, size.height) * 0.7f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f - size.height * 0.04f
        val right = left + side
        val bottom = top + side
        val radius = 28.dp.toPx()
        val hole = Path().apply { addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(radius))) }
        clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.55f)) }

        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val length = side * 0.17f
        val arc = Size(radius * 2, radius * 2)
        val white = Color.White
        // Top left, top right, bottom right, bottom left: a rounded corner and two short arms each.
        drawArc(white, 180f, 90f, false, Offset(left, top), arc, style = stroke)
        drawLine(white, Offset(left + radius, top), Offset(left + length, top), stroke.width, StrokeCap.Round)
        drawLine(white, Offset(left, top + radius), Offset(left, top + length), stroke.width, StrokeCap.Round)
        drawArc(white, 270f, 90f, false, Offset(right - 2 * radius, top), arc, style = stroke)
        drawLine(white, Offset(right - radius, top), Offset(right - length, top), stroke.width, StrokeCap.Round)
        drawLine(white, Offset(right, top + radius), Offset(right, top + length), stroke.width, StrokeCap.Round)
        drawArc(white, 0f, 90f, false, Offset(right - 2 * radius, bottom - 2 * radius), arc, style = stroke)
        drawLine(white, Offset(right, bottom - radius), Offset(right, bottom - length), stroke.width, StrokeCap.Round)
        drawLine(white, Offset(right - radius, bottom), Offset(right - length, bottom), stroke.width, StrokeCap.Round)
        drawArc(white, 90f, 90f, false, Offset(left, bottom - 2 * radius), arc, style = stroke)
        drawLine(white, Offset(left + radius, bottom), Offset(left + length, bottom), stroke.width, StrokeCap.Round)
        drawLine(white, Offset(left, bottom - radius), Offset(left, bottom - length), stroke.width, StrokeCap.Round)

        if (scanning) {
            val y = top + side * sweep
            val inset = 20.dp.toPx()
            val glow = 26.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to accent.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                    startY = y - glow,
                    endY = y + glow,
                ),
                topLeft = Offset(left + inset, y - glow),
                size = Size(side - 2 * inset, glow * 2),
            )
            drawLine(accent, Offset(left + inset, y), Offset(right - inset, y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** How a kind of content is shown: icon, colour and name. */
private data class Kind(val icon: ImageVector, val color: Color, val label: Int)

private fun kindOf(content: ScanContent, format: BarcodeFormat): Kind = when (content) {
    is ScanContent.Url -> Kind(MesOSGlyphs.Link, MesOSPalette.Blue, R.string.scanner_kind_link)
    is ScanContent.Wifi -> Kind(MesOSGlyphs.Wifi, MesOSPalette.Teal, R.string.scanner_kind_wifi)
    is ScanContent.Phone -> Kind(MesOSGlyphs.Phone, MesOSPalette.Green, R.string.scanner_kind_phone)
    is ScanContent.Email -> Kind(MesOSGlyphs.Mail, MesOSPalette.Orange, R.string.scanner_kind_email)
    is ScanContent.Sms -> Kind(MesOSGlyphs.Message, MesOSPalette.Sky, R.string.scanner_kind_sms)
    is ScanContent.Geo -> Kind(MesOSGlyphs.Location, MesOSPalette.Rose, R.string.scanner_kind_location)
    is ScanContent.Contact -> Kind(MesOSGlyphs.Person, MesOSPalette.Violet, R.string.scanner_kind_contact)
    is ScanContent.Text -> if (format.isProductCode()) {
        Kind(MesOSGlyphs.Grid, MesOSPalette.Amber, R.string.scanner_kind_product)
    } else {
        Kind(MesOSGlyphs.Qr, MesOSPalette.Slate, R.string.scanner_kind_text)
    }
}

private fun BarcodeFormat.isProductCode() =
    this == BarcodeFormat.EAN_13 || this == BarcodeFormat.EAN_8 || this == BarcodeFormat.UPC_A || this == BarcodeFormat.UPC_E

private fun formatLabel(format: BarcodeFormat): String =
    if (format == BarcodeFormat.QR_CODE) "QR" else format.name.replace('_', ' ')

@Composable
private fun ResultSheet(code: DecodedCode, onScanAgain: () -> Unit) {
    val context = LocalContext.current
    val content = remember(code) { ScanParser.parse(code.text) }
    val kind = kindOf(content, code.format)
    val copied = stringResource(org.mesos.core.R.string.mesos_copied)
    val failed = stringResource(org.mesos.core.R.string.mesos_open_failed)
    val open: (Array<Intent>) -> Unit = { intents ->
        if (intents.none { context.startActivitySafely(it) }) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
    }
    val copy: (String, Boolean) -> Unit = { text, sensitive ->
        copyText(context, text, sensitive)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF0F1526))
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(kind.icon, kind.color, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(kind.label), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(formatLabel(code.format), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            }
        }
        Column(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SelectionContainer { ContentDetails(content) }
        }
        if (content is ScanContent.Url && !content.secure) {
            Text(
                stringResource(R.string.scanner_insecure_link),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFBBF24),
            )
        }

        PrimaryAction(content, open, copy)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { copy(content.raw, false) }, modifier = Modifier.weight(1f)) {
                Icon(MesOSGlyphs.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(org.mesos.core.R.string.mesos_copy))
            }
            FilledTonalButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content.raw)
                    open(arrayOf(Intent.createChooser(send, null)))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(MesOSGlyphs.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(org.mesos.core.R.string.mesos_share))
            }
        }
        TextButton(onClick = onScanAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.scanner_scan_again), color = Color.White)
        }
    }
}

@Composable
private fun ContentDetails(content: ScanContent) {
    val strong = MaterialTheme.typography.bodyLarge
    val dim = Color.White.copy(alpha = 0.65f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (content) {
            is ScanContent.Wifi -> {
                Detail(stringResource(R.string.scanner_wifi_name), content.ssid)
                Detail(
                    stringResource(R.string.scanner_wifi_security),
                    when (content.security) {
                        WifiSecurity.OPEN -> stringResource(R.string.scanner_wifi_open)
                        WifiSecurity.WEP -> "WEP"
                        WifiSecurity.WPA -> "WPA/WPA2"
                        WifiSecurity.WPA3 -> "WPA3"
                    },
                )
                content.password?.let { Detail(stringResource(R.string.scanner_wifi_password), it) }
            }
            is ScanContent.Email -> {
                Text(content.address, style = strong, color = Color.White)
                content.subject?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = dim) }
                content.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = dim) }
            }
            is ScanContent.Sms -> {
                Text(content.number, style = strong, color = Color.White)
                content.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = dim) }
            }
            is ScanContent.Geo -> {
                content.label?.let { Text(it, style = strong, color = Color.White) }
                Text("%.5f, %.5f".format(java.util.Locale.ROOT, content.latitude, content.longitude), style = MaterialTheme.typography.bodyMedium, color = dim)
            }
            is ScanContent.Contact -> {
                content.name?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = Color.White) }
                content.organization?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = dim) }
                (content.phones + content.emails).forEach { Text(it, style = strong, color = Color.White) }
            }
            is ScanContent.Url -> Text(content.url, style = strong, color = Color.White)
            is ScanContent.Phone -> Text(content.number, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            is ScanContent.Text -> Text(content.raw, style = strong, color = Color.White)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

/** The one thing most people want to do with this kind of code. */
@Composable
private fun PrimaryAction(content: ScanContent, open: (Array<Intent>) -> Unit, copy: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val passwordCopied = stringResource(R.string.scanner_wifi_password_copied)
    val action = when (content) {
        is ScanContent.Url -> Action(R.string.scanner_open_link, MesOSGlyphs.Globe) {
            open(arrayOf(MesOSApps.openInBrowser(context, content.url), Intent(Intent.ACTION_VIEW, Uri.parse(content.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
        }
        is ScanContent.Wifi -> Action(R.string.scanner_wifi_connect, MesOSGlyphs.Wifi) {
            val suggestion = wifiSuggestion(content)
            if (suggestion != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                open(
                    arrayOf(
                        Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion)),
                    ),
                )
            } else {
                content.password?.let {
                    copy(it, true)
                    Toast.makeText(context, passwordCopied, Toast.LENGTH_LONG).show()
                }
                open(arrayOf(Intent(Settings.ACTION_WIFI_SETTINGS)))
            }
        }
        is ScanContent.Phone -> Action(R.string.scanner_call, MesOSGlyphs.Phone) {
            open(arrayOf(MesOSApps.dial(context, content.number), Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", content.number, null))))
        }
        is ScanContent.Email -> Action(R.string.scanner_send_email, MesOSGlyphs.Mail) {
            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(content.address))
                .apply {
                    content.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    content.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                }
            open(arrayOf(mail))
        }
        is ScanContent.Sms -> Action(R.string.scanner_send_message, MesOSGlyphs.Message) {
            val body = content.body
            val mesos = MesOSApps.message(context, content.number).apply { if (body != null) putExtra("sms_body", body) }
            val any = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", content.number, null)).apply { if (body != null) putExtra("sms_body", body) }
            open(arrayOf(mesos, any))
        }
        is ScanContent.Geo -> Action(R.string.scanner_open_map, MesOSGlyphs.Location) {
            val lat = content.latitude
            val lon = content.longitude
            val label = Uri.encode(content.label ?: "$lat,$lon")
            val osm = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon"
            open(
                arrayOf(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")),
                    MesOSApps.openInBrowser(context, osm),
                    Intent(Intent.ACTION_VIEW, Uri.parse(osm)),
                ),
            )
        }
        is ScanContent.Contact -> Action(R.string.scanner_add_contact, MesOSGlyphs.PersonAdd) {
            val insert = Intent(ContactsContract.Intents.Insert.ACTION)
                .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply {
                    content.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                    content.phones.getOrNull(0)?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                    content.phones.getOrNull(1)?.let { putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
                    content.emails.getOrNull(0)?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                    content.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
                }
            open(arrayOf(insert))
        }
        is ScanContent.Text -> Action(R.string.scanner_search_web, MesOSGlyphs.Search) {
            val query = content.raw
            val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val url = "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8")
            open(arrayOf(Intent(search).setClassName(context.packageName, MesOSApps.BROWSER), search, Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        }
    }
    Button(onClick = action.run, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(action.label), style = MaterialTheme.typography.titleSmall)
    }
}

private class Action(val label: Int, val icon: ImageVector, val run: () -> Unit)

private fun wifiSuggestion(wifi: ScanContent.Wifi): WifiNetworkSuggestion? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
        val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
        val password = wifi.password
        when (wifi.security) {
            WifiSecurity.OPEN -> Unit
            WifiSecurity.WPA -> builder.setWpa2Passphrase(password ?: return null)
            WifiSecurity.WPA3 -> builder.setWpa3Passphrase(password ?: return null)
            WifiSecurity.WEP -> return null // Not supported by Android's network suggestions.
        }
        builder.build()
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun copyText(context: Context, text: String, sensitive: Boolean) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("MesOS", text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
}

@Composable
private fun HistoryPanel(history: ScanHistory, onOpen: (ScanRecord) -> Unit, onClose: () -> Unit) {
    val records by history.records.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20A0F1E))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(MesOSGlyphs.Back, stringResource(org.mesos.core.R.string.mesos_back), onClose)
            Text(
                stringResource(R.string.scanner_history),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            if (records.isNotEmpty()) {
                TextButton(onClick = history::clear) { Text(stringResource(R.string.scanner_clear_history), color = Color.White) }
            }
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.scanner_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(records, key = { it.text + it.time }) { record ->
                val format = formatOf(record.format)
                val kind = kindOf(ScanParser.parse(record.text), format)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(record) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconBadge(kind.icon, kind.color, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            record.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(kind.label) + " · " +
                                DateUtils.getRelativeTimeSpanString(record.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
