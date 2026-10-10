package xyz.deseteral.jaruga.ranger.campground.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import xyz.deseteral.jaruga.ranger.campground.domain.StatusService

fun Routing.statusEndpoint(
    statusService: StatusService,
) {
    get("/status") {
        statusService.getStatus().let { call.respond(it) }
    }
}