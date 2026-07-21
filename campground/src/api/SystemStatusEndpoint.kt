package xyz.deseteral.jaruga.campground.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import xyz.deseteral.jaruga.campground.domain.StatusService

fun Routing.systemStatusEndpoint(
    statusService: StatusService,
) {
    get("/system/status") {
        statusService.getStatus().let { call.respond(it) }
    }
}