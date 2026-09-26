package xyz.deseteral.jaruga.ranger.lookout.external

import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onErr
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import xyz.deseteral.jaruga.ranger.lookout.domain.CampgroundRepository
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

class CampgroundClient(
    private val config: LookoutConfig.Campground,
) : CampgroundRepository {
    override suspend fun getSystemStatusUpdates(): Flow<SystemStatus?> {
        return ticker(config.interval).map { getSystemStatus() }
    }

    private suspend fun getSystemStatus(): SystemStatus? {
        return runSuspendCatching { httpClient.get("${config.instanceUrl}/system/status") }
            .map { response -> response.body<SystemStatus>() }
            .onErr { ex -> logger.error(ex) { "Error while getting Campground system status." } }
            .get()
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

    private fun ticker(period: Duration) = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(period)
        }
    }
}