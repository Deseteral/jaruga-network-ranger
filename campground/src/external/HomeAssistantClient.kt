package xyz.deseteral.jaruga.campground.external

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import xyz.deseteral.jaruga.campground.configuration.Configuration
import xyz.deseteral.jaruga.campground.domain.datasources.Hass
import xyz.deseteral.jaruga.campground.domain.datasources.HomeAssistantRepository
import xyz.deseteral.jaruga.campground.domain.datasources.HomeAssistantState

private val logger = KotlinLogging.logger {}

class HomeAssistantClient(
    private val config: Configuration.DataSource.HomeAssistant,
) : HomeAssistantRepository {
    override suspend fun getHass(): Hass? {
        val states = getAllStates() ?: return null
        return Hass(states)
    }

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                },
            )
        }
    }

    private suspend fun getAllStates(): List<HomeAssistantState>? {
        return try {
            httpClient
                .get("${config.instanceUrl}/api/states") { bearerAuth(config.accessToken) }
                .body()
        } catch (ex: Exception) {
            logger.error(ex) { "Error while getting HomeAssistant states." }
            null
        }
    }
}