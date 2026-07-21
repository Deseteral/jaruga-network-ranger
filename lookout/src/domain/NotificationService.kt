package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.network.ranger.domain.CheckId
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult

class NotificationService(
    val discordRepository: DiscordRepository,
    val config: Configuration.Notifier,
) {
    suspend fun sendNotification(checkId: CheckId, check: CheckResult) {
        when (val notification = config.process(check)) {
            is Configuration.NotificationChannel.DiscordWebhook -> discordRepository.notifyViaWebhook(
                checkId,
                check,
                notification,
            )
        }
    }
}