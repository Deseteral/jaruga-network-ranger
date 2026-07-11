package xyz.deseteral.jaruga.campground.domain

import xyz.deseteral.jaruga.campground.configuration.Configuration

interface TeslaMateRepository {
    suspend fun getState(): TeslaMateState?
}

typealias TeslaMateRepositoryProvider = (dataSource: Configuration.DataSource.TeslaMate) -> TeslaMateRepository

data class TeslaMateState(
    val isUpdateAvailable: Boolean,
)