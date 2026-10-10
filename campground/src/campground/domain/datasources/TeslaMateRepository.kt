package xyz.deseteral.jaruga.ranger.campground.domain.datasources

import com.github.michaelbull.result.Result
import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext

interface TeslaMateRepository {
    suspend fun getState(): Result<CampgroundContext.TeslaMate, TeslaMateError>
}

sealed interface TeslaMateError {
    data object Unavailable : TeslaMateError
}
