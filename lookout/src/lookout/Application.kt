package xyz.deseteral.jaruga.ranger.lookout

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import xyz.deseteral.jaruga.ranger.lookout.api.statusEndpoint
import xyz.deseteral.jaruga.ranger.lookout.domain.IncidentService
import xyz.deseteral.jaruga.ranger.lookout.domain.NotificationService
import xyz.deseteral.jaruga.ranger.lookout.domain.ProbeService
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService
import xyz.deseteral.jaruga.ranger.lookout.external.CampgroundClient
import xyz.deseteral.jaruga.ranger.lookout.external.DiscordClient
import xyz.deseteral.jaruga.ranger.lookout.external.FilePersistentCache
import xyz.deseteral.jaruga.ranger.trail.loader.loadLookoutConfig
import kotlin.time.Clock

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val dataDirectoryPath = Path(environment.config.property("lookout.data_path").getString())
    val config = loadLookoutConfig(Path(dataDirectoryPath, "config.lookout.kts"))

    install(ContentNegotiation) {
        json()
    }

    val campgroundClient = CampgroundClient(config.campground)
    val timelineService = TimelineService(config.bufferSize, Clock.System)
    val discordClient = DiscordClient()
    val notificationService = NotificationService(discordClient, config.notifier)
    val incidentService = IncidentService(
        timelineService = timelineService,
        notificationService = notificationService,
        activeIncidentsCache = FilePersistentCache.json(
            name = "active-incidents",
            dataDirectoryPath = dataDirectoryPath,
            fileSystem = SystemFileSystem,
            format = true,
        ),
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
        statusEndpoint(timelineService)
    }
}
