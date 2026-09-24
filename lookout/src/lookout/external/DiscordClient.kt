package xyz.deseteral.jaruga.ranger.lookout.external

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.deseteral.jaruga.ranger.lookout.domain.DiscordRepository
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import kotlin.time.Instant

class DiscordClient : DiscordRepository {
    private val messageIds: MutableMap<Incident.Id, String> = mutableMapOf()

    override suspend fun notifyViaWebhook(
        incident: Incident,
        webhook: LookoutConfig.NotificationChannel.DiscordWebhook,
    ) {
        val messageId = messageIds[incident.id]
        val embeds = listOf(incident.toDiscordEmbed())

        if (messageId != null) {
            editMessage(messageId, embeds, webhook)
        } else {
            createMessage(embeds, webhook)
                .also { messageIds[incident.id] = it }
        }
    }

    private suspend fun createMessage(
        embeds: List<DiscordWebhookRequestBody.Embed>,
        webhook: LookoutConfig.NotificationChannel.DiscordWebhook,
    ): String {
        val url = URLBuilder(webhook.url).apply {
            parameters.append("wait", "true")
        }.toString()

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(DiscordWebhookRequestBody(embeds))
        }
        val body = response.body<DiscordWebhookResponseBody>()
        return body.id
    }

    private suspend fun editMessage(
        messageId: String,
        embeds: List<DiscordWebhookRequestBody.Embed>,
        webhook: LookoutConfig.NotificationChannel.DiscordWebhook,
    ) {
        val url = URLBuilder(webhook.url).apply {
            appendPathSegments("messages", messageId)
        }.toString()

        httpClient.patch(url) {
            contentType(ContentType.Application.Json)
            setBody(DiscordWebhookRequestBody(embeds))
        }
    }

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
    }
}

private fun Incident.toDiscordEmbed(): DiscordWebhookRequestBody.Embed {
    val footerText = buildString {
        append("Triggered on ${triggeredOn.format()}")
        resolvedOn?.let { append(", resolved on ${it.format()}") }
        append(".")
    }

    val embed = DiscordWebhookRequestBody.Embed(
        title = checkId.value,
        description = description,
        color = if (isResolved) DiscordWebhookRequestBody.COLOR_GREEN else DiscordWebhookRequestBody.COLOR_RED,
        footer = DiscordWebhookRequestBody.Embed.Footer(footerText),
    )

    return embed
}

@Serializable
private data class DiscordWebhookRequestBody(
    val embeds: List<Embed>,
) {
    @Serializable
    data class Embed(
        val title: String,
        val description: String?,
        val color: Int,
        val footer: Footer? = null,
    ) {
        @Serializable
        data class Footer(val text: String)
    }

    companion object {
        const val COLOR_RED: Int = 0xB34141 // Flexoki red-400.
        const val COLOR_GREEN: Int = 0x879A39 // Flexoki green-400.
    }
}

@Serializable
private data class DiscordWebhookResponseBody(
    val id: String,
)

private val dateTimeFormat = LocalDateTime.Format {
    year()
    char('-')
    monthNumber(Padding.ZERO)
    char('-')
    day(Padding.ZERO)

    char(' ')

    hour(Padding.ZERO)
    char(':')
    minute(Padding.ZERO)
    char(':')
    second(Padding.ZERO)
}

private fun Instant.format() = toLocalDateTime(TimeZone.currentSystemDefault()).format(dateTimeFormat)