package xyz.deseteral.jaruga.lookout

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import xyz.deseteral.jaruga.lookout.domain.ProbeService
import xyz.deseteral.jaruga.lookout.domain.TimelineService
import xyz.deseteral.jaruga.lookout.external.CampgroundClient

fun main() {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8081,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val campgroundClient = CampgroundClient(configuration.campground)
    val timelineService = TimelineService(configuration.bufferSize)
    val probeService = ProbeService(
        campgroundRepository = campgroundClient,
        timelineService = timelineService,
    )

    launch {
        probeService.startProbing()
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
