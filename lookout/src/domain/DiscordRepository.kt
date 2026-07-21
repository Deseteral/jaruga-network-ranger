package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus

interface DiscordRepository {
    suspend fun notifyViaWebhook(
        checkId: SystemStatus.CheckResult.CheckId,
        check: SystemStatus.CheckResult,
        webhook: Configuration.NotificationChannel.DiscordWebhook,
    )
}