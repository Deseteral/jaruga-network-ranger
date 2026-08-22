package xyz.deseteral.jaruga.ranger.lookout.domain

import xyz.deseteral.jaruga.ranger.lookout.configuration.Configuration
import xyz.deseteral.jaruga.ranger.domain.Incident

class NotificationService(
    val discordRepository: DiscordRepository,
    val config: Configuration.Notifier,
) {
    suspend fun sendNotification(incident: Incident) {
        when (val notification = config.process(incident)) {
            is Configuration.NotificationChannel.DiscordWebhook ->
                discordRepository.notifyViaWebhook(incident, notification)
        }
    }
}