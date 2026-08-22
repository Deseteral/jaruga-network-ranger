package xyz.deseteral.jaruga.ranger.campground.domain.datasources

import xyz.deseteral.jaruga.ranger.campground.configuration.Configuration

interface TeslaMateRepository {
    suspend fun getState(): TeslaMateState?
}

typealias TeslaMateRepositoryProvider = (dataSource: Configuration.DataSource.TeslaMate) -> TeslaMateRepository

data class TeslaMateState(
    val isUpdateAvailable: Boolean,
)