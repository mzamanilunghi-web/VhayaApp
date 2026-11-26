package com.wayfare.ride.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object PaystackReturnMatcher {

    fun extractCompletedReference(
        url: String?,
        apiBaseUrl: String,
        fallbackReference: String
    ): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        val params = parseQueryParams(uri.rawQuery)
        val refParam = params["reference"]?.takeIf { it.isNotBlank() }
            ?: params["trxref"]?.takeIf { it.isNotBlank() }

        if (scheme == "vhaya" && host == "payment-return") {
            return refParam ?: fallbackReference.takeIf { it.isNotBlank() }
        }

        if (scheme != "http" && scheme != "https") {
            return null
        }

        if (isPaystackHostedDomain(host)) {
            return null
        }

        val apiHost = runCatching { URI(apiBaseUrl.trim()).host?.lowercase().orEmpty() }.getOrDefault("")
        val isBackendHost = apiHost.isNotBlank() && host == apiHost
        val isReturnPath = path.contains("payment-return") ||
            path.contains("payment/return") ||
            path.contains("paystack/callback") ||
            path.contains("paystack/return") ||
            path.contains("payment/callback")

        if (isBackendHost && (isReturnPath || refParam != null)) {
            return refParam ?: fallbackReference.takeIf { it.isNotBlank() }
        }

        if (isReturnPath) {
            return refParam ?: fallbackReference.takeIf { it.isNotBlank() }
        }

        val hasBothPaystackParams = !params["reference"].isNullOrBlank() && !params["trxref"].isNullOrBlank()
        if (hasBothPaystackParams) {
            return params["reference"]
        }

        return null
    }

    fun extractDisplayHost(url: String?): String {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return "checkout.paystack.com"
        return runCatching { URI(raw).host?.takeIf { it.isNotBlank() } }.getOrNull() ?: "checkout.paystack.com"
    }

    fun isSecureUrl(url: String?): Boolean {
        return url?.trim()?.lowercase()?.startsWith("https://") == true
    }

    fun isSafeCheckoutUrl(url: String?): Boolean {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        return (scheme == "https" || scheme == "http") && host.isNotBlank()
    }

    private fun isPaystackHostedDomain(host: String): Boolean {
        return host == "paystack.com" ||
            host.endsWith(".paystack.com") ||
            host == "paystack.co" ||
            host.endsWith(".paystack.co")
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            val key = decodePart(if (idx >= 0) pair.substring(0, idx) else pair)
            val value = if (idx >= 0) decodePart(pair.substring(idx + 1)) else ""
            if (key.isNotBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun decodePart(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
