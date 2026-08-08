package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.network.ranger.domain.Incident

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