package xyz.deseteral.jaruga.ranger.trail.loader

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClass

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
            updateClasspath(classpathFromClass(CampgroundConfigurationScript::class))
        }
    },
)
