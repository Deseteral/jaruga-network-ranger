package xyz.deseteral.jaruga.lookout.configuration

import xyz.deseteral.jaruga.network.ranger.domain.Incident
import kotlin.time.Duration

data class Configuration(
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
