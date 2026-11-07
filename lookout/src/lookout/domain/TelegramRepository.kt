package xyz.deseteral.jaruga.ranger.lookout.domain

import com.github.michaelbull.result.Result
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.Incident

interface TelegramRepository {
    suspend fun notify(
        incident: Incident,
        channel: LookoutConfig.NotificationChannel.Telegram,
    ): Result<Unit, TelegramError>
}

sealed interface TelegramError : NotificationError {
    data object RequestFailed : TelegramError
}
