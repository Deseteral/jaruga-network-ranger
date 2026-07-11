package xyz.deseteral.jaruga.campground.external

import xyz.deseteral.jaruga.campground.configuration.Configuration
import xyz.deseteral.jaruga.campground.domain.TeslaMateRepository
import xyz.deseteral.jaruga.campground.domain.TeslaMateState

class TeslaMateClient(
    private val config: Configuration.DataSource.TeslaMate,
): TeslaMateRepository {
    override suspend fun getState(): TeslaMateState? {
        // TODO: Implement!
        return TeslaMateState(isUpdateAvailable = true)
    }
}