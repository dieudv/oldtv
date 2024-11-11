package org.lzdev.oldtv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import org.intellij.lang.annotations.Language

class MainActivity : Activity() {
    private val channels: ArrayList<Channel> = ArrayList()
    private var index = 0
    private lateinit var webView: WebView
    private var currentVideoURL = ""
    private lateinit var surfaceView: SurfaceView
    private var isChannelLoaded = false
    private var mediaPlayer: MediaPlayer? = null

    private var mWifiReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isNetworkConnected()) {
                webView.loadUrl("https://vtvgo.vn")
            }
        }
    }

    @Language("js")
    private val jsCode = """
        var elements = document.querySelectorAll(".list_channel>a");
        var channels_text = "";

        elements.forEach(element => {
            channels_text += element.getAttribute("alt") + ",";
            channels_text += element.getAttribute("href") + "|";
        });

        channels_text = channels_text.slice(0, channels_text.length - 1);
        channels_text;
    """.trimIndent()

    private val mOnErrorListener: MediaPlayer.OnErrorListener = MediaPlayer.OnErrorListener { _, _, _ ->
        if (isNetworkConnected()) {
            mediaPlayer?.start()
        }
        true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surface_view)

        WebView.setWebContentsDebuggingEnabled(true)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = getString(R.string.user_agent)
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                runOnUiThread {
                    request?.let {
                        val url = it.url.toString()
                        if (url.contains(".m3u8") && currentVideoURL != url) {
                            currentVideoURL = url
                            val headers = it.requestHeaders // Capture all request headers
                            playVideoWithHeaders(url, headers)
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isChannelLoaded) {
                    view?.evaluateJavascript(jsCode) {
                        if (it != null && it.contains("http")) {
                            val text = it.replace("\"", "")
                            channels.clear()
                            val channelsText = text.split("|")
                            for (line in channelsText) {
                                channels.add(Channel(line.split(",")[0], line.split(",")[1]))
                            }
                            isChannelLoaded = true
                        }
                    }
                }
                super.onPageFinished(view, url)
            }
        }
        webView.loadUrl("https://vtvgo.vn")

        registerWifiReceiver()

        if (isNetworkConnected()) {
            webView.loadUrl("https://vtvgo.vn")
        }
    }

    private fun playVideoWithHeaders(url: String, headers: Map<String, String>) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, Uri.parse(url), headers)
            setDisplay(surfaceView.holder)
            setOnPreparedListener { start() }
            setOnErrorListener(mOnErrorListener)
            prepareAsync()
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                cm.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    }

    private fun registerWifiReceiver() {
        val filter = IntentFilter()
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(mWifiReceiver, filter)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (index < channels.size - 1) {
                    index++
                } else {
                    index = 0
                }
                return playChannel()
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (index > 0) {
                    index--
                } else {
                    index = channels.size - 1
                }
                return playChannel()
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                val adapter = ArrayAdapter(this, android.R.layout.select_dialog_item, channels.map { it.name })
                builder.setSingleChoiceItems(adapter, index) { dialog, which ->
                    index = which
                    playChannel()
                    dialog.dismiss()
                }
                builder.show()
            }

            KeyEvent.KEYCODE_MENU -> {
                startActivityForResult(Intent(android.provider.Settings.ACTION_SETTINGS), 0)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun playChannel(): Boolean {
        if (index >= channels.size) {
            index = 0
        }
        if (channels.isEmpty()) {
            webView.loadUrl("https://vtvgo.vn")
        } else {
            webView.loadUrl(channels[index].url)
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.start()
    }

    companion object {
        private const val TAG = "OLDTV_MainActivity"
    }
}

class Channel(val name: String, val url: String)