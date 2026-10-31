package com.gareca.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class WebStreamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutWebError: View
    private lateinit var tvWebErrorMessage: TextView
    private lateinit var btnWebBack: Button

    private val adBlockDomains = listOf(
        "popads", "propellerads", "adsterra", "exoclick", "admob", "doubleclick",
        "googlesyndication", "bet365", "1xbet", "adx", "yandex", "monetag", "trafficjunky"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_stream)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.webLoading)
        layoutWebError = findViewById(R.id.layoutWebError)
        tvWebErrorMessage = findViewById(R.id.tvWebErrorMessage)
        btnWebBack = findViewById(R.id.btnWebBack)

        btnWebBack.setOnClickListener {
            finish()
        }

        val url = intent.getStringExtra("EXTRA_URL") ?: ""
        if (url.isEmpty()) {
            finish()
            return
        }

        setupWebView(url)
        loadStream(url)
    }

    private val YOUTUBE_REGEX = Regex("(?:watch\\?v=|shorts/|youtu\\.be/|embed/|live/)([a-zA-Z0-9_-]{8,32})")
    private val YOUTUBE_CHANNEL_REGEX = Regex("(?:channel=|channel/)([a-zA-Z0-9_-]+)")
    private val YOUTUBE_LIST_REGEX = Regex("[?&]list=([a-zA-Z0-9_-]+)")

    private fun extractYouTubeEmbedSrc(url: String): String? {
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) return null

        val listMatch = YOUTUBE_LIST_REGEX.find(url)?.groupValues?.get(1)
        if (!listMatch.isNullOrBlank()) {
            return "https://www.youtube-nocookie.com/embed/videoseries?list=$listMatch&autoplay=1&controls=1&fs=1&rel=0&iv_load_policy=3&modestbranding=1&enablejsapi=1&origin=https://www.youtube-nocookie.com"
        }

        if (url.startsWith("live_stream?channel=")) {
            return "https://www.youtube-nocookie.com/embed/$url&autoplay=1&controls=1&fs=1&rel=0&iv_load_policy=3&modestbranding=1&enablejsapi=1&origin=https://www.youtube-nocookie.com"
        }

        if (url.contains("channel=") || url.contains("channel/")) {
            val chId = YOUTUBE_CHANNEL_REGEX.find(url)?.groupValues?.get(1)
            if (!chId.isNullOrBlank()) {
                return "https://www.youtube-nocookie.com/embed/live_stream?channel=$chId&autoplay=1&controls=1&fs=1&rel=0&iv_load_policy=3&modestbranding=1&enablejsapi=1&origin=https://www.youtube-nocookie.com"
            }
        }

        val ytId = YOUTUBE_REGEX.find(url)?.groupValues?.get(1)
        if (ytId != null) {
            val sep = if (ytId.contains("?")) "&" else "?"
            return "https://www.youtube-nocookie.com/embed/$ytId${sep}autoplay=1&controls=1&fs=1&rel=0&iv_load_policy=3&modestbranding=1&enablejsapi=1&origin=https://www.youtube-nocookie.com"
        }

        return null
    }

    private fun loadStream(url: String) {
        val embedSrc = extractYouTubeEmbedSrc(url)
        if (embedSrc != null) {
            val embedHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                  * { margin:0; padding:0; box-sizing:border-box; }
                  html, body { width:100vw; height:100vh; overflow:hidden; background:#000; }
                  #ytplayer { position:fixed; top:0; left:0; width:100vw !important; height:100vh !important; border:none; }
                </style>
                </head>
                <body>
                  <iframe id="ytplayer"
                    src="$embedSrc"
                    referrerpolicy="strict-origin-when-cross-origin"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                    allowfullscreen>
                  </iframe>
                  <script>
                    function sendYtCommand(func, args) {
                      var iframe = document.getElementById('ytplayer');
                      if (iframe && iframe.contentWindow) {
                        iframe.contentWindow.postMessage(JSON.stringify({
                          event: 'command',
                          func: func,
                          args: args || []
                        }), '*');
                      }
                    }

                    window.addEventListener('message', function(event) {
                      try {
                        var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                        if (data && (data.event === 'initialDelivery' || data.event === 'onReady')) {
                          sendYtCommand('playVideo');
                        }
                      } catch(e) {}
                    });

                    setTimeout(function() { sendYtCommand('playVideo'); }, 1000);
                    setTimeout(function() { sendYtCommand('playVideo'); }, 2500);
                    setTimeout(function() { sendYtCommand('playVideo'); }, 4500);
                  </script>
                </body>
                </html>
            """.trimIndent()

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                }
            }
            webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
            return
        }

        webView.loadUrl(url)
    }

    private fun injectUnmuteScript() {
        val js = """
            (function() {
                var media = document.querySelectorAll('video, audio');
                for (var i = 0; i < media.length; i++) {
                    try {
                        media[i].muted = false;
                        media[i].volume = 1.0;
                        media[i].defaultMuted = false;
                        if (media[i].paused) {
                            media[i].play().catch(function(){});
                        }
                    } catch(_) {}
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun setupWebView(url: String) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        val isYouTube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
        settings.userAgentString = if (isYouTube) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 12; SMART TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        } catch (_: Exception) {}

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 90) {
                    progressBar.visibility = View.GONE
                    injectUnmuteScript()
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString()?.lowercase() ?: ""
                val isYtAsset = reqUrl.contains("youtube.com") || reqUrl.contains("youtube-nocookie.com") || reqUrl.contains("googlevideo.com") || reqUrl.contains("ytimg.com")
                if (!isYtAsset) {
                    for (domain in adBlockDomains) {
                        if (reqUrl.contains(domain)) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true && !isYouTube) {
                    showWebError("No se pudo conectar con el sitio web externo (Error de conexión o bloqueo de red).")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true && !isYouTube) {
                    val code = errorResponse?.statusCode ?: 0
                    if (code in 400..599) {
                        showWebError("El servidor externo rechazó la conexión (Código HTTP $code).")
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectUnmuteScript()
                view?.postDelayed({ injectUnmuteScript() }, 1000)
                view?.postDelayed({ injectUnmuteScript() }, 2500)

                // Sniffer opcional: Si la página expone directamente un video HLS, lo enviamos a ExoPlayer
                view?.evaluateJavascript(
                    "(function(){ var v = document.querySelector('video'); return (v && v.src) ? v.src : ''; })();"
                ) { videoSrc ->
                    val cleanSrc = videoSrc?.replace("\"", "")?.trim().orEmpty()
                    if (cleanSrc.contains(".m3u8")) {
                        val intent = android.content.Intent(this@WebStreamActivity, PlayerActivity::class.java).apply {
                            putExtra("EXTRA_URL", cleanSrc)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun showWebError(message: String) {
        progressBar.visibility = View.GONE
        webView.visibility = View.GONE
        layoutWebError.visibility = View.VISIBLE
        tvWebErrorMessage.text = "$message\n\nTe recomendamos volver atrás y elegir una de las opciones [HD DIRECTO] que reproducen de forma garantizada."
        btnWebBack.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            } else {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
