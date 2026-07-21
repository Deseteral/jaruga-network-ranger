package xyz.deseteral.jaruga.lookout.external

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.deseteral.jaruga.lookout.configuration.Configuration
import xyz.deseteral.jaruga.lookout.domain.DiscordRepository
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus

class DiscordClient: DiscordRepository {
    override suspend fun notifyViaWebhook(
        checkId: SystemStatus.CheckResult.CheckId,
        check: SystemStatus.CheckResult,
        webhook: Configuration.NotificationChannel.DiscordWebhook,
    ) {
        val content = checkId.value

        httpClient.post(webhook.url) {
            contentType(ContentType.Application.Json)
            setBody(DiscordWebhookRequestBody(content))
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

    @Serializable
    private data class DiscordWebhookRequestBody(
        val content: String,
    )
}