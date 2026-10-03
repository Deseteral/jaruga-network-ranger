package xyz.deseteral.jaruga.ranger.lookout.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class ProbeService(
    private val campgroundRepository: CampgroundRepository,
    private val timelineService: TimelineService,
    private val incidentService: IncidentService,
) {
    private suspend fun performSingleProbe() {
        campgroundRepository.getSystemStatusUpdates().collect { systemStatusUpdates ->
            val diff = timelineService.insertLatest(systemStatusUpdates)
            incidentService.processTimelineDiff(diff)
        }
    }

    suspend fun startProbing() {
        while (currentCoroutineContext().isActive) {
            try {
                performSingleProbe()
                logger.warn { "Probe flow completed. Restarting." }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                logger.error(ex) { "Probe loop failed. Restarting." }
            }
            delay(5.seconds)
        }
    }
}