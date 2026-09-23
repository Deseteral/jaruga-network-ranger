package xyz.deseteral.jaruga.ranger.lookout.api

import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult

fun Routing.statusEndpoint(timelineService: TimelineService) {
    get("/status") {
        timelineService.getCheckTimelines()
            .mapKeys { (checkId, _) -> checkId.value }
            .mapValues { (_, timeline) -> timeline.toList() }
            .let {
                call.respond(StatusResponse(checks = it))
            }
    }
}

@Serializable
private data class StatusResponse(
    val checks: Map<String, List<CheckResult?>>
)