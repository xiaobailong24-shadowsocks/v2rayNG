package com.v2ray.compose.core.config

import com.v2ray.compose.core.model.ProfileItem
import com.v2ray.compose.core.model.SubscriptionItem
import com.v2ray.compose.core.util.httpGetText

/** Fetches a subscription URL and parses its body into profiles. */
object SubscriptionUpdater {

    private const val DEFAULT_UA = "v2rayNG-Compose/1.0"

    suspend fun fetch(sub: SubscriptionItem): Result<List<ProfileItem>> {
        if (sub.url.isBlank()) return Result.failure(IllegalArgumentException("Empty subscription URL"))
        val ua = sub.userAgent.ifBlank { DEFAULT_UA }
        return httpGetText(sub.url, ua).map { body ->
            SubscriptionParser.parse(body, subscriptionId = sub.id)
        }
    }
}
