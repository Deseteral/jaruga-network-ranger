package xyz.deseteral.jaruga.ranger.campground

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import xyz.deseteral.jaruga.ranger.campground.api.statusEndpoint
import xyz.deseteral.jaruga.ranger.campground.domain.StatusService
import xyz.deseteral.jaruga.ranger.campground.domain.datasources.DataSourceProvider
import xyz.deseteral.jaruga.ranger.campground.external.HomeAssistantClient
import xyz.deseteral.jaruga.ranger.campground.external.TeslaMateClient
import xyz.deseteral.jaruga.ranger.trail.loader.loadCampgroundConfig
import kotlin.time.Clock

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val dataDirectoryPath = Path(environment.config.property("campground.data_path").getString())
        .also { check(SystemFileSystem.metadataOrNull(it)?.isDirectory == true) { "Data path is not a directory: $it." } }
    val config = loadCampgroundConfig(Path(dataDirectoryPath, "config.campground.kts"))

    install(ContentNegotiation) {
        json()
    }

    val statusService = StatusService(
        homeAssistantRepositoryProvider = DataSourceProvider { HomeAssistantClient(it) },
        teslaMateRepositoryProvider = DataSourceProvider { TeslaMateClient(it) },
        sources = config.sources,
        checks = config.checks,
        clock = Clock.System,
    )

    routing {
        statusEndpoint(statusService)
    }
}
