package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.network.ranger.domain.CheckId
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult

interface DiscordRepository {
    suspend fun notifyViaWebhook(
        checkId: CheckId,
        check: CheckResult,
        webhook: Configuration.NotificationChannel.DiscordWebhook,
    )
}