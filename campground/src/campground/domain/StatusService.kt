package xyz.deseteral.jaruga.ranger.campground.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.HomeAssistantRepositoryProvider
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.TeslaMateRepositoryProvider
import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext
import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

class StatusService(
    private val homeAssistantRepositoryProvider: HomeAssistantRepositoryProvider,
    private val teslaMateRepositoryProvider: TeslaMateRepositoryProvider,
    private val sources: Map<String, CampgroundConfig.DataSource>,
    private val checks: List<CampgroundConfig.CheckDefinition>,
    private val clock: Clock,
) {
    suspend fun getStatus(): SystemStatus = coroutineScope {
        val timestamp = clock.now()

        // Gather context.
        val ctx = Context().apply {
            sources.forEach { (name, dataSource) ->
                when (dataSource) {
                    is CampgroundConfig.DataSource.HomeAssistant ->
                        hass[name] = async { homeAssistantRepositoryProvider(dataSource).getHass() }.await()

                    is CampgroundConfig.DataSource.TeslaMate ->
                        teslaMate[name] = async { teslaMateRepositoryProvider(dataSource).getState() }.await()
                }
            }
        }.toDomain()

        // Evaluate checks.
        checks.associate { check ->
            logger.info { "Evaluating check \"${check.name}\"." }
            CheckId(check.name) to CheckResult(
                severity = when (check.severity(ctx)) {
                    CampgroundConfig.CheckDefinition.Severity.MAJOR -> CheckResult.Severity.MAJOR
                    CampgroundConfig.CheckDefinition.Severity.MINOR -> CheckResult.Severity.MINOR
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

private data class Context(
    val hass: MutableMap<String, CampgroundContext.Hass?> = mutableMapOf(),
    val teslaMate: MutableMap<String, CampgroundContext.TeslaMate?> = mutableMapOf(),
) {
    fun toDomain() = CampgroundContext(hass, teslaMate)
}
