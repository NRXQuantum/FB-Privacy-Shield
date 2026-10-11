package com.fbprivacy.shield

import android.content.Context
import org.json.JSONObject

class EndpointBlocklist(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    val defaultBlockedEndpoints = listOf(
        "graph.facebook.com/logging_client_events",
        "graph.facebook.com/ajax/bz",
        "b-www.facebook.com/mobile/extra_data_collector",
        "b-www.facebook.com/mobile/parfait_trace_upload",
        "lookaside.facebook.com/ras/v2",
        "m.facebook.com/async/wbloks/log",
        "graph.facebook.com/mobile_crash_reports",
        "graph.facebook.com/dsp",
        "graph.facebook.com/ad_tracking",
        "graph.facebook.com/signal_collection"
    )

    val defaultAllowedDomains = listOf(
        "graph.facebook.com/graphql",
        "graph.facebook.com/graphqlbatch",
        "mqtt.facebook.com",
        "edge-mqtt.facebook.com",
        "*.fbcdn.net",
        "*.fbsbx.com",
        "*.whatsapp.net",
        "*.instagram.com"
    )

    val blockedTcpPorts = setOf(12387, 12388)
    val blockedUdpPorts = setOf(12580, 12581, 12582, 12583, 12584, 12585)

    private var customBlockedList: Set<String> = emptySet()

    fun updateCustomRules(customRules: Set<String>) {
        this.customBlockedList = customRules
    }

    fun isDomainBlocked(host: String): Boolean {
        val cleanHost = host.trim().lowercase().removeSuffix(".")

        for (custom in customBlockedList) {
            if (cleanHost == custom || cleanHost.endsWith(".$custom") || cleanHost.contains(custom)) {
                return true
            }
        }

        // Check if explicitly blocked endpoint keywords or domains match
        for (blocked in defaultBlockedEndpoints) {
            val domainPart = blocked.substringBefore("/").lowercase()
            val pathPart = if (blocked.contains("/")) blocked.substringAfter("/") else ""
            if (domainPart.isNotEmpty() && cleanHost == domainPart) {
                // If it's a dedicated tracker subdomain like b-www or lookaside or specialized
                if (domainPart.startsWith("b-www.") || domainPart.startsWith("pixel.") || domainPart.startsWith("an.")) {
                    return true
                }
            }
        }

        val directBlockedKeywords = listOf(
            "logging_client_events",
            "extra_data_collector",
            "parfait_trace_upload",
            "mobile_crash_reports",
            "signal_collection",
            "ad_tracking",
            "pixel.facebook.com",
            "an.facebook.com",
            "events.facebook.com"
        )
        for (kw in directBlockedKeywords) {
            if (cleanHost.contains(kw)) {
                return true
            }
        }

        return false
    }

    fun isAllowedDomain(host: String): Boolean {
        val cleanHost = host.trim().lowercase().removeSuffix(".")
        for (allowed in defaultAllowedDomains) {
            val domainOnly = allowed.substringBefore("/").lowercase()
            if (domainOnly.startsWith("*.")) {
                val suffix = domainOnly.removePrefix("*")
                if (cleanHost.endsWith(suffix) || cleanHost == suffix.removePrefix(".")) {
                    return true
                }
            } else if (cleanHost == domainOnly) {
                return true
            }
        }
        return false
    }

    fun shouldBlockUrlOrPayload(urlOrPath: String): Boolean {
        val lower = urlOrPath.lowercase()
        // Allowed paths take precedence
        if (lower.contains("graph.facebook.com/graphql") || lower.contains("/graphql") || lower.contains("/graphqlbatch")) {
            return false
        }
        for (blocked in defaultBlockedEndpoints) {
            val pathPart = if (blocked.contains("/")) blocked.substringAfter("/") else blocked
            if (pathPart.isNotEmpty() && lower.contains(pathPart)) {
                return true
            }
        }
        for (custom in customBlockedList) {
            if (lower.contains(custom)) {
                return true
            }
        }
        return false
    }

    fun isBlockedPort(port: Int, isTcp: Boolean): Boolean {
        return if (isTcp) {
            blockedTcpPorts.contains(port)
        } else {
            blockedUdpPorts.contains(port)
        }
    }
}
