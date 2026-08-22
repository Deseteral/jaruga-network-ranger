package xyz.deseteral.jaruga.campground

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import xyz.deseteral.jaruga.campground.api.systemStatusEndpoint
import xyz.deseteral.jaruga.campground.configuration.Configuration
import xyz.deseteral.jaruga.campground.domain.StatusService
import xyz.deseteral.jaruga.campground.external.HomeAssistantClient
import xyz.deseteral.jaruga.campground.external.TeslaMateClient
import xyz.deseteral.jaruga.network.ranger.configuration.CampgroundConfigurationScript
import xyz.deseteral.jaruga.network.ranger.configuration.loadUserConfiguration
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
