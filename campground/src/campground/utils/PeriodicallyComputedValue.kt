package xyz.deseteral.jaruga.ranger.campground.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

class PeriodicallyComputedValue<T>(
    private val period: Duration,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val compute: suspend () -> T,
) {
    private val state = MutableStateFlow<T?>(null)

    val value: T?
        get() = state.value

    init {
        require(period > Duration.ZERO) { "Period must be positive." }
    }

    private val job: Job = scope.launch {
        while (isActive) {
            try {
                state.value = compute()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            delay(period)
        }
    }

    fun stop() {
        job.cancel()
    }
}
