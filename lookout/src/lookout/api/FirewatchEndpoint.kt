package xyz.deseteral.jaruga.ranger.lookout.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import xyz.deseteral.jaruga.ranger.lookout.domain.IncidentService
import xyz.deseteral.jaruga.ranger.lookout.domain.ProbeService
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import kotlin.time.Instant

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
    }
}

@Serializable
private data class FirewatchResponse(
    val lastSuccessfulProbeAt: Instant?,
    val checks: Map<String, List<CheckResult?>>,
    val activeIncidents: Map<String, Incident>,
    val degraded: Boolean,
)
