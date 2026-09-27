package org.mesos.settings

import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mesos.core.log.MesOSLog
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.Wallpaper
import org.mesos.core.system.HomeRole
import org.mesos.core.ui.IconBadge
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSMark
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.glass
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.wallpaper.AuroraLiveWallpaper
import org.mesos.core.ui.wallpaper.WallpaperLayer
import org.mesos.updater.UpdateCheckScheduler

/**
 * The MesOS setup wizard, shown by MesOS Home on first start and from
 * Settings → System. Every step can be skipped; nothing is granted without the
 * user confirming it in Android's own dialogs.
 */
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SETTINGS, "MesOS setup opened")
        setContent {
            MesOSUserTheme(forceDark = true) {
                // No Surface here (the wallpaper is the background), so set the text colour.
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    SetupFlow(onFinish = ::complete)
                }
            }
        }
    }

    private fun complete() {
        MesOSPreferences.get(this).setSetupDone(true)
        UpdateCheckScheduler.sync(this)
        MesOSLog.i(MesOSLog.SETTINGS, "MesOS setup finished")
        finish()
    }
}

private enum class SetupStep { WELCOME, STYLE, PERMISSIONS, HOME, DONE }

private val glassShape = RoundedCornerShape(24.dp)

@Composable
private fun SetupFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val wallpaper by preferences.wallpaper.collectAsState()
    val accent by preferences.accent.collectAsState()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val steps = SetupStep.entries
    val step = steps[index]
    val next: () -> Unit = { if (index < steps.lastIndex) index++ else onFinish() }

    // Back walks through the steps; on the first step it does nothing (Home would reopen setup).
    BackHandler { if (index > 0) index-- }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070C1A)),
    ) {
        WallpaperLayer(wallpaper = if (wallpaper == Wallpaper.SYSTEM) Wallpaper.AURORA else wallpaper, accent = accent)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 1f to Color.Black.copy(alpha = 0.62f))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDots(count = steps.size, current = index, modifier = Modifier.weight(1f))
                if (step != SetupStep.DONE) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.setup_skip), color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(360)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(360)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "setupStep",
            ) { shown ->
                when (steps[shown]) {
                    SetupStep.WELCOME -> WelcomeStep()
                    SetupStep.STYLE -> StyleStep()
                    SetupStep.PERMISSIONS -> PermissionsStep()
                    SetupStep.HOME -> HomeStep()
                    SetupStep.DONE -> DoneStep()
                }
            }
            Button(
                onClick = next,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B1224)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(
                        when (step) {
                            SetupStep.WELCOME -> R.string.setup_start
                            SetupStep.DONE -> R.string.setup_finish
                            else -> R.string.setup_continue
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 7.dp, label = "dot")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i <= current) 0.95f else 0.3f)),
            )
        }
    }
}

@Composable
private fun StepColumn(title: String, message: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.78f))
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(glassShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Spacer(Modifier.height(48.dp))
        MesOSMark(size = 96.dp)
        Text(
            stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.setup_welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            )
            LanguageChoices()
        }
    }
}

@Composable
private fun StyleStep() {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val theme by preferences.themeMode.collectAsState()
    val accent by preferences.accent.collectAsState()
    val wallpaper by preferences.wallpaper.collectAsState()
    val label = Color.White.copy(alpha = 0.85f)

    StepColumn(stringResource(R.string.setup_style_title), stringResource(R.string.setup_style_message)) {
        GlassCard {
            CardTitle(stringResource(R.string.appearance_wallpaper))
            WallpaperPicker(wallpaper, accent, preferences::setWallpaper, labelColor = label, contentPadding = PaddingValues(end = 4.dp))
        }
        GlassCard {
            CardTitle(stringResource(R.string.appearance_accent))
            AccentPicker(accent, preferences::setAccent)
        }
        GlassCard {
            CardTitle(stringResource(R.string.setup_theme_title))
            ThemePicker(theme, preferences::setThemeMode, labelColor = label)
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }
    val request = rememberPermissionRequest { tick++ }
    val specs = remember { permissionSpecs(context).filter { it.key in setupPermissions } }
    val granted = remember(tick) { specs.associate { it.key to it.isGranted(context) } }
    val listener = remember(tick) { SpecialAccess.notificationListener(context) }

    StepColumn(stringResource(R.string.setup_permissions_title), stringResource(R.string.setup_permissions_message)) {
        GlassCard {
            SetupRow(
                icon = MesOSGlyphs.Bell,
                color = MesOSPalette.Rose,
                title = stringResource(R.string.notifications_access),
                message = stringResource(R.string.setup_permission_listener),
                done = listener,
                onAllow = {
                    val intents = SpecialAccess.notificationListenerIntents(context)
                    intents.firstOrNull { context.startActivitySafely(it) }
                },
            )
            specs.forEach { spec ->
                SetupRow(
                    icon = spec.icon,
                    color = spec.color,
                    title = stringResource(spec.title),
                    message = stringResource(spec.summary),
                    done = granted[spec.key] == true,
                    onAllow = { request(spec.permissions) },
                )
            }
        }
        Text(
            stringResource(R.string.setup_permissions_note),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )
    }
}

/** Permission groups offered during setup (Phone and SMS are asked by their apps). */
private val setupPermissions = setOf("notifications", "location", "photos", "music", "storage", "camera", "microphone", "contacts")

@Composable
private fun SetupRow(
    icon: ImageVector,
    color: Color,
    title: String,
    message: String,
    done: Boolean,
    onAllow: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, color, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
        if (done) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34D399).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MesOSGlyphs.Check, contentDescription = stringResource(R.string.setup_granted), tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
            }
        } else {
            FilledTonalButton(
                onClick = onAllow,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(stringResource(R.string.setup_allow), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HomeStep() {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val autoCheck by preferences.autoUpdateCheck.collectAsState()
    var isHome by remember { mutableStateOf(HomeRole.isMesOSDefaultHome(context)) }
    var liveActive by remember { mutableStateOf(AuroraLiveWallpaper.isActive(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isHome = HomeRole.isMesOSDefaultHome(context)
    }
    OnResume {
        isHome = HomeRole.isMesOSDefaultHome(context)
        liveActive = AuroraLiveWallpaper.isActive(context)
    }

    StepColumn(stringResource(R.string.setup_home_title), stringResource(R.string.setup_home_message)) {
        GlassCard {
            SetupRow(
                icon = MesOSGlyphs.Home,
                color = MesOSPalette.Indigo,
                title = stringResource(R.string.home_default),
                message = stringResource(R.string.setup_home_default),
                done = isHome,
                onAllow = {
                    try {
                        roleLauncher.launch(HomeRole.requestIntent(context))
                    } catch (e: ActivityNotFoundException) {
                        isHome = HomeRole.isMesOSDefaultHome(context)
                    }
                },
            )
            SetupRow(
                icon = MesOSGlyphs.Lock,
                color = MesOSPalette.Violet,
                title = stringResource(R.string.appearance_live_wallpaper),
                message = stringResource(R.string.appearance_live_wallpaper_summary),
                done = liveActive,
                onAllow = { context.startActivitySafely(AuroraLiveWallpaper.chooserIntent(context)) },
            )
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(MesOSGlyphs.Refresh, MesOSPalette.Teal, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_auto_check), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        stringResource(R.string.update_auto_check_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Switch(checked = autoCheck, onCheckedChange = preferences::setAutoUpdateCheck)
            }
        }
    }
}

@Composable
private fun DoneStep() {
    StepColumn(stringResource(R.string.setup_done_title), stringResource(R.string.setup_done_message)) {
        GlassCard {
            Tip(MesOSGlyphs.ChevronDown, stringResource(R.string.home_gesture_control))
            Tip(MesOSGlyphs.ChevronUp, stringResource(R.string.home_gesture_drawer))
            Tip(MesOSGlyphs.Edit, stringResource(R.string.home_gesture_edit))
            Tip(MesOSGlyphs.Settings, stringResource(R.string.setup_done_settings))
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
    }
}
