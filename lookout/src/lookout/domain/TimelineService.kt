package xyz.deseteral.jaruga.ranger.lookout.domain

import xyz.deseteral.jaruga.ranger.trail.domain.CheckId
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.FAILURE
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult.State.SUCCESS
import xyz.deseteral.jaruga.ranger.trail.domain.SystemStatus
import kotlin.time.Clock

/**
 * Keeps state of checks.
 * If each check is a row, then each column represents a point in time. Front of the queue is most recent.
 * Each row is a circular buffer of size [timelineSize].
 */
class TimelineService(
    private val timelineSize: Int,
    private val clock: Clock,
) {
    init {
        require(timelineSize > 1) { "timelineSize must be greater than 1." }
    }

    private val userDefinedChecks: MutableMap<CheckId, Timeline> = mutableMapOf()

    private val systemChecks: Map<CheckId, Timeline> = mapOf(
        ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID to emptyTimeline(),
    )

    private val allChecks: Map<CheckId, Timeline>
        get() = systemChecks + userDefinedChecks

    /**
     * Returns timelines of all known checks.
     */
    fun getCheckTimelines(): Map<CheckId, Timeline> =
        allChecks

    /**
     * Returns latest [CheckResult] for given [CheckId].
     * Returns null if given ID is not recorded or the check's state is unknown.
     */
    fun getCheckResult(id: CheckId): CheckResult? =
        (userDefinedChecks[id] ?: systemChecks[id])?.firstOrNull()

    /**
     * Process latest system update.
     * Inserts new timeline entry for all known checks (both user defined and system checks).
     * If new update couldn't be obtained, then [[systemStatusUpdate]] should be null.
     *
     * Return [TimelinesUpdateDiff] which indicates changes in the system after the update was processed.
     */
    fun insertLatest(systemStatusUpdate: SystemStatus?): TimelinesUpdateDiff {
        // systemStatus is null when we could not connect to Campground.
        // When that happens all existing known checks should be failed.
        val isSuccessfulUpdate = systemStatusUpdate != null

        if (isSuccessfulUpdate) {
            // Process all checks in update.
            // When update contains new check - add it with timeline full of unknown (null) values and process latest update state.
            systemStatusUpdate.checks.forEach { (checkId, check) ->
                userDefinedChecks.getOrPut(checkId) { emptyTimeline() }
                    .insertLatestCheck(check)
            }

            // When update does not contain checks that were previously known, set their latest state to unknown (null).
            val missingInUpdate = userDefinedChecks.keys.toSet() - systemStatusUpdate.checks.keys.toSet()
            missingInUpdate.forEach { checkId ->
                userDefinedChecks.getOrPut(checkId) { emptyTimeline() }
                    .insertLatestCheck(null)
            }
        } else {
            insertLatestAllFailsForExistingChecks()
        }

        systemChecks[ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID]?.insertLatestCheck(
            allChecksInUnknownStateResult(state = if (isSuccessfulUpdate) SUCCESS else FAILURE),
        )

        return generateTimelinesDiff()
    }

    private fun generateTimelinesDiff() = TimelinesUpdateDiff(
        degraded = allChecks.queryForCheckIdsWhere { current, previous ->
            current?.state == FAILURE && (previous?.state == SUCCESS || previous == null)
        },
        healed = allChecks.queryForCheckIdsWhere { current, previous ->
            current?.state == SUCCESS && previous?.state == FAILURE
        },
    )

    private fun insertLatestAllFailsForExistingChecks() =
        userDefinedChecks.values.forEach { it.insertLatestCheck(null) }

    private fun emptyTimeline(): Timeline =
        ArrayDeque(List(timelineSize) { null })

    // TODO: Extract along with corresponding ID.
    private fun allChecksInUnknownStateResult(state: CheckResult.State) = CheckResult(
        state = state,
        severity = CheckResult.Severity.MAJOR,
        timestamp = clock.now(),
        description = "Could not acquire system status update. All checks are in unknown state. Check Campground instance.",
    )

    /**
     * Describes the outcome of system status update processing.
     * [CheckId] contained in the sets indicate the states of which checks changed during the update.
     *
     * [degraded] indicates checks that failed after this update, which have not failed before.
     * [healed] indicates checks that succeed after this update, which have been failed before.
     */
    data class TimelinesUpdateDiff(
        val degraded: Set<CheckId>,
        val healed: Set<CheckId>,
    ) {
        val isDirty: Boolean = degraded.isNotEmpty() || healed.isNotEmpty()
    }

    companion object {
        val ALL_CHECKS_IN_UNKNOWN_STATE_CHECK_ID = CheckId("All checks in unknown state")
    }
}

private typealias Timeline = ArrayDeque<CheckResult?>

private fun Timeline.insertLatestCheck(check: CheckResult?) {
    removeLast()
    addFirst(check)
}

private fun Map<CheckId, Timeline>.queryForCheckIdsWhere(predicate: (current: CheckResult?, previous: CheckResult?) -> Boolean): Set<CheckId> =
    filter { (_, timeline) ->
        val (current, previous) = timeline
        predicate(current, previous)
    }.map { (checkId, _) -> checkId }.toSet()
