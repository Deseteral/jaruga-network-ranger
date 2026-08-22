package xyz.deseteral.jaruga.ranger.trail.config

import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import kotlin.time.Duration

data class LookoutConfig(
    val campground: Campground,
    val bufferSize: Int,
    val notifier: Notifier,
) {
    data class Campground(
        val instanceUrl: String,
        val interval: Duration,
    )

    data class Notifier(
        val process: (incident: Incident) -> NotificationChannel,
    )

    sealed class NotificationChannel {
        data class DiscordWebhook(
            // TODO: Validate URL can parse and are correct.
            val url: String,
        ) : NotificationChannel()
    }
}
