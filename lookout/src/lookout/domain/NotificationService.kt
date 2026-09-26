package xyz.deseteral.jaruga.ranger.lookout.domain

import com.github.michaelbull.result.Result
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.Incident

class NotificationService(
    val discordRepository: DiscordRepository,
    val config: LookoutConfig.Notifier,
) {
    suspend fun sendNotification(incident: Incident): Result<Unit, DiscordError> {
        return when (val notification = config.process(incident)) {
            is LookoutConfig.NotificationChannel.DiscordWebhook ->
                discordRepository.notifyViaWebhook(incident, notification)
        }
    }
}