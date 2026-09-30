import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import kotlin.time.Duration.Companion.seconds

private val discordTestMinor = LookoutConfig.NotificationChannel.DiscordWebhook("http://localhost:9930/discord")
private val discordTestMajor = LookoutConfig.NotificationChannel.DiscordWebhook("http://localhost:9930/discord")

LookoutConfig(
    campground = LookoutConfig.Campground(
        instanceUrl = "http://localhost:8080",
        interval = 5.seconds,
    ),
    bufferSize = 12,
    notifier = LookoutConfig.Notifier(
        retryPolicy = LookoutConfig.Notifier.RetryPolicy(
            maxAttempts = 3,
        ),
        process = { incident ->
            when (incident.severity) {
                CheckResult.Severity.MAJOR -> discordTestMajor
                CheckResult.Severity.MINOR -> discordTestMinor
            }
        },
    ),
)
