package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus

class NotificationService(
    val discordRepository: DiscordRepository,
    val config: Configuration.Notifier,
) {
    suspend fun sendNotification(check: SystemStatus.CheckResult) {
        when (val notification = config.process(check)) {
            is Configuration.NotificationChannel.DiscordWebhook -> discordRepository.notifyViaWebhook(
                check,
                notification,
            )
        }
    }
}