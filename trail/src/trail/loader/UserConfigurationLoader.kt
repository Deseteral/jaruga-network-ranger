package xyz.deseteral.jaruga.ranger.trail.loader

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import xyz.deseteral.jaruga.ranger.trail.config.CampgroundConfig
import xyz.deseteral.jaruga.ranger.trail.config.LookoutConfig
import java.io.File
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

fun loadCampgroundConfig(path: Path): CampgroundConfig =
    loadUserConfiguration<CampgroundConfig, CampgroundConfigScript>(CampgroundConfig::class, path)

fun loadLookoutConfig(path: Path): LookoutConfig =
    loadUserConfiguration<LookoutConfig, LookoutConfigScript>(LookoutConfig::class, path)

private inline fun <T : Any, reified ScriptConfigT : Any> loadUserConfiguration(
    configurationType: KClass<T>,
    path: Path,
): T {
    require(SystemFileSystem.metadataOrNull(path)?.isRegularFile == true) {
        "Configuration script does not exist: $path"
    }

    val classLoader = Thread.currentThread().contextClassLoader
    val script = File(path.toString()).toScriptSource()
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<ScriptConfigT>()
    val evaluationConfiguration = createJvmEvaluationConfigurationFromTemplate<ScriptConfigT> {
        jvm {
            baseClassLoader(classLoader)
        }
    }

    val result = BasicJvmScriptingHost().eval(script, compilationConfiguration, evaluationConfiguration)

    val returnValue = when (result) {
        is ResultWithDiagnostics.Failure -> {
            val diagnostics = result.reports.joinToString("\n") { report -> "${report.severity}: ${report.message}" }
            throw IllegalArgumentException(
                "Could not compile configuration script $path:\n$diagnostics",
            )
        }

        is ResultWithDiagnostics.Success -> result.value.returnValue
    }

    return when (returnValue) {
        is ResultValue.Value -> {
            val value = returnValue.value
            require(configurationType.isInstance(value)) {
                "Configuration script must return ${configurationType.qualifiedName}, but returned ${value?.let { it::class.qualifiedName } ?: "null"}."
            }
            configurationType.java.cast(value)
        }

        is ResultValue.Error -> throw IllegalArgumentException(
            "Could not evaluate configuration script $path.",
            returnValue.error,
        )

        is ResultValue.NotEvaluated,
        is ResultValue.Unit,
            -> throw IllegalArgumentException(
            "Configuration script must return ${configurationType.qualifiedName} as its final expression.",
        )
    }
}
