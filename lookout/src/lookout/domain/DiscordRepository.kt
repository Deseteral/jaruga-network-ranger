package xyz.deseteral.jaruga.ranger.lookout.domain

import xyz.deseteral.jaruga.ranger.lookout.configuration.Configuration
import xyz.deseteral.jaruga.ranger.domain.Incident

interface DiscordRepository {
    suspend fun notifyViaWebhook(
        incident: Incident,
        webhook: Configuration.NotificationChannel.DiscordWebhook,
    )
}