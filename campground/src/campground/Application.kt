package xyz.deseteral.jaruga.ranger.campground

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import xyz.deseteral.jaruga.ranger.campground.api.systemStatusEndpoint
import xyz.deseteral.jaruga.ranger.campground.configuration.Configuration
import xyz.deseteral.jaruga.ranger.campground.domain.StatusService
import xyz.deseteral.jaruga.ranger.campground.external.HomeAssistantClient
import xyz.deseteral.jaruga.ranger.campground.external.TeslaMateClient
import java.io.File
import kotlin.time.Clock

fun main() {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8080,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val configuration = loadUserConfiguration<Configuration, CampgroundConfigurationScript>(
        Configuration::class,
        File("config.campground.kts"),
    )

    install(ContentNegotiation) {
        json()
    }

    val statusService = StatusService(
        homeAssistantRepositoryProvider = { HomeAssistantClient(it) },
        teslaMateRepositoryProvider = { TeslaMateClient(it) },
        Clock.System,
        configuration,
    )

    routing {
        systemStatusEndpoint(statusService)
    }
}
