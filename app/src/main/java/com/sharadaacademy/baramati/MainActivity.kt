package com.sharadaacademy.baramati

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader
import com.sharadaacademy.baramati.ui.theme.SharadaAcademyTheme
import com.sharadaacademy.baramati.ui.theme.BgSlate
import com.sharadaacademy.baramati.ui.theme.PinkLight
import com.sharadaacademy.baramati.ui.theme.PinkPrimary
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize Splash Screen (standard behavior)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val lastUrl = savedInstanceState?.getString("WEB_URL")

        setContent {
            SharadaAcademyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebAppContent(
                        initialUrl = lastUrl,
                        onWebViewCreated = { webViewRef = it }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViewRef?.url?.let { outState.putString("WEB_URL", it) }
    }

    override fun onDestroy() {
        webViewRef?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.removeAllViews()
            wv.destroy()
        }
        webViewRef = null
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppContent(
    initialUrl: String? = null,
    onWebViewCreated: (WebView) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var internalWebView by remember { mutableStateOf<WebView?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required for OMR scanning", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        delay(500)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    BackHandler(enabled = true) {
        if (internalWebView?.canGoBack() == true) {
            internalWebView?.goBack()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(text = "Exit App") },
            text = { Text(text = "Are you sure you want to quit Sharada Academy?") },
            confirmButton = {
                Button(
                    onClick = {
                        (context as? android.app.Activity)?.finish()
                        exitProcess(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("No", color = Color.Gray)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSlate)
            .systemBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Use reflection for BuildConfig check to avoid compile-time issues if not synced
                try {
                    val buildConfigClass = Class.forName("${ctx.packageName}.BuildConfig")
                    val debugField = buildConfigClass.getField("DEBUG")
                    if (debugField.get(null) as Boolean) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
                } catch (e: Exception) {
                    // Fallback: disabled for safety
                    WebView.setWebContentsDebuggingEnabled(false)
                }

                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    overScrollMode = android.view.View.OVER_SCROLL_NEVER

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        mediaPlaybackRequiresUserGesture = false
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            hasError = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            handler?.cancel()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                isLoading = false
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url.toString()
                            
                            if (url.lowercase().contains(".pdf")) {
                                if (url.startsWith("https://appassets.androidplatform.net/assets/")) {
                                    val assetPath = url.substring("https://appassets.androidplatform.net/assets/".length)
                                    val cleanPath = assetPath.split("?")[0].split("#")[0]
                                    openAssetPdf(context, cleanPath)
                                    return true
                                } else if (url.startsWith("https://") || url.startsWith("http://")) {
                                    val googleDocsUrl = "https://docs.google.com/viewer?embedded=true&url=" + Uri.encode(url)
                                    view?.loadUrl(googleDocsUrl)
                                    return true
                                }
                            }

                            if (url.startsWith("https://appassets.androidplatform.net/")) {
                                return false
                            } else if (url.startsWith("https://") || url.startsWith("http://")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                    return true
                                } catch (_: Exception) {
                                    return false
                                }
                            }
                        }
                    }

                    val startUrl = initialUrl ?: "https://appassets.androidplatform.net/assets/index.html"
                    loadUrl(startUrl)
                    
                    internalWebView = this
                    onWebViewCreated(this)
                }
            },
            update = { /* No updates needed */ }
        )

        if (isLoading && !hasError) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = PinkPrimary,
                trackColor = PinkLight,
            )
        }

        if (hasError) {
            ErrorScreen(
                modifier = Modifier.fillMaxSize(),
                onRetry = {
                    hasError = false
                    isLoading = true
                    internalWebView?.reload()
                }
            )
        }
    }
}

fun openAssetPdf(context: Context, assetPath: String) {
    try {
        val decodedPath = Uri.decode(assetPath)
        
        var inputStream = try {
            context.assets.open(decodedPath)
        } catch (e: Exception) {
            null
        }

        if (inputStream == null) {
            val parts = decodedPath.split("/")
            if (parts.isNotEmpty()) {
                val firstPart = parts[0]
                val alternativeFirstPart = if (firstPart[0].isUpperCase()) firstPart.lowercase() else firstPart.replaceFirstChar { it.uppercase() }
                val alternativePath = (listOf(alternativeFirstPart) + parts.drop(1)).joinToString("/")
                try {
                    inputStream = context.assets.open(alternativePath)
                } catch (e: Exception) { }
            }
        }

        if (inputStream != null) {
            val fileName = File(decodedPath).name
            val outFile = File(context.cacheDir, fileName)
            
            FileOutputStream(outFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            
            context.startActivity(Intent.createChooser(intent, "Open PDF with..."))
        } else {
            Toast.makeText(context, "File not found in assets: $decodedPath", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ErrorScreen(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Failed to load page")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text(text = "Retry")
        }
    }
}
