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

fun main(args: Array<String>) {
    println("Hello campground!")
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    val homeAssistantClient = configuration.homeAssistant?.let { HomeAssistantClient(it) }

    val statusService = StatusService(homeAssistantRepository = homeAssistantClient)

    routing {
        get("/") {
            statusService.getStatus().let { call.respond(it) }
        }
    }
}
