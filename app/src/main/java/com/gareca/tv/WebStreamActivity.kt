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

        setupWebView()
        webView.loadUrl(url)
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 12; SMART TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 90) {
                    progressBar.visibility = View.GONE
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
                val isYouTube = reqUrl.contains("youtube.com") || reqUrl.contains("googlevideo.com") || reqUrl.contains("ytimg.com")
                if (!isYouTube) {
                    for (domain in adBlockDomains) {
                        if (reqUrl.contains(domain)) {
                            // Return empty response to drop ad
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
                if (request?.isForMainFrame == true) {
                    showWebError("No se pudo conectar con el sitio web externo (Error de conexión o bloqueo de red).")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    val code = errorResponse?.statusCode ?: 0
                    if (code in 400..599) {
                        showWebError("El servidor externo rechazó la conexión (Código HTTP $code).")
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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
