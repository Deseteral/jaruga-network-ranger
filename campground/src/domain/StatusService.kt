package xyz.deseteral.jaruga.campground.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.deseteral.jaruga.campground.checks
import xyz.deseteral.jaruga.campground.configuration.CheckDefinition

private val logger = KotlinLogging.logger {}

class StatusService(
    private val homeAssistantRepository: HomeAssistantRepository?,
) {
    suspend fun getStatus(): SystemStatus = coroutineScope {
        val context = Context(
            hass = async { homeAssistantRepository?.getHass() }.await(),
        )

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
        }.let { SystemStatus(it) }
    }
}

data class Context(
    val hass: Hass? = null,
)