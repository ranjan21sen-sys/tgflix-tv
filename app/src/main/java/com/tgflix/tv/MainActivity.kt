package com.tgflix.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        // Change this to point the app at any other page
        const val START_URL = "https://tgflix.qzz.io/live-tv"
        // Desktop-ish UA makes the site render its big-screen layout. Set to null to use default.
        val USER_AGENT_SUFFIX: String? = " AFT TV TGFlixTV/1.0"
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBack = 0L

    // Makes focused items visible for remote use + makes clickable divs focusable
    private val tvJs = """
        (function(){
          if(window.__tvInit) return; window.__tvInit = true;
          var s = document.createElement('style');
          s.textContent = '*:focus{outline:4px solid #e50914 !important;outline-offset:2px !important;' +
                          'border-radius:6px;} html{scroll-behavior:smooth;} ::-webkit-scrollbar{display:none}';
          document.head.appendChild(s);
          var t = null;
          function mark(){
            document.querySelectorAll('div,span,li,img,svg').forEach(function(e){
              if(e.hasAttribute('tabindex')) return;
              if(e.onclick || getComputedStyle(e).cursor === 'pointer'){ e.setAttribute('tabindex','0'); }
            });
          }
          new MutationObserver(function(){ clearTimeout(t); t = setTimeout(mark, 400); })
            .observe(document.documentElement, {childList:true, subtree:true});
          mark();
          document.addEventListener('keydown', function(e){
            if(e.key === 'Enter' && document.activeElement && document.activeElement.tagName !== 'INPUT'){
              document.activeElement.click();
            }
          });
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(webView)
        setContentView(root)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            USER_AGENT_SUFFIX?.let { userAgentString = userAgentString + it }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(tvJs, null)
                view.requestFocus()
            }
            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(
                        null,
                        "<body style='background:#0a0a0a;color:#fff;font-family:sans-serif;" +
                        "display:flex;align-items:center;justify-content:center;height:100vh;" +
                        "text-align:center'><div><h1>No connection</h1>" +
                        "<p>Check your network, then press OK to retry.</p>" +
                        "<button autofocus onclick=\"location.href='$START_URL'\" " +
                        "style='font-size:28px;padding:14px 40px;background:#e50914;color:#fff;" +
                        "border:0;border-radius:8px'>Retry</button></div></body>",
                        "text/html", "utf-8", null
                    )
                }
            }
            // keep navigation inside the app
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                hideSystemUi()
            }
            override fun onHideCustomView() { exitCustomView() }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl(START_URL)
    }

    private fun exitCustomView() {
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.visibility = View.VISIBLE
        webView.requestFocus()
        hideSystemUi()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    // Remote: media keys control the <video> element; Back navigates history
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                when {
                    customView != null -> exitCustomView()
                    webView.canGoBack() -> webView.goBack()
                    System.currentTimeMillis() - lastBack < 2000 -> finish()
                    else -> {
                        lastBack = System.currentTimeMillis()
                        Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                js("var v=document.querySelector('video');if(v){v.paused?v.play():v.pause();}")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                { js("var v=document.querySelector('video');if(v)v.currentTime+=15;"); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND ->
                { js("var v=document.querySelector('video');if(v)v.currentTime-=15;"); return true }
            KeyEvent.KEYCODE_MENU -> { webView.reload(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun js(code: String) = webView.evaluateJavascript("(function(){$code})()", null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); webView.saveState(outState)
    }
    override fun onResume() { super.onResume(); webView.onResume(); hideSystemUi() }
    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemUi()
    }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
