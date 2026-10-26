package xyz.deseteral.jaruga.ranger.lookout.api

import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.deseteral.jaruga.ranger.lookout.domain.CampgroundRepository
import xyz.deseteral.jaruga.ranger.lookout.domain.DiscordRepository
import xyz.deseteral.jaruga.ranger.lookout.domain.IncidentService
import xyz.deseteral.jaruga.ranger.lookout.domain.NotificationService
import xyz.deseteral.jaruga.ranger.lookout.domain.PersistentCache
import xyz.deseteral.jaruga.ranger.lookout.domain.ProbeService
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import kotlin.time.Clock

class OpenApiEndpointTest : FunSpec({
    test("the specification documents firewatch and its response schemas without documenting itself") {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                val timeline = TimelineService(3, Clock.System)
                val incidents = IncidentService(
                    notificationService = NotificationService(
                        discordRepository = object : DiscordRepository {
                            override suspend fun notifyViaWebhook(
                                incident: Incident,
                                webhook: LookoutConfig.NotificationChannel.DiscordWebhook,
                            ) = Ok(Unit)
                        },
                        config = LookoutConfig.Notifier(
                            process = { error("No notifications expected") },
                            retryPolicy = LookoutConfig.Notifier.RetryPolicy(),
                        ),
                    ),
                    activeIncidentsCache = object : PersistentCache<MutableMap<CheckId, Incident>> {
                        override fun read(defaultProvider: () -> MutableMap<CheckId, Incident>) = Ok(defaultProvider())
                        override fun write(value: MutableMap<CheckId, Incident>) = Ok(value)
                    },
                    clock = Clock.System,
                )
                val probe = ProbeService(
                    campgroundRepository = object : CampgroundRepository {
                        override suspend fun getSystemStatusUpdates() = emptyFlow<Nothing>()
                    },
                    timelineService = timeline,
                    incidentService = incidents,
                    clock = Clock.System,
                )
                routing {
                    openApiEndpoint()
                    firewatchEndpoint(timeline, probe, incidents)
                }
            }

            val response = client.get("/openapi.json")
            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.withoutParameters() shouldBe ContentType.Application.Json
            val spec = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            spec.getValue("openapi").jsonPrimitive.content.startsWith("3.1.") shouldBe true
            spec.getValue("info").jsonObject.getValue("title").jsonPrimitive.content shouldBe "Lookout API"
            spec.getValue("info").jsonObject["description"] shouldBe null
            val paths = spec.getValue("paths").jsonObject
            paths.keys shouldBe setOf("/firewatch")
            paths.getValue("/firewatch").jsonObject["post"] shouldBe null
            val responseSchema = paths.getValue("/firewatch").jsonObject.getValue("get").jsonObject
                .getValue("responses").jsonObject.getValue("200").jsonObject
                .getValue("content").jsonObject.getValue("application/json").jsonObject.getValue("schema").jsonObject
            responseSchema.getValue("\$ref").jsonPrimitive.content shouldBe "#/components/schemas/FirewatchResponse"

            val schemas = spec.getValue("components").jsonObject.getValue("schemas").jsonObject
            val properties = schemas.getValue("FirewatchResponse").jsonObject.getValue("properties").jsonObject
            properties.keys shouldBe setOf("lastSuccessfulProbeAt", "checks", "activeIncidents", "degraded")
            properties.getValue("degraded").jsonObject.getValue("type").jsonPrimitive.content shouldBe "boolean"
            val timestamp = properties.getValue("lastSuccessfulProbeAt").jsonObject
            timestamp.getValue("type").jsonArray.map { it.jsonPrimitive.content }.toSet() shouldBe setOf("string", "null")
            timestamp.getValue("format").jsonPrimitive.content shouldBe "date-time"
            val timelines = properties.getValue("checks").jsonObject.getValue("additionalProperties").jsonObject
            timelines.getValue("type").jsonPrimitive.content shouldBe "array"
            timelines.getValue("items").jsonObject.getValue("\$ref").jsonPrimitive.content shouldBe "#/components/schemas/CheckResult"
            schemas.getValue("CheckResult").jsonObject.getValue("type").jsonArray
                .map { it.jsonPrimitive.content }.toSet() shouldBe setOf("object", "null")
            properties.getValue("activeIncidents").jsonObject.getValue("additionalProperties").jsonObject
                .getValue("\$ref").jsonPrimitive.content shouldBe "#/components/schemas/Incident"

            val firewatch = client.get("/firewatch")
            firewatch.status shouldBe HttpStatusCode.OK
            val status = Json.parseToJsonElement(firewatch.bodyAsText()).jsonObject
            status.keys shouldBe properties.keys
            status.getValue("lastSuccessfulProbeAt").jsonPrimitive.content shouldBe "null"
        }
    }
})
