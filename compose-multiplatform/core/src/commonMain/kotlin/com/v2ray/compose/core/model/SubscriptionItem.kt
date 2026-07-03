package com.v2ray.compose.core.model

import kotlinx.serialization.Serializable

/** A remote subscription that yields a list of [ProfileItem]s when updated. */
@Serializable
data class SubscriptionItem(
    val id: String,
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    var lastUpdated: Long = 0L,
    var autoUpdate: Boolean = false,
    /** User-Agent to send when fetching the subscription (some providers gate on it). */
    var userAgent: String = "",
)
