package xyz.deseteral.jaruga.lookout.external

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.parseUrl
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.lookout.domain.DiscordRepository
import xyz.deseteral.jaruga.network.ranger.domain.Incident

class DiscordClient : DiscordRepository {
    private val messageIds: MutableMap<Incident.Id, String> = mutableMapOf()

    override suspend fun notifyViaWebhook(
        incident: Incident,
        webhook: Configuration.NotificationChannel.DiscordWebhook,
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
        webhook: Configuration.NotificationChannel.DiscordWebhook,
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
        webhook: Configuration.NotificationChannel.DiscordWebhook,
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
    // TODO: Date formatting.
    val footerText = buildString {
        append("Triggered on $triggeredOn")
        resolvedOn?.let { append(", resolved on $it") }
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
        // TODO: Fix colors.
        const val COLOR_RED: Int = 0xB34141
        const val COLOR_GREEN: Int = 0x69B341
    }
}

@Serializable
private data class DiscordWebhookResponseBody(
    val id: String,
)