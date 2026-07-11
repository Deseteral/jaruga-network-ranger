package xyz.deseteral.jaruga.campground.domain

import xyz.deseteral.jaruga.campground.configuration.Configuration

interface TeslaMateRepository {
    suspend fun getVersion(): String?
}

typealias TeslaMateRepositoryProvider = (dataSource: Configuration.DataSource.TeslaMate) -> TeslaMateRepository
