package io.sfere.analytics.internal

import java.net.URI

/** A validated ingestion origin. Uploads go to `{scheme}://{authority}/v1/batch/b`. */
internal data class SfereHost(val scheme: String, val authority: String) {
    /** The base library's `apiHost`: no scheme. It appends `/b` itself. */
    val apiHost: String get() = "$authority/v1/batch"
}

internal sealed class HostValidation {
    data class Valid(val host: SfereHost) : HostValidation()
    data class Invalid(val reason: String) : HostValidation()
}

internal fun validateHost(host: String?, allowInsecure: Boolean): HostValidation {
    if (host.isNullOrBlank()) {
        return HostValidation.Invalid("\"host\" is required, e.g. \"https://ingest.example.com\".")
    }
    val uri = try {
        URI(host.trim())
    } catch (e: Exception) {
        return HostValidation.Invalid("\"host\" is not a valid URL: \"$host\".")
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
        return HostValidation.Invalid("\"host\" must start with https://, got \"$host\".")
    }
    if (scheme == "http" && !allowInsecure) {
        return HostValidation.Invalid("\"host\" uses http://, which is allowed only in debug builds. Use https://.")
    }
    val authority = uri.rawAuthority
    if (uri.host.isNullOrEmpty() || authority.isNullOrEmpty() || uri.rawUserInfo != null) {
        return HostValidation.Invalid("\"host\" must be an origin like \"https://ingest.example.com\", got \"$host\".")
    }
    val path = uri.rawPath.orEmpty()
    if ((path.isNotEmpty() && path != "/") || uri.rawQuery != null || uri.rawFragment != null) {
        return HostValidation.Invalid("\"host\" must be an origin without a path, got \"$host\". Use \"$scheme://$authority\".")
    }
    return HostValidation.Valid(SfereHost(scheme, authority))
}
