package org.mesos.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mesos.core.MesOSIntents
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.theme.MesOSTheme

/** Pages of MesOS Settings. [id] is what [MesOSIntents.EXTRA_SETTINGS_PAGE] carries. */
internal enum class Page(val id: String, val title: Int) {
    MAIN("main", R.string.settings_title),
    APPEARANCE(MesOSIntents.PAGE_APPEARANCE, R.string.settings_appearance),
    HOME(MesOSIntents.PAGE_HOME, R.string.settings_home),
    NOTIFICATIONS(MesOSIntents.PAGE_NOTIFICATIONS, R.string.settings_notifications),
    PRIVACY(MesOSIntents.PAGE_PRIVACY, R.string.settings_privacy),
    LANGUAGE("language", R.string.settings_language),
    APPS("apps", R.string.settings_apps),
    SYSTEM("system", R.string.settings_system),
    UPDATE(MesOSIntents.PAGE_UPDATE, R.string.settings_update),
    ABOUT("about", R.string.settings_about),
    LICENSES("licenses", R.string.settings_licenses);

    companion object {
        fun fromId(id: String?): Page? = entries.firstOrNull { it.id == id }
    }
}

/** What pages can do: open another page, go back, or hand off to Android. */
internal class SettingsNav(
    val open: (Page) -> Unit,
    val back: () -> Unit,
    private val launch: (List<Intent>) -> Unit,
) {
    /** Opens the first of [intents] this device can handle. */
    fun external(vararg intents: Intent) = launch(intents.toList())
}

/**
 * Settings with a page stack. Opened on a specific page (from Home, a notification
 * or another MesOS app), back leaves Settings from that page.
 */
@Composable
internal fun SettingsApp(initialPage: Page?, openExternal: (List<Intent>) -> Unit, finish: () -> Unit) {
    // Page ids joined with "/", so the stack survives rotation and process death.
    var stackIds by rememberSaveable { mutableStateOf((initialPage ?: Page.MAIN).id) }
    val stack = stackIds.split('/').mapNotNull { Page.fromId(it) }.ifEmpty { listOf(Page.MAIN) }

    val nav = SettingsNav(
        open = { page -> stackIds = "$stackIds/${page.id}" },
        back = {
            if ('/' in stackIds) stackIds = stackIds.substringBeforeLast('/') else finish()
        },
        launch = openExternal,
    )
    BackHandler(enabled = stack.size > 1) { nav.back() }

    AnimatedContent(
        targetState = stack,
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            val enter = slideInHorizontally(tween(320)) { width -> if (forward) width / 3 else -width / 6 } + fadeIn(tween(240))
            val exit = slideOutHorizontally(tween(320)) { width -> if (forward) -width / 6 else width / 3 } + fadeOut(tween(160))
            enter togetherWith exit
        },
        contentKey = { pages -> pages.joinToString("/") { it.id } },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        label = "settingsPage",
    ) { pages ->
        val page = pages.last()
        val canLeave = pages.size > 1 || page != Page.MAIN
        when (page) {
            Page.MAIN -> MainPage(nav)
            Page.APPEARANCE -> AppearancePage(nav, canLeave)
            Page.HOME -> HomePage(nav, canLeave)
            Page.NOTIFICATIONS -> NotificationsPage(nav, canLeave)
            Page.PRIVACY -> PrivacyPage(nav, canLeave)
            Page.LANGUAGE -> LanguagePage(nav, canLeave)
            Page.APPS -> AppsPage(nav, canLeave)
            Page.SYSTEM -> SystemPage(nav, canLeave)
            Page.UPDATE -> UpdatePage(nav, canLeave)
            Page.ABOUT -> AboutPage(nav, canLeave)
            Page.LICENSES -> LicensesPage(nav, canLeave)
        }
    }
}

/** A settings page: large collapsing title, back button, grouped content. */
@Composable
internal fun SettingsPage(
    page: Page,
    nav: SettingsNav,
    canLeave: Boolean,
    content: LazyListScope.() -> Unit,
) {
    MesOSListScreen(
        title = stringResource(page.title),
        onBack = if (canLeave) nav.back else null,
        content = content,
    )
}

/** "On" / "Off" pill at the end of a status row. */
@Composable
internal fun StatusPill(on: Boolean, onText: String = stringResource(R.string.settings_on), offText: String = stringResource(R.string.settings_off)) {
    val colors = MesOSTheme.colors
    val color = if (on) colors.success else colors.dim
    Text(
        text = if (on) onText else offText,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (on) 0.16f else 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Label above a value, for read-only information inside a list group. */
@Composable
internal fun InfoLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MesOSTheme.colors.dim)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Small explanatory text under a group. */
@Composable
internal fun Note(text: String, color: Color = MesOSTheme.colors.dim) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
