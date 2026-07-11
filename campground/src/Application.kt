package xyz.deseteral.jaruga.campground

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import xyz.deseteral.jaruga.campground.domain.StatusService
import xyz.deseteral.jaruga.campground.external.HomeAssistantClient
import xyz.deseteral.jaruga.campground.external.TeslaMateClient

fun main(args: Array<String>) {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8080,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    val statusService = StatusService(
        homeAssistantRepositoryProvider = { HomeAssistantClient(it) },
        teslaMateRepositoryProvider = { TeslaMateClient(it) },
    )

    routing {
        get("/") {
            statusService.getStatus().let { call.respond(it) }
        }
    }
}
