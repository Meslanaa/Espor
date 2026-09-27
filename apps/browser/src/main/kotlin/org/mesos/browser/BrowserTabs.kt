package org.mesos.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mesos.core.log.MesOSLog

/** Why a page could not be shown. */
sealed interface PageError {
    data object Insecure : PageError
    data class Network(val description: String) : PageError
    data object Crashed : PageError
}

/** One browser tab. Its WebView is created when the tab is first shown. */
@Stable
class BrowserTab(val id: Long, startUrl: String) {
    /** Observable, so the page is shown again after its renderer crashed and was reloaded. */
    var webView by mutableStateOf<WebView?>(null)
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var favicon by mutableStateOf<Bitmap?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var error by mutableStateOf<PageError?>(null)
    var desktop by mutableStateOf(false)

    /** A new tab shows the MesOS start page until something is loaded. */
    val isStartPage: Boolean get() = url.isEmpty()
}

/** What tabs need from the activity: other apps, downloads, file pickers, full screen video. */
internal interface BrowserHost {
    fun openExternal(tab: BrowserTab, url: String)
    fun download(url: String, userAgent: String, contentDisposition: String?, mimeType: String?)
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun showCustomView(view: View?, onHide: (() -> Unit)?)
    fun pageFinished(tab: BrowserTab)
}

/** The open tabs and their WebViews. Lives as long as the browser activity. */
@Stable
internal class TabManager(private val activity: Activity, private val host: BrowserHost) {

    val tabs = mutableStateListOf<BrowserTab>()
    var currentId by mutableLongStateOf(-1L)
        private set
    private var nextId = 1L

    val current: BrowserTab?
        get() = tabs.firstOrNull { it.id == currentId } ?: tabs.lastOrNull()

    fun open(url: String = "", select: Boolean = true): BrowserTab {
        if (tabs.size >= BrowserStore.MAX_TABS) tabs.firstOrNull()?.let(::close)
        val tab = BrowserTab(nextId++, url)
        tabs += tab
        if (select) currentId = tab.id
        return tab
    }

    fun select(tab: BrowserTab) {
        currentId = tab.id
    }

    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tabs.removeAt(index)
        tab.webView?.let(::destroy)
        tab.webView = null
        if (tabs.isEmpty()) {
            // There is always a tab: closing the last one leaves a fresh start page.
            open()
        } else if (tab.id == currentId) {
            currentId = tabs[index.coerceAtMost(tabs.lastIndex)].id
        }
    }

    /** Back from an error page: the previous page, or the start page. */
    fun back(tab: BrowserTab) {
        tab.error = null
        val view = tab.webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            tab.url = ""
        }
    }

    fun load(tab: BrowserTab, url: String) {
        if (url.isEmpty()) return
        tab.error = null
        tab.url = url
        // A new WebView loads tab.url itself when it is created.
        tab.webView?.loadUrl(url) ?: webViewFor(tab)
    }

    fun setDesktop(tab: BrowserTab, desktop: Boolean) {
        tab.desktop = desktop
        tab.webView?.let { view ->
            applyUserAgent(view, desktop)
            view.reload()
        }
    }

    /** The tab's WebView, created (and its page loaded) on first use. */
    fun webViewFor(tab: BrowserTab): WebView {
        tab.webView?.let { return it }
        val view = createWebView(tab)
        tab.webView = view
        if (tab.url.isNotEmpty()) view.loadUrl(tab.url)
        return view
    }

    fun onPause() {
        tabs.forEach { it.webView?.onPause() }
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        tabs.forEach { it.webView?.onResume() }
    }

    fun destroyAll() {
        tabs.forEach { tab -> tab.webView?.let(::destroy) }
        tabs.forEach { it.webView = null }
    }

    private fun destroy(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.webChromeClient = null
        view.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled") // Needed by the web; no JavaScript bridges are added.
    private fun createWebView(tab: BrowserTab): WebView {
        val view = WebView(activity)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
        }
        applyUserAgent(view, tab.desktop)
        CookieManager.getInstance().setAcceptCookie(true)
        // Third-party cookies follow people across sites; MesOS Browser blocks them.
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when (UrlPolicy.classify(url)) {
                    LinkAction.LOAD -> false
                    LinkAction.EXTERNAL -> {
                        // Only a tap may open another app; pages cannot do it on their own.
                        if (request.hasGesture()) host.openExternal(tab, url)
                        true
                    }
                    LinkAction.BLOCK -> {
                        MesOSLog.w(MesOSLog.SYSTEM, "Blocked navigation to ${UrlPolicy.schemeOf(url)}: link")
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.url = url
                tab.error = null
                tab.favicon = favicon
                tab.progress = 5
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.progress = 100
                tab.title = view.title.orEmpty()
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
                host.pageFinished(tab)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                tab.url = url
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
            }

            @SuppressLint("WebViewClientOnReceivedSslError") // Always cancelled, never proceeds.
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                tab.error = PageError.Insecure
                tab.progress = 100
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    tab.error = PageError.Network(error.description?.toString().orEmpty())
                    tab.progress = 100
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The page's renderer crashed or was killed; keep MesOS running and offer a reload.
                MesOSLog.w(MesOSLog.SYSTEM, "Browser renderer gone (crash=${detail.didCrash()})")
                if (tab.webView === view) tab.webView = null
                destroy(view)
                tab.error = PageError.Crashed
                return true
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                tab.favicon = icon
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // Camera and microphone for web pages are not offered in this version.
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean = host.chooseFiles(filePathCallback, fileChooserParams)

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                host.showCustomView(view) { callback?.onCustomViewHidden() }
            }

            override fun onHideCustomView() {
                host.showCustomView(null, null)
            }
        }

        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            host.download(url, userAgent, contentDisposition, mimeType)
        }
        return view
    }

    private fun applyUserAgent(view: WebView, desktop: Boolean) {
        val mobile = WebSettings.getDefaultUserAgent(activity)
        view.settings.userAgentString = if (desktop) desktopAgent(mobile) else mobile
        view.settings.useWideViewPort = true
    }

    private fun desktopAgent(mobile: String): String =
        mobile.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
}
