package com.wayfare.ride.ui.payments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentInitOutcome
import com.wayfare.ride.domain.PaystackReturnMatcher
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaystackWebCheckoutSheet(
    checkout: PaymentInitOutcome,
    apiBaseUrl: String,
    onPaymentReturnDetected: (reference: String) -> Unit,
    onVerifyManually: (reference: String) -> Unit,
    onOpenInExternalBrowser: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val initialUrl = checkout.authorizationUrl?.trim().orEmpty()
    val hasValidInitialUrl = remember(initialUrl) { PaystackReturnMatcher.isSafeCheckoutUrl(initialUrl) }
    var currentUrl by remember(checkout.reference, initialUrl) { mutableStateOf(initialUrl) }
    var loadingProgress by remember(checkout.reference) { mutableIntStateOf(if (hasValidInitialUrl) 15 else 100) }
    var pageLoadError by remember(checkout.reference, initialUrl) {
        mutableStateOf(
            if (hasValidInitialUrl) null else "Invalid Paystack checkout link received. Try again or verify payment."
        )
    }
    var inlineWarning by remember(checkout.reference, initialUrl) { mutableStateOf<String?>(null) }
    var webViewRef by remember(checkout.reference) { mutableStateOf<WebView?>(null) }
    val completionDispatched = remember(checkout.reference, initialUrl) { AtomicBoolean(false) }

    fun dispatchCompletion(reference: String) {
        if (completionDispatched.compareAndSet(false, true)) {
            onPaymentReturnDetected(reference.ifBlank { checkout.reference })
        }
    }

    BackHandler {
        val webView = webViewRef
        if (pageLoadError == null && webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(checkout.reference, initialUrl) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .statusBarsPadding()
            .imePadding()
            .testTag("paystack_web_checkout_sheet")
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.93f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
                ) {
                    val isSecure = PaystackReturnMatcher.isSecureUrl(currentUrl)
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = if (isSecure) "Secure HTTPS checkout" else "Paystack checkout",
                        tint = if (isSecure) wayfareColors.positive else colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Paystack Checkout",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (checkout.amountMinor > 0L) {
                                MonoBadge(text = LaunchPolicy.formatZarMinor(checkout.amountMinor))
                            }
                        }
                        Text(
                            text = "Secure card payment (${PaystackReturnMatcher.extractDisplayHost(currentUrl)})",
                            fontFamily = WayfareMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasValidInitialUrl) {
                        IconButton(
                            onClick = onOpenInExternalBrowser,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("paystack_web_open_browser_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open checkout in browser"
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("paystack_web_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close checkout"
                        )
                    }
                }

                if (loadingProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                } else {
                    HorizontalDivider(color = colorScheme.outlineVariant)
                }

                inlineWarning?.let { warning ->
                    Surface(
                        color = colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paystack_web_inline_warning")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Embedded Paystack WebView + Error fallback
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(colorScheme.background)
                ) {
                    if (hasValidInitialUrl) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        javaScriptCanOpenWindowsAutomatically = true
                                        setSupportMultipleWindows(false)
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadingProgress = newProgress
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val targetUrl = request?.url?.toString() ?: return false
                                            return handleNavigationUrl(
                                                view = view,
                                                targetUrl = targetUrl,
                                                apiBaseUrl = apiBaseUrl,
                                                fallbackReference = checkout.reference,
                                                onReturnDetected = ::dispatchCompletion,
                                                onExternalLaunchError = { msg -> inlineWarning = msg }
                                            )
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            if (!url.isNullOrBlank()) {
                                                currentUrl = url
                                            }
                                            pageLoadError = null
                                            inlineWarning = null
                                            val matchedRef = PaystackReturnMatcher.extractCompletedReference(
                                                url = url,
                                                apiBaseUrl = apiBaseUrl,
                                                fallbackReference = checkout.reference
                                            )
                                            if (matchedRef != null) {
                                                view?.stopLoading()
                                                dispatchCompletion(matchedRef)
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            loadingProgress = 100
                                            if (!url.isNullOrBlank()) {
                                                currentUrl = url
                                            }
                                            val matchedRef = PaystackReturnMatcher.extractCompletedReference(
                                                url = url,
                                                apiBaseUrl = apiBaseUrl,
                                                fallbackReference = checkout.reference
                                            )
                                            if (matchedRef != null) {
                                                dispatchCompletion(matchedRef)
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            if (request?.isForMainFrame != true || completionDispatched.get()) return
                                            val failingUrl = request.url?.toString()
                                            val matchedRef = PaystackReturnMatcher.extractCompletedReference(
                                                url = failingUrl,
                                                apiBaseUrl = apiBaseUrl,
                                                fallbackReference = checkout.reference
                                            )
                                            if (matchedRef != null) {
                                                dispatchCompletion(matchedRef)
                                                return
                                            }
                                            val description = error?.description?.toString().orEmpty()
                                            if (description.contains("ERR_ABORTED", ignoreCase = true)) {
                                                return
                                            }
                                            loadingProgress = 100
                                            pageLoadError = friendlyWebErrorMessage(
                                                errorCode = error?.errorCode,
                                                description = description
                                            )
                                        }

                                        override fun onReceivedHttpError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            errorResponse: WebResourceResponse?
                                        ) {
                                            if (request?.isForMainFrame != true || completionDispatched.get()) return
                                            val failingUrl = request.url?.toString()
                                            val matchedRef = PaystackReturnMatcher.extractCompletedReference(
                                                url = failingUrl,
                                                apiBaseUrl = apiBaseUrl,
                                                fallbackReference = checkout.reference
                                            )
                                            if (matchedRef != null) {
                                                dispatchCompletion(matchedRef)
                                                return
                                            }
                                            val status = errorResponse?.statusCode ?: return
                                            if (status >= 400) {
                                                loadingProgress = 100
                                                pageLoadError = "Paystack checkout returned HTTP $status. Tap Retry or verify if you already paid."
                                            }
                                        }

                                        override fun onReceivedSslError(
                                            view: WebView?,
                                            handler: SslErrorHandler?,
                                            error: SslError?
                                        ) {
                                            handler?.cancel()
                                            if (!completionDispatched.get()) {
                                                loadingProgress = 100
                                                pageLoadError = "Secure HTTPS connection to Paystack could not be verified."
                                            }
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            webViewRef = null
                                            loadingProgress = 100
                                            if (!completionDispatched.get()) {
                                                pageLoadError = "Checkout view restarted unexpectedly. Tap Retry to reload."
                                            }
                                            return true
                                        }
                                    }

                                    webViewRef = this
                                    loadUrl(initialUrl)
                                }
                            },
                            update = { view ->
                                webViewRef = view
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("paystack_webview")
                        )
                    }

                    pageLoadError?.let { err ->
                        Surface(
                            color = colorScheme.surface,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("paystack_web_error_state")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Checkout could not load",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (hasValidInitialUrl) {
                                        Button(
                                            onClick = {
                                                pageLoadError = null
                                                loadingProgress = 15
                                                val web = webViewRef
                                                if (web != null) {
                                                    web.loadUrl(initialUrl)
                                                } else {
                                                    onDismiss()
                                                }
                                            },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("paystack_web_retry_btn")
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                        OutlinedButton(
                                            onClick = onOpenInExternalBrowser,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("paystack_web_error_browser_btn")
                                        ) {
                                            Text("Open in browser")
                                        }
                                    } else {
                                        Button(
                                            onClick = onDismiss,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("paystack_web_error_close_btn")
                                        ) {
                                            Text("Back to Vhaya")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Bar: Reference + Manual Verify Fallback
                HorizontalDivider(color = colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "You'll return automatically once paid",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Ref · ${checkout.reference}",
                            fontFamily = WayfareMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = {
                            if (completionDispatched.compareAndSet(false, true)) {
                                onVerifyManually(checkout.reference)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("paystack_web_verify_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("I've paid", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun friendlyWebErrorMessage(errorCode: Int?, description: String): String {
    return when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_IO -> "No internet connection to reach Paystack. Check your connection and tap Retry."
        WebViewClient.ERROR_TIMEOUT -> "Paystack checkout timed out. Tap Retry to reload."
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Secure connection to Paystack could not be established."
        else -> description.ifBlank { "Could not load Paystack checkout page. Tap Retry or open in browser." }
    }
}

private fun handleNavigationUrl(
    view: WebView?,
    targetUrl: String,
    apiBaseUrl: String,
    fallbackReference: String,
    onReturnDetected: (String) -> Unit,
    onExternalLaunchError: (String) -> Unit
): Boolean {
    val matchedRef = PaystackReturnMatcher.extractCompletedReference(
        url = targetUrl,
        apiBaseUrl = apiBaseUrl,
        fallbackReference = fallbackReference
    )
    if (matchedRef != null) {
        onReturnDetected(matchedRef)
        return true
    }

    val lower = targetUrl.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        return false
    }

    val context = view?.context ?: return true
    if (lower.startsWith("intent://")) {
        val handled = runCatching {
            val parsedIntent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
            val dataUri = parsedIntent.data?.toString()
            val intentRef = PaystackReturnMatcher.extractCompletedReference(
                url = dataUri,
                apiBaseUrl = apiBaseUrl,
                fallbackReference = fallbackReference
            )
            if (intentRef != null) {
                onReturnDetected(intentRef)
                return true
            }
            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
            if (!fallbackUrl.isNullOrBlank()) {
                val fallbackRef = PaystackReturnMatcher.extractCompletedReference(
                    url = fallbackUrl,
                    apiBaseUrl = apiBaseUrl,
                    fallbackReference = fallbackReference
                )
                if (fallbackRef != null) {
                    onReturnDetected(fallbackRef)
                } else if (PaystackReturnMatcher.isSafeCheckoutUrl(fallbackUrl)) {
                    view.loadUrl(fallbackUrl)
                }
                return true
            }
            context.startActivity(parsedIntent)
            true
        }.getOrDefault(false)
        if (!handled) {
            onExternalLaunchError("Could not open external payment app. Continue on this page or tap Open in browser.")
        }
        return true
    }

    val launched = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
        true
    }.getOrDefault(false)
    if (!launched) {
        onExternalLaunchError("No app installed to handle this payment link. Tap Open in browser or I've paid.")
    }
    return true
}
