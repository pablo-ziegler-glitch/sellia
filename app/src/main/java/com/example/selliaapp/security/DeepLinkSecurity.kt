package com.example.selliaapp.security

import android.content.Intent
import android.net.Uri
import android.util.Log

object DeepLinkSecurity {
    private const val TAG = "DeepLinkSecurity"

    private val allowedSchemes = setOf("https", "sellia")
    private val allowedHttpsHosts = setOf("sellia1993.web.app", "sellia1993.firebaseapp.com")
    private const val customSchemeHost = "product"
    private const val productPath = "/product"
    private val allowedQueryParams = setOf("q", "sku", "tenantId")

    private val qrRegex = Regex("^[A-Za-z0-9._:@/+\\-]{1,120}$")
    private val skuRegex = Regex("^[A-Za-z0-9._\\-]{1,64}$")
    private val tenantRegex = Regex("^[A-Za-z0-9_\\-]{3,64}$")

    fun sanitizeIncomingIntent(intent: Intent?): Intent? {
        intent ?: return null
        if (intent.action != Intent.ACTION_VIEW) return intent

        val data = intent.data ?: return intent
        val validation = validateUri(data)

        if (!validation.isValid || validation.sanitizedUri == null) {
            logInvalidIntent(
                reason = validation.reason ?: "invalid_deep_link",
                originalUri = data.toString()
            )
            return Intent(intent).apply { setData(null) }
        }

        return Intent(intent).apply { setData(validation.sanitizedUri) }
    }

    fun isSafeQr(rawValue: String?): Boolean {
        val value = rawValue?.trim().orEmpty()
        return value.isNotEmpty() && qrRegex.matches(value)
    }

    fun logInvalidIntent(reason: String, originalUri: String?) {
        Log.w(TAG, "invalid_intent reason=$reason uri=${originalUri.orEmpty().take(300)}")
    }

    private fun validateUri(uri: Uri): ValidationResult {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme !in allowedSchemes) {
            return ValidationResult(false, null, "scheme_not_allowed")
        }

        val host = uri.host?.lowercase().orEmpty()
        if (scheme == "https") {
            if (host !in allowedHttpsHosts) {
                return ValidationResult(false, null, "host_not_allowed")
            }
            val path = uri.path.orEmpty()
            if (!(path == productPath || path.startsWith("$productPath/"))) {
                return ValidationResult(false, null, "route_not_allowlisted")
            }
        }

        if (scheme == "sellia" && host != customSchemeHost) {
            return ValidationResult(false, null, "custom_host_not_allowed")
        }

        val queryParameterNames = uri.queryParameterNames
        if (queryParameterNames.any { it !in allowedQueryParams }) {
            return ValidationResult(false, null, "unexpected_query_param")
        }

        val sanitizedQ = uri.getQueryParameter("q")?.trim().orEmpty()
        if (!qrRegex.matches(sanitizedQ)) {
            return ValidationResult(false, null, "invalid_q")
        }

        uri.getQueryParameter("sku")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (!skuRegex.matches(it)) return ValidationResult(false, null, "invalid_sku")
        }

        uri.getQueryParameter("tenantId")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (!tenantRegex.matches(it)) return ValidationResult(false, null, "invalid_tenant_id")
        }

        val builder = Uri.Builder()
            .scheme(scheme)
            .authority(host)

        if (scheme == "https") {
            builder.path(productPath)
        }

        builder.appendQueryParameter("q", sanitizedQ)
        uri.getQueryParameter("sku")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.appendQueryParameter("sku", it)
        }
        uri.getQueryParameter("tenantId")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.appendQueryParameter("tenantId", it)
        }

        return ValidationResult(
            isValid = true,
            sanitizedUri = builder.build(),
            reason = null
        )
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val sanitizedUri: Uri?,
        val reason: String?
    )
}
