package xyz.deseteral.jaruga.campground.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.deseteral.jaruga.campground.healthcheck

private val logger = KotlinLogging.logger {}

class StatusService(
    private val homeAssistantRepository: HomeAssistantRepository?,
) {
    suspend fun getStatus(): Status = coroutineScope {
        val context = Context(
            hass = async { homeAssistantRepository?.getHass() }.await(),
        )

        healthcheck.checks.map { check ->
            logger.info { "Evaluating check \"${check.name}\"." }
            CheckResult(
                name = check.name,
                state = if (check.isFailure(context) ?: true) {
                    CheckState.FAILURE
                } else {
                    CheckState.SUCCESS
                },
            )
        }.let { Status(it) }
    }
}

data class Context(
    val hass: Hass? = null,
)