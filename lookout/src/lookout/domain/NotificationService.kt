package xyz.deseteral.jaruga.ranger.lookout.domain

import com.github.michaelbull.result.Result
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.stopAtAttempts
import com.github.michaelbull.retry.result.retry
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.Incident

class NotificationService(
    val discordRepository: DiscordRepository,
    val config: LookoutConfig.Notifier,
) {
    private val retryPolicy =
        binaryExponentialBackoff<DiscordError>(min = 100, max = 10_000) +
            stopAtAttempts(config.maxAttempts)

    suspend fun sendNotification(incident: Incident): Result<Unit, DiscordError> {
        return when (val notification = config.process(incident)) {
            is LookoutConfig.NotificationChannel.DiscordWebhook ->
                retry(retryPolicy) {
                    discordRepository.notifyViaWebhook(incident, notification)
                }
        }
    }
}