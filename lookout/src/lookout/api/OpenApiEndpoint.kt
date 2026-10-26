package xyz.deseteral.jaruga.ranger.lookout.api

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalKtorApi::class)
fun Routing.openApiEndpoint() {
    get("/openapi.json") {
        val document = OpenApiDoc(info = OpenApiInfo("Lookout API", "1.0.0")) + descendants()
        call.respondText(Json.encodeToString(document), ContentType.Application.Json)
    }.hide()
}
