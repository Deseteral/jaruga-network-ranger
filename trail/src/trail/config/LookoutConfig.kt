package xyz.deseteral.jaruga.ranger.trail.config

import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import java.net.URI
import kotlin.time.Duration

data class LookoutConfig(
    val campground: Campground,
    val bufferSize: Int,
    val notifier: Notifier,
) {
    init {
        require(campground.interval > Duration.ZERO) {
            "Campground interval must be positive, but is ${campground.interval}."
        }
        require(notifier.retryPolicy.maxAttempts >= 1) {
            "Notifier max attempts must be at least 1, but is ${notifier.retryPolicy.maxAttempts}."
        }
    }

    data class Campground(
        val instanceUrl: String,
        val interval: Duration,
        val proxyUrl: String? = null,
    )

    data class Notifier(
        val process: (incident: Incident) -> NotificationChannel,
        val retryPolicy: RetryPolicy,
    ) {
        data class RetryPolicy(
            val maxAttempts: Int = 3,
        )
    }

    sealed class NotificationChannel {
        data class DiscordWebhook(
            val url: String,
        ) : NotificationChannel() {
            init {
                require(url.isValidUrl()) {
                    "Discord webhook URL must be valid."
                }
            }
        }
    }
}

private fun String.isValidUrl(): Boolean = try {
    URI(this).let { it.scheme != null && it.host != null }
} catch (_: Exception) {
    false
}