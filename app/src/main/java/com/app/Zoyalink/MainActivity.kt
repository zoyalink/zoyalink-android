package com.app.zoyalink

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.io.File
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    private val CHANNEL_ID = "zoyalink_channel"

    // =====================================================
    // ON CREATE
    // =====================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotifyChannel()

        swipeRefresh = SwipeRefreshLayout(this)
        webView = WebView(this)
        swipeRefresh.addView(webView)
        setContentView(swipeRefresh)

        // ===============================
        // STATUS BAR (UNGU – TIDAK HITAM)
        // ===============================
        window.statusBarColor = Color.parseColor("#542385")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        swipeRefresh.setColorSchemeColors(Color.parseColor("#542385"))
        swipeRefresh.setOnRefreshListener {
            if (!webView.url.orEmpty().contains("/chat/")) {
                webView.reload()
            }
            swipeRefresh.isRefreshing = false
        }

        // ===============================
        // WEBVIEW SETTINGS
        // ===============================
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString += " vinebre"
        }

        requestRuntimePermissions()

        // ===============================
        // WEBVIEW CLIENT
        // ===============================
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false

                // zoyalink.com tetap di WebView
                if (uri.host == "zoyalink.com") return false

                // link luar → browser
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
                swipeRefresh.isEnabled = !(url?.contains("/chat/") ?: false)
            }
        }

        webView.webChromeClient = fileChooserChromeClient()

        // deeplink pertama
        handleIntent(intent)

        checkUpdate()
        autoCheckPush()
    }

    // =====================================================
    // DEEPLINK
    // =====================================================
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: run {
            webView.loadUrl("https://zoyalink.com/")
            return
        }

        if (data.scheme == "zoyalink" && data.host == "open") {
            val target = data.getQueryParameter("url")
            webView.loadUrl(target ?: "https://zoyalink.com/")
            return
        }

        if (data.scheme == "https" && data.host == "zoyalink.com") {
            webView.loadUrl(data.toString())
            return
        }

        webView.loadUrl("https://zoyalink.com/")
    }

    // =====================================================
    // FILE CHOOSER
    // =====================================================
    private fun fileChooserChromeClient(): WebChromeClient {
        return object : WebChromeClient() {

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {

                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val accept = params?.acceptTypes?.joinToString(",") ?: ""
                val wantsVideo = accept.contains("video")
                val wantsImage = accept.contains("image") || accept.isEmpty()

                val extraIntents = ArrayList<Intent>()

                if (wantsImage) {
                    val photoFile = File.createTempFile("zoyalink_img_", ".jpg", cacheDir)
                    cameraPhotoUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.provider",
                        photoFile
                    )
                    val cam = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                    extraIntents.add(cam)
                }

                if (wantsVideo) {
                    extraIntents.add(Intent(MediaStore.ACTION_VIDEO_CAPTURE))
                }

                val gallery = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = when {
                        wantsVideo && wantsImage -> "*/*"
                        wantsVideo -> "video/*"
                        else -> "image/*"
                    }
                }

                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, gallery)
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toTypedArray())
                }

                return try {
                    fileChooserActivity.launch(chooser)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }
    }

    private val fileChooserActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val cb = fileChooserCallback ?: return@registerForActivityResult
            var results: Array<Uri>? = null

            if (res.resultCode == RESULT_OK && cameraPhotoUri != null) {
                results = arrayOf(cameraPhotoUri!!)
                cameraPhotoUri = null
            }

            res.data?.let { data ->
                data.clipData?.let {
                    results = Array(it.itemCount) { i -> it.getItemAt(i).uri }
                } ?: data.data?.let {
                    results = arrayOf(it)
                }
            }

            cb.onReceiveValue(results)
            fileChooserCallback = null
        }

    // =====================================================
    // PERMISSIONS
    // =====================================================
    private fun requestRuntimePermissions() {
        val p = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.READ_MEDIA_IMAGES)
            p.add(Manifest.permission.READ_MEDIA_VIDEO)
            p.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            p.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}.launch(p.toTypedArray())
    }

    // =====================================================
    // NOTIFICATION
    // =====================================================
    private fun createNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zoyalink Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun autoCheckPush() {
        Thread {
            while (true) {
                try {
                    val o = JSONObject(
                        URL("https://zoyalink.com/backend/check-push.php").readText()
                    )
                    if (o.optBoolean("send")) {
                        sendPush(
                            o.optString("title") ?: "",
                            o.optString("body") ?: "",
                            o.optString("icon") ?: "",
                            o.optString("url") ?: ""
                        )
                    }
                } catch (_: Exception) {}
                Thread.sleep(10_000)
            }
        }.start()
    }

    private fun sendPush(title: String, body: String, iconUrl: String, url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pending = PendingIntent.getActivity(
            this, 999, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notif)
    }

    // =====================================================
    // VERSION CHECK (FIX PALSU UPDATE)
    // =====================================================
    private fun getCurrentVersion(): String {
    val info = packageManager.getPackageInfo(packageName, 0)
    return info.versionName?.trim() ?: "0.0.0"
}
    private fun isNewerVersion(latest: String?, current: String?): Boolean {

    val lParts = (latest ?: "").split(".")
    val cParts = (current ?: "").split(".")

    val max = maxOf(lParts.size, cParts.size)

    for (i in 0 until max) {
        val lv = lParts.getOrNull(i)?.toIntOrNull() ?: 0
        val cv = cParts.getOrNull(i)?.toIntOrNull() ?: 0

        if (lv > cv) return true
        if (lv < cv) return false
    }
    return false
}

    private fun checkUpdate() {
    Thread {
        try {
            val jsonText = URL("https://zoyalink.com/app/version.json").readText()
            val o = JSONObject(jsonText)

            // ⛔ PAKAI getString (NON NULL)
            val latest: String = o.getString("latest_version").trim()
            val notes: String  = o.getString("notes")
            val apk: String    = o.getString("apk_url")

            val current = getCurrentVersion()

            if (isNewerVersion(latest, current)) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Update $latest")
                        .setMessage(notes)
                        .setCancelable(false)
                        .setPositiveButton("UPDATE") { _, _ ->
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(apk))
                            )
                        }
                        .show()
                }
            }
        } catch (e: Exception) {
            // diamkan → tidak crash
        }
    }.start()
}

    // =====================================================
    // BACK
    // =====================================================
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Exit Zoyalink?")
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }
}