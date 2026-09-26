package org.mesos.tips

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSIntents
import org.mesos.core.MesOSRelease
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.IconBadge
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme

/** MesOS Tips: what is new, and how to get the most out of MesOS. */
class TipsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Tips opened")
        setContent {
            MesOSUserTheme {
                TipsApp(onBack = ::finish)
            }
        }
    }
}

/** Where a tip's button takes the user. */
private sealed interface TipAction {
    fun intent(context: Context): Intent

    data class App(val className: String) : TipAction {
        override fun intent(context: Context) = MesOSApps.launchIntent(context, className)
    }

    data class SettingsPage(val page: String) : TipAction {
        override fun intent(context: Context): Intent =
            MesOSIntents.settings(context, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private class Tip(
    val icon: ImageVector,
    val color: Color,
    val title: Int,
    val text: Int,
    val action: TipAction? = null,
    val actionLabel: Int = R.string.tips_try,
)

private class Section(val title: Int, val tips: List<Tip>)

private val sections = listOf(
    Section(
        R.string.tips_section_home,
        listOf(
            Tip(MesOSGlyphs.ChevronDown, MesOSPalette.Indigo, R.string.tip_control_title, R.string.tip_control_text),
            Tip(MesOSGlyphs.Search, MesOSPalette.Blue, R.string.tip_search_title, R.string.tip_search_text),
            Tip(MesOSGlyphs.Grid, MesOSPalette.Violet, R.string.tip_edit_title, R.string.tip_edit_text),
            Tip(MesOSGlyphs.Widgets, MesOSPalette.Teal, R.string.tip_widgets_title, R.string.tip_widgets_text),
            Tip(
                MesOSGlyphs.Palette,
                MesOSPalette.Pink,
                R.string.tip_style_title,
                R.string.tip_style_text,
                TipAction.SettingsPage(MesOSIntents.PAGE_APPEARANCE),
                R.string.tips_open_settings,
            ),
        ),
    ),
    Section(
        R.string.tips_section_apps,
        listOf(
            Tip(MesOSGlyphs.Qr, MesOSPalette.Slate, R.string.tip_scanner_title, R.string.tip_scanner_text, TipAction.App(MesOSApps.SCANNER)),
            Tip(MesOSGlyphs.Mic, MesOSPalette.Rose, R.string.tip_recorder_title, R.string.tip_recorder_text, TipAction.App(MesOSApps.RECORDER)),
            Tip(MesOSGlyphs.Alarm, MesOSPalette.Orange, R.string.tip_clock_title, R.string.tip_clock_text, TipAction.App(MesOSApps.CLOCK)),
            Tip(MesOSGlyphs.Cloud, MesOSPalette.Sky, R.string.tip_weather_title, R.string.tip_weather_text, TipAction.App(MesOSApps.WEATHER)),
            Tip(MesOSGlyphs.Music, MesOSPalette.Pink, R.string.tip_music_title, R.string.tip_music_text, TipAction.App(MesOSApps.MUSIC)),
            Tip(MesOSGlyphs.Battery, MesOSPalette.Green, R.string.tip_care_title, R.string.tip_care_text, TipAction.App(MesOSApps.CARE)),
        ),
    ),
    Section(
        R.string.tips_section_privacy,
        listOf(
            Tip(
                MesOSGlyphs.Shield,
                MesOSPalette.Slate,
                R.string.tip_privacy_title,
                R.string.tip_privacy_text,
                TipAction.SettingsPage(MesOSIntents.PAGE_PRIVACY),
                R.string.tips_open_settings,
            ),
            Tip(
                MesOSGlyphs.Download,
                MesOSPalette.Indigo,
                R.string.tip_update_title,
                R.string.tip_update_text,
                TipAction.SettingsPage(MesOSIntents.PAGE_UPDATE),
                R.string.tips_open_settings,
            ),
            Tip(
                MesOSGlyphs.Globe,
                MesOSPalette.Orange,
                R.string.tip_language_title,
                R.string.tip_language_text,
                TipAction.SettingsPage("language"),
                R.string.tips_open_settings,
            ),
        ),
    ),
)

@Composable
private fun TipsApp(onBack: () -> Unit) {
    MesOSListScreen(title = stringResource(R.string.tips_app_name), onBack = onBack) {
        item(key = "new") { WhatsNew() }
        sections.forEach { section ->
            item(key = "section-${section.title}") { GroupLabel(stringResource(section.title)) }
            section.tips.forEach { tip ->
                item(key = "tip-${tip.title}") { TipCard(tip) }
            }
        }
    }
}

@Composable
private fun WhatsNew() {
    val accent = MaterialTheme.colorScheme.primary
    val highlights = listOf(
        R.string.tips_new_design,
        R.string.tips_new_home,
        R.string.tips_new_control,
        R.string.tips_new_settings,
        R.string.tips_new_apps,
        R.string.tips_new_updates,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), accent.copy(alpha = 0.95f), Color(0xFF0F766E))))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.tips_whats_new, MesOSRelease.current.versionName),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        highlights.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Text(stringResource(line), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
}

@Composable
private fun TipCard(tip: Tip) {
    val context = LocalContext.current
    MesOSCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(tip.icon, tip.color, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(tip.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(tip.text), style = MaterialTheme.typography.bodyMedium, color = MesOSTheme.colors.dim)
                tip.action?.let { action ->
                    FilledTonalButton(
                        onClick = { context.startActivitySafely(action.intent(context)) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(tip.actionLabel))
                        Icon(MesOSGlyphs.ChevronRight, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                    }
                }
            }
        }
    }
}
