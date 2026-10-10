package xyz.deseteral.jaruga.ranger.campground.external

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onErr
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.HomeAssistantError
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.HomeAssistantRepository
import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext

private val logger = KotlinLogging.logger {}

class HomeAssistantClient(
    private val config: CampgroundConfig.DataSource.HomeAssistant,
) : HomeAssistantRepository {
    override suspend fun getHass(): Result<CampgroundContext.Hass, HomeAssistantError> {
        val states = getAllStates() ?: return Err(HomeAssistantError.Unavailable)
        return Ok(CampgroundContext.Hass(states.map { it.toDomain() }))
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
        return runSuspendCatching {
            httpClient
                .get(urlApiStates) { bearerAuth(config.accessToken) }
                .body<List<HomeAssistantState>>()
        }
            .onErr { ex -> logger.error(ex) { "Error while getting HomeAssistant states." } }
            .get()
    }

    private val urlApiStates = URLBuilder(config.instanceUrl).apply {
        appendPathSegments("api", "states")
    }.toString()

    @Serializable
    private data class HomeAssistantState(
        val entityId: String,
        val state: String,
    ) {
        fun toDomain() = CampgroundContext.Hass.EntityState(entityId, state)
    }
}