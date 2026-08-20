package xyz.deseteral.jaruga.campground.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.deseteral.jaruga.campground.configuration.CheckDefinition
import xyz.deseteral.jaruga.campground.configuration.Configuration
import xyz.deseteral.jaruga.campground.domain.datasources.Hass
import xyz.deseteral.jaruga.campground.domain.datasources.HomeAssistantRepositoryProvider
import xyz.deseteral.jaruga.campground.domain.datasources.TeslaMateRepositoryProvider
import xyz.deseteral.jaruga.campground.domain.datasources.TeslaMateState
import xyz.deseteral.jaruga.network.ranger.domain.CheckId
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus
import kotlin.collections.forEach
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

class StatusService(
    private val homeAssistantRepositoryProvider: HomeAssistantRepositoryProvider,
    private val teslaMateRepositoryProvider: TeslaMateRepositoryProvider,
    private val clock: Clock,
    private val configuration: Configuration,
) {
    suspend fun getStatus(): SystemStatus = coroutineScope {
        val ctx = Context()

        val timestamp = clock.now()

        configuration.sources.forEach { (name, dataSource) ->
            when (dataSource) {
                is Configuration.DataSource.HomeAssistant ->
                    ctx.hass[name] = async { homeAssistantRepositoryProvider(dataSource).getHass() }.await()

                is Configuration.DataSource.TeslaMate ->
                    ctx.teslaMate[name] = async { teslaMateRepositoryProvider(dataSource).getState() }.await()
            }
        }

        configuration.checks.associate { check ->
            logger.info { "Evaluating check \"${check.name}\"." }
            CheckId(check.name) to CheckResult(
                severity = when (check.severity(ctx)) {
                    CheckDefinition.Severity.MAJOR -> CheckResult.Severity.MAJOR
                    CheckDefinition.Severity.MINOR -> CheckResult.Severity.MINOR
                },
                state = if (check.isFailure(ctx) ?: true) {
                    CheckResult.State.FAILURE
                } else {
                    CheckResult.State.SUCCESS
                },
                description = check.description?.let { it(ctx) },
                timestamp = timestamp, // TODO: This should be a timestamp of each check, not the "status timestamp".
            )
        }.let { checks -> SystemStatus(checks) }
    }
}

// TODO: Change maps to be immutable.
data class Context(
    val hass: MutableMap<String, Hass?> = mutableMapOf(),
    val teslaMate: MutableMap<String, TeslaMateState?> = mutableMapOf(),
)
