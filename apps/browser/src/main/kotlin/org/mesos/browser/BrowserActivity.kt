package org.mesos.browser

import android.app.DownloadManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSUserTheme
import java.net.URISyntaxException

/** MesOS Browser: tabs, bookmarks, history and downloads on Android's WebView. */
class BrowserActivity : ComponentActivity(), BrowserHost {

    private lateinit var store: BrowserStore
    private lateinit var tabs: TabManager
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** Full-screen video shown over the browser, and how to tell the page it ended. */
    private var customView by mutableStateOf<View?>(null)
    private var hideCustomView: (() -> Unit)? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Browser opened")
        store = BrowserStore.get(this)
        lifecycleScope.launch(Dispatchers.IO) { store.load() }
        tabs = TabManager(this, this)

        // Tabs from last time (Android may have closed MesOS), then whatever the intent asks for.
        store.openTabs.forEach { tabs.open(it, select = true) }
        val opened = savedInstanceState == null && handle(intent)
        if (!opened && tabs.tabs.isEmpty()) tabs.open()

        setContent {
            MesOSUserTheme {
                BrowserScreen(
                    tabs = tabs,
                    store = store,
                    customView = customView,
                    onExitCustomView = { hideCustomView?.invoke() },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Opens what another app asked for. Returns whether the intent carried a page. */
    private fun handle(intent: Intent?): Boolean {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { UrlPolicy.classify(it) == LinkAction.LOAD }
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
                ?.takeIf { it.isNotBlank() }
                ?.let { UrlPolicy.resolve(it, store.engine.value) }
            else -> null
        } ?: return false
        val reusable = tabs.current?.takeIf { it.isStartPage && it.webView == null }
        if (reusable != null) reusable.url = url else tabs.open(url)
        return true
    }

    override fun onPause() {
        tabs.onPause()
        store.openTabs = tabs.tabs.map { it.url }.filter { it.isNotEmpty() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        tabs.onResume()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        tabs.destroyAll()
        super.onDestroy()
    }

    // ---- BrowserHost ----

    override fun openExternal(tab: BrowserTab, url: String) {
        if (url.startsWith("intent:", ignoreCase = true)) {
            val intent = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (e: URISyntaxException) {
                return
            }
            // A web page may only ask for a browsable activity, never a specific component.
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            if (startActivitySafely(intent)) return
            UrlPolicy.safeFallback(intent.getStringExtra("browser_fallback_url"))?.let {
                tabs.load(tab, it)
                return
            }
            intent.`package`?.let { pkg ->
                if (startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))) return
            }
            Toast.makeText(this, R.string.browser_no_app, Toast.LENGTH_SHORT).show()
            return
        }
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        if (!startActivitySafely(view)) Toast.makeText(this, R.string.browser_no_app, Toast.LENGTH_SHORT).show()
    }

    override fun download(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (UrlPolicy.classify(url) != LinkAction.LOAD) {
            Toast.makeText(this, R.string.browser_download_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                mimeType?.let(::setMimeType)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(name)
                setDescription(UrlPolicy.displayHost(url))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            getSystemService(DownloadManager::class.java)?.enqueue(request)
            Toast.makeText(this, getString(R.string.browser_downloading, name), Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Download not started", e)
            Toast.makeText(this, R.string.browser_download_failed, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Download not allowed", e)
            Toast.makeText(this, R.string.browser_download_failed, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Download address rejected", e)
            Toast.makeText(this, R.string.browser_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileChooser.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            false
        }
    }

    override fun showCustomView(view: View?, onHide: (() -> Unit)?) {
        customView = view
        hideCustomView = onHide
    }

    override fun pageFinished(tab: BrowserTab) {
        val url = tab.url
        val title = tab.title
        lifecycleScope.launch(Dispatchers.IO) { store.recordVisit(url, title) }
    }
}
