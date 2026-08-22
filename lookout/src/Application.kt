package xyz.deseteral.jaruga.ranger.lookout

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
import xyz.deseteral.jaruga.ranger.lookout.configuration.Configuration
import xyz.deseteral.jaruga.ranger.lookout.domain.IncidentService
import xyz.deseteral.jaruga.ranger.lookout.domain.NotificationService
import xyz.deseteral.jaruga.ranger.lookout.domain.ProbeService
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService
import xyz.deseteral.jaruga.ranger.lookout.external.CampgroundClient
import xyz.deseteral.jaruga.ranger.lookout.external.DiscordClient
import xyz.deseteral.jaruga.ranger.lookout.external.FilePersistentCache
import xyz.deseteral.jaruga.network.ranger.configuration.LookoutConfigurationScript
import xyz.deseteral.jaruga.network.ranger.configuration.loadUserConfiguration
import java.io.File
import kotlin.time.Clock

fun main() {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8081,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val configuration = loadUserConfiguration<Configuration, LookoutConfigurationScript>(
        Configuration::class,
        File("config.lookout.kts"),
    )

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
