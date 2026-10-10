package xyz.deseteral.jaruga.ranger.campground.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

class PeriodicallyComputedValue<T>(
    private val period: Duration,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val compute: suspend () -> T,
) {
    private val state = MutableStateFlow<T?>(null)

    /**
     * The current value, or null if it has not been computed yet.
     */
    val value: T?
        get() = state.value.also { job.start() }

    init {
        require(period > Duration.ZERO) { "Period must be positive." }
    }

    private val job: Job = scope.launch(start = CoroutineStart.LAZY) {
        while (isActive) {
            try {
                state.value = compute()
            } catch (e: CancellationException) {
                throw e
            } catch (ex: Exception) {
                logger.warn(ex) { "Periodically computed value computation failed. Keeping last value." }
            }
            delay(period)
        }
    }
}
