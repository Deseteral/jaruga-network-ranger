package xyz.deseteral.jaruga.lookout

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import kotlinx.io.files.SystemFileSystem
import xyz.deseteral.jaruga.lookout.domain.IncidentService
import xyz.deseteral.jaruga.lookout.domain.NotificationService
import xyz.deseteral.jaruga.lookout.domain.ProbeService
import xyz.deseteral.jaruga.lookout.domain.TimelineService
import xyz.deseteral.jaruga.lookout.external.CampgroundClient
import xyz.deseteral.jaruga.lookout.external.DiscordClient
import xyz.deseteral.jaruga.lookout.external.FilePersistentCache
import kotlin.time.Clock

fun main() {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8081,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    val campgroundClient = CampgroundClient(configuration.campground)
    val timelineService = TimelineService(configuration.bufferSize, Clock.System)
    val discordClient = DiscordClient()
    val notificationService = NotificationService(discordClient, configuration.notifier)
    val incidentService = IncidentService(
        timelineService = timelineService,
        notificationService = notificationService,
        activeIncidentsCache = FilePersistentCache.json("active-incidents", SystemFileSystem),
    )
    val probeService = ProbeService(
        campgroundRepository = campgroundClient,
        timelineService = timelineService,
        incidentService = incidentService,
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
