package xyz.deseteral.jaruga.campground.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.deseteral.jaruga.campground.checks
import xyz.deseteral.jaruga.campground.configuration
import xyz.deseteral.jaruga.campground.configuration.CheckDefinition
import xyz.deseteral.jaruga.campground.configuration.Configuration
import kotlin.collections.forEach
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

class StatusService(
    private val homeAssistantRepositoryProvider: HomeAssistantRepositoryProvider,
    private val teslaMateRepositoryProvider: TeslaMateRepositoryProvider,
    private val clock: Clock,
) {
    suspend fun getStatus(): SystemStatus = coroutineScope {
        val context = Context()

        val timestamp = clock.now()

        configuration.sources.forEach { (name, dataSource) ->
            when (dataSource) {
                is Configuration.DataSource.HomeAssistant ->
                    context.hass[name] = async { homeAssistantRepositoryProvider(dataSource).getHass() }.await()

                is Configuration.DataSource.TeslaMate ->
                    context.teslaMate[name] = async { teslaMateRepositoryProvider(dataSource).getState() }.await()
            }
        }

        checks.map { check ->
            logger.info { "Evaluating check \"${check.name}\"." }
            SystemStatus.CheckResult(
                name = check.name,
                severity = when (check.severity(context)) {
                    CheckDefinition.Severity.MAJOR -> SystemStatus.CheckResult.Severity.MAJOR
                    CheckDefinition.Severity.MINOR -> SystemStatus.CheckResult.Severity.MINOR
                },
                state = if (check.isFailure(context) ?: true) {
                    SystemStatus.CheckResult.State.FAILURE
                } else {
                    SystemStatus.CheckResult.State.SUCCESS
                },
            )
        }.let { checks -> SystemStatus(timestamp, checks) }
    }
}

// TODO: Change maps to be immutable.
data class Context(
    val hass: MutableMap<String, Hass?> = mutableMapOf(),
    val teslaMate: MutableMap<String, TeslaMateState?> = mutableMapOf(),
)