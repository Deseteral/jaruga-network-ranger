package xyz.deseteral.jaruga.lookout.domain

class ProbeService(
    private val campgroundRepository: CampgroundRepository,
    private val timelineService: TimelineService,
) {
    suspend fun startProbing() {
        campgroundRepository.getSystemStatusUpdates().collect { systemStatusUpdates ->
            val diff = timelineService.insertLatest(systemStatusUpdates)
            println(diff)
        }
    }
}