package xyz.deseteral.jaruga.lookout.domain

import xyz.deseteral.jaruga.network.ranger.domain.CheckId
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult
import xyz.deseteral.jaruga.network.ranger.domain.SystemStatus
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.network.ranger.domain.CheckResult.State.SUCCESS
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.filter
import kotlin.time.Clock

class TimelineService(
    private val timelineSize: Int,
    private val clock: Clock,
) {
    init {
        require(timelineSize > 1) { "timelineSize must be greater than 1." }
    }

    /**
     * Keeps state of checks.
     * If each check is a row, then each column represents a point in time. Front of the queue is most recent.
     * Each row is a circular buffer of size `timelineSize`.
     */
    private val checkTimelines: MutableMap<CheckId, Timeline> = mutableMapOf()

    private val allChecksInUnknownStateTimeline = emptyTimeline()

    fun getCheckResult(id: CheckId): CheckResult? {
        val timeline = if (id == ALL_CHECKS_UNKNOWN_STATE_ID) allChecksInUnknownStateTimeline else checkTimelines[id]
        return timeline?.firstOrNull()
    }

    fun insertLatest(systemStatusUpdate: SystemStatus?): TimelinesUpdateDiff {
        // systemStatus is null when we could not connect to Campground.
        // When that happens all existing known checks should be failed.
        val isSuccessfulUpdate = systemStatusUpdate != null

        if (isSuccessfulUpdate) {
            // Process all checks in update.
            // When update contains new check - add it with timeline full of unknown (null) values and process latest update state.
            systemStatusUpdate.checks.forEach { (checkId, check) ->
                checkTimelines.getOrPut(checkId) { emptyTimeline() }
                    .insertLatestCheck(check)
            }

            // When update does not contain checks that were previously known, set their latest state to unknown (null).
            val missingInUpdate = checkTimelines.keys.toSet() - systemStatusUpdate.checks.keys.toSet()
            missingInUpdate.forEach { checkId ->
                checkTimelines.getOrPut(checkId) { emptyTimeline() }
                    .insertLatestCheck(null)
            }
        } else {
            insertLatestAllFailsForExistingChecks()
        }

        allChecksInUnknownStateTimeline.insertLatestCheck(allChecksInUnknownStateResult(state = if (isSuccessfulUpdate) SUCCESS else FAILURE))

        // TODO: Remove.
        debugPrint()

        return generateTimelinesDiff()
    }

    private fun Map<CheckId, Timeline>.queryForCheckIdsWhere(predicate: (current: CheckResult?, previous: CheckResult?) -> Boolean): Set<CheckId> {
        return this
            .filter { (_, timeline) ->
                val (current, previous) = timeline
                predicate(current, previous)
            }
            .map { (checkId, _) -> checkId }
            .toSet()
    }

    private fun generateTimelinesDiff(): TimelinesUpdateDiff =
        (checkTimelines + (ALL_CHECKS_UNKNOWN_STATE_ID to allChecksInUnknownStateTimeline)).let {
            TimelinesUpdateDiff(
                degraded = it.queryForCheckIdsWhere { current, previous ->
                    current?.state == FAILURE && (previous?.state == SUCCESS || previous == null)
                },
                healed = it.queryForCheckIdsWhere { current, previous ->
                    current?.state == SUCCESS && previous?.state == FAILURE
                },
            )
        }

    private fun insertLatestAllFailsForExistingChecks() =
        checkTimelines.values.forEach { it.insertLatestCheck(null) }

    private fun emptyTimeline(): Timeline =
        ArrayDeque(List(timelineSize) { null })

    private fun allChecksInUnknownStateResult(state: CheckResult.State) = CheckResult(
        state = state,
        severity = CheckResult.Severity.MAJOR,
        timestamp = clock.now(),
        description = null,
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

        checkTimelines.forEach { (id, timeline) ->
            println("${id.value.padEnd(50, ' ')} | ${timeline.map { statusToChar(it?.state) }.joinToString("")}")
        }
    }

    private typealias Timeline = ArrayDeque<CheckResult?>

    private fun Timeline.insertLatestCheck(check: CheckResult?) {
        removeLast()
        addFirst(check)
    }

    data class TimelinesUpdateDiff(
        val degraded: Set<CheckId>,
        val healed: Set<CheckId>,
    )

    companion object {
        val ALL_CHECKS_UNKNOWN_STATE_ID = CheckId("All checks in unknown state")
    }
}