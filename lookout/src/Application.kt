package xyz.deseteral.jaruga.lookout

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    println("Hello lookout!")
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8081,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
