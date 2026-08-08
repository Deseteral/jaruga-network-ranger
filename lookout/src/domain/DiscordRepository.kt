package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.network.ranger.domain.Incident

interface DiscordRepository {
    suspend fun notifyViaWebhook(
        incident: Incident,
        webhook: Configuration.NotificationChannel.DiscordWebhook,
    )
}