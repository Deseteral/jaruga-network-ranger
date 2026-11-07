package xyz.deseteral.jaruga.ranger.lookout.external

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onErr
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.deseteral.jaruga.ranger.lookout.domain.TelegramError
import xyz.deseteral.jaruga.ranger.lookout.domain.TelegramRepository
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.Incident

private val logger = KotlinLogging.logger {}
private const val TELEGRAM_MAX_MESSAGE_LENGTH = 4096

class TelegramClient : TelegramRepository {
    private val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun notify(
        incident: Incident,
        channel: LookoutConfig.NotificationChannel.Telegram,
    ): Result<Unit, TelegramError> {
        return runSuspendCatching {
            val response = httpClient.post("https://api.telegram.org/bot${channel.botToken}/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(TelegramRequest(chatId = channel.chatId, text = incident.toTelegramText()))
            }.body<TelegramResponse>()
            check(response.ok) { "Telegram request was rejected." }
        }
            .map { Unit }
            .onErr { ex -> logger.error(ex) { "Telegram notification failed." } }
            .mapError { TelegramError.RequestFailed }
    }
}

private fun Incident.toTelegramText(): String {
    val footer = buildString {
        append("\n\nTriggered on $triggeredOn")
        resolvedOn?.let { append(", resolved on $it") }
        append(".")
    }
    val content = buildString {
        append(if (isResolved) "RESOLVED: " else "ALERT: ")
        append(checkId.value)
        description?.let { append("\n\n$it") }
    }
    return content.take(TELEGRAM_MAX_MESSAGE_LENGTH - footer.length) + footer
}

@Serializable
private data class TelegramRequest(
    @SerialName("chat_id") val chatId: String,
    val text: String,
)

@Serializable
private data class TelegramResponse(
    val ok: Boolean,
)
