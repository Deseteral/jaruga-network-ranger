package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.network.ranger.domain.CheckId
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult.State.SUCCESS
import kotlin.time.Clock

class TimelineService(
    private val timelineSize: Int,
    private val clock: Clock,
) {
    private typealias Timeline = ArrayDeque<CheckResult?>

    init {
        require(timelineSize > 1) { "timelineSize must be greater than 1." }
    }

    /**
     * Keeps state of checks.
     * If each check is a row, then each column represents a point in time. Front of the queue is most recent.
     * Each row is a circular buffer of size `timelineSize`.
     */
    private val checks: MutableMap<CheckId, Timeline> = mutableMapOf()

    private var ticks = 0

    fun insertLatest(systemStatusUpdate: SystemStatus?) {
        ticks += 1

        // systemStatus is null when we could not connect to Campground.
        // When that happens all existing known checks should be failed.
        if (systemStatusUpdate == null) {
            insertLatestAllFailsForExistingChecks()
            return
        }

        // Process all checks in update.
        // When update contains new check - add it with unknown (null) values and process latest update state.
        systemStatusUpdate.checks.forEach { (checkId, check) ->
            checks.getOrPut(checkId) { emptyTimeline() }
                .insertLatestCheck(check)
        }

        // When update does not contain checks that were previously known, set their latest state to unknown (null).
        val missingInUpdate = checks.keys.toSet().minus(systemStatusUpdate.checks.keys.toSet())
        missingInUpdate.forEach { checkId ->
            checks.getOrPut(checkId) { emptyTimeline() }
                .insertLatestCheck(null)
        }

        // TODO: Remove.
        debugPrint()
    }

    fun findChecksThatFailedDuringRecentTick(): Map<CheckId, CheckResult> {
        val allCurrentInUnknownState = checks.values.all { it[0] == null }
        val allPreviousInUnknownState = checks.values.all { it[1] == null }

        return if (allCurrentInUnknownState && (!allPreviousInUnknownState || ticks == 1)) {
            allChecksInUnknownStateCheckResult()
        } else {
            checks
                .filter { (_, timeline) ->
                    val (current, previous) = timeline
                    current?.state == FAILURE && (previous?.state == SUCCESS || previous == null)
                }
                .map { (checkId, timeline) -> checkId to requireNotNull(timeline.first()) }
                .associate { (checkId, check) -> checkId to check }
        }
    }

    private fun insertLatestAllFailsForExistingChecks() =
        checks.values.forEach { it.insertLatestCheck(null) }

    private fun emptyTimeline(): Timeline =
        ArrayDeque(List(timelineSize) { null })

    private fun allChecksInUnknownStateCheckResult(): Map<CheckId, CheckResult> =
        mapOf(
            CheckId("All checks in unknown state") to CheckResult(
                state = FAILURE,
                severity = CheckResult.Severity.MAJOR,
                description = "Could not communicate with Campground instance.",
                timestamp = clock.now(),
            )
        )

    // TODO: Remove.
    private fun debugPrint() {
        val statusToChar = { s: CheckResult.State? ->
            when (s) {
                SUCCESS -> '1'
                FAILURE -> '0'
                null -> '.'
            }
        }

        checks.forEach { (id, timeline) ->
            println("${id.value.padEnd(50, ' ')} | ${timeline.map { statusToChar(it?.state) }.joinToString("")}")
        }
    }

    private fun Timeline.insertLatestCheck(check: CheckResult?) {
        removeLast()
        addFirst(check)
    }
}