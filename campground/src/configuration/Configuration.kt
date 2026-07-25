package xyz.deseteral.jaruga.campground.configuration

import xyz.deseteral.jaruga.campground.domain.Context

data class Configuration(
    val sources: Map<String, DataSource>,
) {
    sealed class DataSource {
        data class HomeAssistant(
            val instanceUrl: String,
            val accessToken: String,
        ) : DataSource()

        data class TeslaMate(
            val instanceUrl: String,
        ) : DataSource()
    }
}

// TODO: Add unique validation on name field.
data class CheckDefinition(
    /**
     * Unique name for a check, that is also its identifier.
     */
    val name: String,

    /**
     * Description of a check and why it failed.
     * May be empty (null).
     */
    val description: ((ctx: Context) -> String)? = null,

    /**
     * Based on data used to decide this check's health, what is the severity of this check.
     */
    val severity: (ctx: Context) -> Severity,

    /**
     * Should this check failed. Return false to make this check as healthy.
     * Returning null will make this check fail.
     */
    val isFailure: (ctx: Context) -> Boolean?,
) {
    enum class Severity {
        MAJOR,
        MINOR,
    }
}
