package xyz.deseteral.jaruga.ranger.campground.domain

import com.github.michaelbull.result.get
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.DataSourceProvider
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.HomeAssistantRepository
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.TeslaMateRepository
import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext
import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

class StatusService(
    private val homeAssistantRepositoryProvider: DataSourceProvider<CampgroundConfig.DataSource.HomeAssistant, HomeAssistantRepository>,
    private val teslaMateRepositoryProvider: DataSourceProvider<CampgroundConfig.DataSource.TeslaMate, TeslaMateRepository>,
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
                        hass[name] = async { homeAssistantRepositoryProvider.of(dataSource).getHass() }
                            .await()
                            .get()

                    is CampgroundConfig.DataSource.TeslaMate ->
                        teslaMate[name] = async { teslaMateRepositoryProvider.of(dataSource).getState() }
                            .await()
                            .get()
                }
            }
        }.toDomain()

        // Evaluate checks.
        checks.associate { check ->
            logger.debug { "Evaluating check definition \"${check.name}\"." }
            val result = try {
                evaluateCheckDefinition(check, ctx, timestamp)
            } catch (ex: Exception) {
                CheckResult(
                    description = "Possible configuration error in Campground, while evaluating check definition \"${check.name}\".",
                    state = CheckResult.State.FAILURE,
                    severity = CheckResult.Severity.MAJOR,
                    timestamp = timestamp,
                ).also { logger.error(ex) { "Error while evaluating check definition \"${check.name}\"." } }
            }
            CheckId(check.name) to result
        }.let { checks -> SystemStatus(checks) }
    }

    private fun evaluateCheckDefinition(
        check: CampgroundConfig.CheckDefinition,
        ctx: CampgroundContext,
        timestamp: Instant,
    ): CheckResult {
        return CheckResult(
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
    }
}

private data class Context(
    val hass: MutableMap<String, CampgroundContext.Hass?> = mutableMapOf(),
    val teslaMate: MutableMap<String, CampgroundContext.TeslaMate?> = mutableMapOf(),
) {
    fun toDomain() = CampgroundContext(hass, teslaMate)
}
