package xyz.deseteral.jaruga.ranger.lookout.api

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import xyz.deseteral.jaruga.ranger.lookout.domain.IncidentService
import xyz.deseteral.jaruga.ranger.lookout.domain.ProbeService
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import kotlin.time.Instant

@OptIn(ExperimentalKtorApi::class)
fun Routing.firewatchEndpoint(
    timelineService: TimelineService,
    probeService: ProbeService,
    incidentService: IncidentService,
) {
    get("/firewatch") {
        val checks = timelineService.getCheckTimelines()
            .mapKeys { (checkId, _) -> checkId.value }
            .mapValues { (_, timeline) -> timeline.toList() }

        val activeIncidents = incidentService.getActiveIncidents()
            .mapKeys { (checkId, _) -> checkId.value }

        val degraded = activeIncidents.isNotEmpty()

        call.respond(
            FirewatchResponse(
                lastSuccessfulProbeAt = probeService.lastSuccessfulProbeAt,
                checks = checks,
                activeIncidents = activeIncidents,
                degraded,
            ),
        )
    }.describe {
        summary = "Get the current monitoring status."
        description = "Returns the last successful probe time, check timelines (newest first), and active incidents. Null timeline entries represent unknown check states. The system is degraded when any incident is active."
        responses {
            HttpStatusCode.OK {
                description = "Current monitoring status."
                schema = jsonSchema<FirewatchResponse>()
            }
        }
    }
}

@Serializable
private data class FirewatchResponse(
    val lastSuccessfulProbeAt: Instant?,
    val checks: Map<String, List<CheckResult?>>,
    val activeIncidents: Map<String, Incident>,
    val degraded: Boolean,
)
