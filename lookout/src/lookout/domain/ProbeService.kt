package xyz.deseteral.jaruga.ranger.lookout.domain

class ProbeService(
    private val campgroundRepository: CampgroundRepository,
    private val timelineService: TimelineService,
    private val incidentService: IncidentService,
) {
    suspend fun startProbing() {
        campgroundRepository.getSystemStatusUpdates().collect { systemStatusUpdates ->
            val diff = timelineService.insertLatest(systemStatusUpdates)
            incidentService.processTimelineDiff(diff)
        }
    }
}