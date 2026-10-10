package xyz.deseteral.jaruga.ranger.trail.config

import xyz.deseteral.jaruga.ranger.trail.domain.CampgroundContext
import xyz.deseteral.jaruga.ranger.trail.domain.CheckResult
import xyz.deseteral.jaruga.ranger.trail.domain.SystemChecks.RESERVED_CHECK_NAMES

data class CampgroundConfig(
    val sources: Map<String, DataSource>,
    val checks: List<CheckDefinition>,
) {
    init {
        val duplicatedNames = checks
            .groupingBy { it.name }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        require(duplicatedNames.isEmpty()) {
            "Check names must be unique. Duplicated: ${duplicatedNames.joinToString(", ")}."
        }

        checks.forEach { check ->
            require(!RESERVED_CHECK_NAMES.contains(check.name)) {
                "Check name \"${check.name}\" is reserved for Lookout."
            }
        }
    }

    sealed class DataSource {
        data class HomeAssistant(
            val instanceUrl: String,
            val accessToken: String,
        ) : DataSource()

        data class TeslaMate(
            val instanceUrl: String,
        ) : DataSource()
    }

    data class CheckDefinition(
        /**
         * Unique name for a check, that is also its identifier.
         */
        val name: String,

        /**
         * Description of a check and why it failed.
         * May be empty (null).
         */
        val description: ((ctx: CampgroundContext) -> String)? = null,

        /**
         * Based on data used to decide this check's health, what is the severity of this check.
         */
        val severity: (ctx: CampgroundContext) -> CheckResult.Severity,

        /**
         * Should this check fail. Return false to make this check as healthy.
         * Returning null will make this check fail.
         */
        val isFailure: (ctx: CampgroundContext) -> Boolean?,
    )
}
