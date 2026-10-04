package xyz.deseteral.jaruga.ranger.lookout.domain

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.io.files.Path
import xyz.deseteral.jaruga.ranger.lookout.domain.TimelineService.Companion.ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.Severity.MAJOR
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.SUCCESS
import xyz.deseteral.jaruga.ranger.trail.domain.Incident
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus
import kotlin.time.Clock
import kotlin.time.Instant

private val CHECK_ID = CheckId("test check")
private val OTHER_CHECK_ID = CheckId("other check")
private val CLOCK_TIME = Instant.parse("2026-10-03T10:00:00Z")
private val T1 = Instant.parse("2026-10-03T10:00:01Z")
private val T2 = Instant.parse("2026-10-03T10:00:02Z")
private val T3 = Instant.parse("2026-10-03T10:00:03Z")
private val T4 = Instant.parse("2026-10-03T10:00:04Z")

class IncidentProcessingTest : FunSpec(
    {
        context("timeline") {
            test("healthy update fills the timeline and sends no notification") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(healthyStatus(T1))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.timelineService.getCheckTimelines()[CHECK_ID]?.toList() shouldBe listOf(
                    checkResult(SUCCESS, T1),
                    null,
                    null,
                )
                fixture.discord.notifications.shouldBeEmpty()
                fixture.incidentCache.incidents.shouldBeEmpty()
            }

            test("the timeline keeps only the newest entries") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(
                        failingStatus(T1),
                        healthyStatus(T2),
                        failingStatus(T3),
                        healthyStatus(T4),
                    )
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.timelineService.getCheckTimelines()[CHECK_ID]?.toList() shouldBe listOf(
                    checkResult(SUCCESS, T4),
                    checkResult(FAILURE, T3),
                    checkResult(SUCCESS, T2),
                )
            }
        }

        context("incident lifecycle") {
            test("failing check opens an incident, and a heal closes it") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(failingStatus(T1))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                val openedIncident = fixture.discord.notifications.single().also {
                    it.checkId shouldBe CHECK_ID
                    it.triggeredOn shouldBe T1
                    it.resolvedOn shouldBe null
                }
                fixture.incidentCache.incidents.keys shouldBe setOf(CHECK_ID)

                // and
                fixture.campground.updates = listOf(healthyStatus(T2))

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.timelineService.getCheckTimelines()[CHECK_ID]?.toList() shouldBe listOf(
                    checkResult(SUCCESS, T2),
                    checkResult(FAILURE, T1),
                    null,
                )

                fixture.discord.notifications shouldHaveSize 2
                fixture.discord.notifications[0].also { triggerNotification ->
                    triggerNotification.id shouldBe openedIncident.id
                    triggerNotification.resolvedOn shouldBe null
                }
                fixture.discord.notifications[1].also { resolveNotification ->
                    resolveNotification.id shouldBe openedIncident.id
                    resolveNotification.resolvedOn shouldBe T2
                    resolveNotification.history.map { it.state } shouldBe listOf(FAILURE, SUCCESS)
                }
                fixture.incidentCache.incidents.shouldBeEmpty()
            }

            test("check that keeps failing does not open a second incident") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(failingStatus(T1))
                }
                fixture.probeService.performSingleProbe()

                // when
                fixture.campground.updates = listOf(failingStatus(T2))
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications.single().also {
                    it.triggeredOn shouldBe T1
                }
                fixture.incidentCache.incidents.getValue(CHECK_ID).triggeredOn shouldBe T1
            }

            test("an open incident is closed when Lookout restarts with a healthy check") {
                // given
                val existingIncident = openIncident(T1)
                val fixture = Fixture(
                    incidentCache = InMemoryIncidentCache(mutableMapOf(CHECK_ID to existingIncident)),
                ).apply {
                    campground.updates = listOf(healthyStatus(T2))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications.single().also {
                    it.id shouldBe existingIncident.id
                    it.resolvedOn shouldBe T2
                }

                fixture.incidentCache.incidents.shouldBeEmpty()
            }

            test("a check removed from the configuration closes its incident") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(failingStatus(T1))
                }
                fixture.probeService.performSingleProbe()

                // when
                fixture.campground.updates = listOf(SystemStatus(checks = emptyMap()))
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications shouldHaveSize 2
                fixture.discord.notifications[0].let {
                    it.checkId shouldBe CHECK_ID
                    it.triggeredOn shouldBe T1
                    it.resolvedOn shouldBe null
                }
                fixture.discord.notifications[1].let {
                    it.checkId shouldBe CHECK_ID
                    it.resolvedOn shouldBe CLOCK_TIME
                    it.description shouldBe "Check removed from configuration."
                }
                fixture.incidentCache.incidents.shouldBeEmpty()
            }

            test("a removed check that comes back opens a new incident") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(failingStatus(T1))
                }
                fixture.probeService.performSingleProbe()
                val firstIncident = fixture.discord.notifications.single()

                fixture.campground.updates = listOf(SystemStatus(checks = emptyMap()))
                fixture.probeService.performSingleProbe()

                fixture.campground.updates = listOf(failingStatus(T3))

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications shouldHaveSize 3
                fixture.discord.notifications[0].let {
                    it.id shouldBe firstIncident.id
                    it.resolvedOn shouldBe null
                }
                fixture.discord.notifications[1].let {
                    it.id shouldBe firstIncident.id
                    it.resolvedOn shouldBe CLOCK_TIME
                }
                fixture.discord.notifications[2].let {
                    it.id shouldNotBe firstIncident.id
                    it.checkId shouldBe CHECK_ID
                    it.triggeredOn shouldBe T3
                    it.resolvedOn shouldBe null
                }

                fixture.incidentCache.incidents.getValue(CHECK_ID).triggeredOn shouldBe T3
            }

            test("only the failing check opens an incident") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(
                        SystemStatus(
                            checks = mapOf(
                                CHECK_ID to checkResult(FAILURE, T1),
                                OTHER_CHECK_ID to checkResult(SUCCESS, T1),
                            ),
                        ),
                    )
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications.single().checkId shouldBe CHECK_ID
                fixture.timelineService.getCheckResult(CHECK_ID)?.state shouldBe FAILURE
                fixture.timelineService.getCheckResult(OTHER_CHECK_ID)?.state shouldBe SUCCESS
                fixture.incidentCache.incidents.keys shouldBe setOf(CHECK_ID)
            }
        }

        context("unknown state") {
            test("unreachable Campground fails the unknown state check and leaves the user-defined check in unknown state") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(healthyStatus(T1))
                }
                fixture.probeService.performSingleProbe()

                // when
                fixture.campground.updates = listOf(null)
                fixture.probeService.performSingleProbe()

                // then
                fixture.timelineService.getCheckResult(CHECK_ID) shouldBe null
                fixture.timelineService.getCheckResult(ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID)?.state shouldBe FAILURE

                fixture.discord.notifications.single().also {
                    it.checkId shouldBe ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID
                    it.severity shouldBe MAJOR
                }

                fixture.incidentCache.incidents.keys shouldBe setOf(ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID)
            }

            test("check that heals after unknown state closes its incident") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(failingStatus(T1))
                }
                fixture.probeService.performSingleProbe()

                fixture.campground.updates = listOf(null)
                fixture.probeService.performSingleProbe()

                // when
                fixture.campground.updates = listOf(healthyStatus(T2))
                fixture.probeService.performSingleProbe()

                // then
                fixture.timelineService.getCheckTimelines()[CHECK_ID]?.toList() shouldBe listOf(
                    checkResult(SUCCESS, T2),
                    null,
                    checkResult(FAILURE, T1),
                )

                fixture.discord.notifications shouldHaveSize 4
                fixture.discord.notifications[0].let {
                    it.checkId shouldBe CHECK_ID
                    it.resolvedOn shouldBe null
                }
                fixture.discord.notifications[1].also {
                    it.checkId shouldBe ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID
                    it.resolvedOn shouldBe null
                }
                fixture.discord.notifications[2].also {
                    it.checkId shouldBe ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID
                    it.resolvedOn shouldBe CLOCK_TIME
                }
                fixture.discord.notifications[3].also {
                    it.checkId shouldBe CHECK_ID
                    it.resolvedOn shouldBe T2
                }

                fixture.incidentCache.incidents.shouldBeEmpty()
            }

            test("an absent check is not resolved while Campground is unreachable") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(failingStatus(T1))
                }
                fixture.probeService.performSingleProbe()

                // when
                fixture.campground.updates = listOf(null)
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications shouldHaveSize 2
                fixture.discord.notifications[0].also {
                    it.checkId shouldBe CHECK_ID
                    it.resolvedOn shouldBe null
                }
                fixture.discord.notifications[1].also {
                    it.checkId shouldBe ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID
                    it.resolvedOn shouldBe null
                }

                fixture.incidentCache.incidents.keys shouldBe setOf(
                    CHECK_ID,
                    ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID,
                )
            }
        }

        context("notifications") {
            test("a failed notification does not discard the incident") {
                // given
                val fixture = Fixture().apply {
                    discord.sendingNotificationWillAlwaysFail = true
                    campground.updates = listOf(failingStatus(T1))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.attempts shouldBe 1
                fixture.discord.notifications.shouldBeEmpty()

                fixture.incidentCache.incidents.keys shouldBe setOf(CHECK_ID)
            }

            test("a failed notification is retried before it is dropped") {
                // given
                val fixture = Fixture(maxAttempts = 3).apply {
                    discord.failuresRemaining = 2
                    campground.updates = listOf(failingStatus(T1))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.attempts shouldBe 3
                fixture.discord.notifications.single().checkId shouldBe CHECK_ID
            }
        }

        context("incident cache") {
            test("a failed cache write does not stop the incident processing") {
                // given
                val fixture = Fixture(
                    incidentCache = InMemoryIncidentCache(failWrite = true),
                ).apply {
                    campground.updates = listOf(failingStatus(T1))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.discord.notifications.single().checkId shouldBe CHECK_ID
                fixture.incidentCache.incidents.keys shouldBe setOf(CHECK_ID)
            }

            test("the cache is written only when the incidents change") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(healthyStatus(T1))
                }

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.incidentCache.writeCount shouldBe 0

                // and
                fixture.campground.updates = listOf(failingStatus(T2))

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.incidentCache.writeCount shouldBe 1
            }
        }

        context("probe health") {
            test("last successful probe time is set") {
                // given
                val fixture = Fixture().apply {
                    campground.updates = listOf(healthyStatus(T1))
                }
                fixture.probeService.lastSuccessfulProbeAt shouldBe null

                // when
                fixture.probeService.performSingleProbe()

                // then
                fixture.probeService.lastSuccessfulProbeAt shouldBe CLOCK_TIME
            }
        }
    },
)

private class Fixture(
    val incidentCache: InMemoryIncidentCache = InMemoryIncidentCache(),
    maxAttempts: Int = 1,
) {
    val campground = FakeCampgroundRepository()
    val timelineService = TimelineService(timelineSize = 3, clock = FixedClock(CLOCK_TIME))
    val discord = FakeDiscordRepository()

    val probeService = ProbeService(
        campgroundRepository = campground,
        timelineService = timelineService,
        incidentService = IncidentService(
            notificationService = NotificationService(
                discordRepository = discord,
                config = LookoutConfig.Notifier(
                    process = { LookoutConfig.NotificationChannel.DiscordWebhook(WEBHOOK_URL) },
                    retryPolicy = LookoutConfig.Notifier.RetryPolicy(maxAttempts = maxAttempts),
                ),
            ),
            activeIncidentsCache = incidentCache,
            clock = FixedClock(CLOCK_TIME),
        ),
        clock = FixedClock(CLOCK_TIME),
    )
}

private const val WEBHOOK_URL = "http://localhost/webhook"

private val CACHE_FAILURE_PATH = Path("test-cache.json")

private fun healthyStatus(timestamp: Instant): SystemStatus =
    SystemStatus(checks = mapOf(CHECK_ID to checkResult(SUCCESS, timestamp)))

private fun failingStatus(timestamp: Instant): SystemStatus =
    SystemStatus(checks = mapOf(CHECK_ID to checkResult(FAILURE, timestamp)))

private fun checkResult(state: CheckResult.State, timestamp: Instant): CheckResult = CheckResult(
    description = null,
    state = state,
    severity = CheckResult.Severity.MINOR,
    timestamp = timestamp,
)

private fun openIncident(triggeredOn: Instant): Incident = Incident(
    checkId = CHECK_ID,
    triggeredOn = triggeredOn,
    resolvedOn = null,
    severity = CheckResult.Severity.MINOR,
    description = null,
    history = listOf(Incident.HistoryEntry(timestamp = triggeredOn, state = FAILURE)),
)

private class FakeCampgroundRepository : CampgroundRepository {
    var updates: List<SystemStatus?> = emptyList()

    override suspend fun getSystemStatusUpdates(): Flow<SystemStatus?> = updates.asFlow()
}

private class FakeDiscordRepository : DiscordRepository {
    val notifications: MutableList<Incident> = mutableListOf()

    var attempts: Int = 0
        private set
    var sendingNotificationWillAlwaysFail: Boolean = false
    var failuresRemaining: Int = 0

    override suspend fun notifyViaWebhook(
        incident: Incident,
        webhook: LookoutConfig.NotificationChannel.DiscordWebhook,
    ): Result<Unit, DiscordError> {
        attempts++

        val failed = sendingNotificationWillAlwaysFail || failuresRemaining > 0
        if (failuresRemaining > 0) failuresRemaining--

        return if (failed) {
            Err(DiscordError.WebhookFailed)
        } else {
            notifications.add(incident)
            Ok(Unit)
        }
    }
}

private class InMemoryIncidentCache(
    private var stored: MutableMap<CheckId, Incident> = mutableMapOf(),
    private val failRead: Boolean = false,
    private val failWrite: Boolean = false,
) : PersistentCache<MutableMap<CheckId, Incident>> {
    val incidents: Map<CheckId, Incident> get() = stored

    var writeCount: Int = 0
        private set

    override fun read(defaultProvider: () -> MutableMap<CheckId, Incident>): Result<MutableMap<CheckId, Incident>, CacheError> =
        if (failRead) Err(CacheError.Read(CACHE_FAILURE_PATH)) else Ok(stored)

    override fun write(value: MutableMap<CheckId, Incident>): Result<MutableMap<CheckId, Incident>, CacheError> {
        writeCount++
        stored = value
        return if (failWrite) Err(CacheError.Write(CACHE_FAILURE_PATH)) else Ok(value)
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
