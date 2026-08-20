package xyz.deseteral.jaruga.network.ranger.configuration

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    fileExtension = "configuration.kts",
    compilationConfiguration = UserConfigurationScriptCompilationConfiguration::class,
)
abstract class UserConfigurationScript

object UserConfigurationScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
    },
)
