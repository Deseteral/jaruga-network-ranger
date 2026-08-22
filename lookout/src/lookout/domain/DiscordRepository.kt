package xyz.deseteral.jaruga.ranger.lookout.domain

import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.Incident

interface DiscordRepository {
    suspend fun notifyViaWebhook(
        incident: Incident,
        webhook: LookoutConfig.NotificationChannel.DiscordWebhook,
    )
}