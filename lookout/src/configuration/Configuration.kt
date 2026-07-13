package xyz.deseteral.jaruga.lookout.configuration

import kotlin.time.Duration

data class Configuration(
    val campground: Campground,
    val bufferSize: Int,
) {
    data class Campground(
        val instanceUrl: String,
        val interval: Duration,
    )
}