package com.fastdm.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fastdm.app.R
import com.fastdm.app.core.AdBlocker
import com.fastdm.app.core.DownloadRequest
import com.fastdm.app.core.Settings
import com.fastdm.app.core.normalizeInput
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class BrowserTab(val id: Int, val webView: WebView) {
    var title by mutableStateOf("")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    val blockedCounter = AtomicInteger(0)
}

/** Holds every browser tab (one WebView each) so they survive switching between app screens. */
class BrowserState(private val context: Context, private val settings: Settings) {

    val tabs = mutableStateListOf<BrowserTab>()
    var selected by mutableIntStateOf(0)
    var desktopMode by mutableStateOf(false)
    var customView by mutableStateOf<View?>(null)

    var onDownload: (DownloadRequest) -> Unit = {}
    var onLinkLongPress: (String) -> Unit = {}
    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var launchChooser: ((Intent) -> Unit)? = null

    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var nextId = 1

    val current: BrowserTab get() = tabs[selected]

    init {
        newTab(settings.homepage.value)
    }

    fun newTab(url: String?, select: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextId++, WebView(context))
        configure(tab)
        tabs.add(tab)
        if (select) selected = tabs.lastIndex
        if (!url.isNullOrBlank()) tab.webView.loadUrl(normalizeInput(url))
        return tab
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val t = tabs.removeAt(index)
        (t.webView.parent as? ViewGroup)?.removeView(t.webView)
        t.webView.destroy()
        if (tabs.isEmpty()) {
            newTab(settings.homepage.value)
        } else {
            if (index < selected) selected -= 1
            selected = selected.coerceIn(0, tabs.lastIndex)
        }
    }

    fun openUrl(url: String) {
        current.webView.loadUrl(normalizeInput(url))
    }

    fun toggleDesktop() {
        desktopMode = !desktopMode
        tabs.forEach { applyUserAgent(it.webView) }
        current.webView.reload()
    }

    fun hideCustomView() {
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    fun destroy() {
        tabs.forEach {
            (it.webView.parent as? ViewGroup)?.removeView(it.webView)
            it.webView.destroy()
        }
        tabs.clear()
    }

    private fun applyUserAgent(wv: WebView) {
        val def = WebSettings.getDefaultUserAgent(context)
        wv.settings.userAgentString = if (desktopMode)
            def.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
        else null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(tab: BrowserTab) {
        val wv = tab.webView
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
        }
        applyUserAgent(wv)
        CookieManager.getInstance().setAcceptCookie(true)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (settings.adBlock.value && !request.isForMainFrame && AdBlocker.isBlocked(request.url.toString())) {
                    tab.blockedCounter.incrementAndGet()
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return false
                if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob") return false
                if (request.hasGesture()) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                tab.url = url ?: ""
                tab.blockedCounter.set(0)
                tab.blocked = 0
                updateNav(tab, view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.blocked = tab.blockedCounter.get()
                updateNav(tab, view)
                if (settings.adBlock.value) view.evaluateJavascript(COSMETIC_JS, null)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null) tab.url = url
                updateNav(tab, view)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    val u = request.url.toString()
                    view.loadDataWithBaseURL(u, errorHtml(error.description?.toString() ?: ""), "text/html", "utf-8", u)
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title ?: ""
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                // Pop-ups that were not triggered by a tap are blocked.
                if (!isUserGesture || resultMsg == null) return false
                val nt = newTab(null)
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = nt.webView
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val launch = launchChooser
                if (launch == null || params == null) {
                    callback?.onReceiveValue(null)
                    filePathCallback = null
                    return true
                }
                return try {
                    launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    callback?.onReceiveValue(null)
                    filePathCallback = null
                    true
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        wv.setDownloadListener { url, ua, cd, mime, len ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(context, R.string.msg_blob, Toast.LENGTH_LONG).show()
            } else {
                onDownload(
                    DownloadRequest(
                        url = url,
                        fileName = URLUtil.guessFileName(url, cd, mime),
                        mime = mime ?: "",
                        size = len,
                        userAgent = ua ?: wv.settings.userAgentString,
                        referer = tab.url,
                        cookie = CookieManager.getInstance().getCookie(url) ?: ""
                    )
                )
            }
        }

        val handler = Handler(Looper.getMainLooper()) { m ->
            m.data.getString("url")?.let { onLinkLongPress(it) }
            true
        }
        wv.setOnLongClickListener {
            val r = wv.hitTestResult
            when (r.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    wv.requestFocusNodeHref(handler.obtainMessage())
                    true
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    r.extra?.let { onLinkLongPress(it) }
                    r.extra != null
                }
                else -> false
            }
        }
    }

    private fun updateNav(tab: BrowserTab, view: WebView) {
        tab.canBack = view.canGoBack()
        tab.canForward = view.canGoForward()
    }

    private fun errorHtml(message: String): String {
        val title = context.getString(R.string.err_page_title)
        val safe = message.replace("<", "&lt;").replace(">", "&gt;")
        return """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="font-family:sans-serif;background:#0B0F1A;color:#E8ECF8;display:flex;flex-direction:column;
            align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:24px">
            <div style="font-size:56px">&#9888;</div>
            <h2 style="margin:12px 0 6px">$title</h2>
            <p style="color:#9AA6C4;margin:0">$safe</p>
            </body></html>
        """.trimIndent()
    }

    companion object {
        private const val COSMETIC_JS = """
            (function(){
              if(document.getElementById('__fdm_css')) return;
              var s=document.createElement('style'); s.id='__fdm_css';
              s.textContent='ins.adsbygoogle,.adsbygoogle,iframe[src*="doubleclick.net"],iframe[src*="googlesyndication"],[id^="google_ads_"],[id^="div-gpt-ad"],.ad-container,.ad-banner,.advert{display:none!important}';
              (document.head||document.documentElement).appendChild(s);
            })();
        """
    }
}
