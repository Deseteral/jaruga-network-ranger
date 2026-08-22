package xyz.deseteral.jaruga.ranger.trail.loader

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    displayName = "Campground config",
    fileExtension = "campground.kts",
    compilationConfiguration = UserConfigurationScriptCompilationConfiguration::class,
)
abstract class CampgroundConfigurationScript

@KotlinScript(
    displayName = "Lookout config",
    fileExtension = "lookout.kts",
    compilationConfiguration = UserConfigurationScriptCompilationConfiguration::class,
)
abstract class LookoutConfigurationScript

object UserConfigurationScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
    },
)
