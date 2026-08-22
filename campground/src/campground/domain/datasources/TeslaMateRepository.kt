package xyz.deseteral.jaruga.ranger.campground.domain.datasources

import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext

interface TeslaMateRepository {
    suspend fun getState(): CampgroundContext.TeslaMate?
}

typealias TeslaMateRepositoryProvider = (dataSource: CampgroundConfig.DataSource.TeslaMate) -> TeslaMateRepository