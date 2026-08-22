package xyz.deseteral.jaruga.network.ranger.trail.loader

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

inline fun <T : Any, reified ScriptConfigT : Any> loadUserConfiguration(configurationType: KClass<T>, file: File): T {
    require(file.isFile) { "Configuration script does not exist: ${file.absolutePath}" }

    val classLoader = Thread.currentThread().contextClassLoader
    val script = file.toScriptSource()
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
                "Could not compile configuration script ${file.absolutePath}:\n$diagnostics",
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
            "Could not evaluate configuration script ${file.absolutePath}.",
            returnValue.error,
        )

        is ResultValue.NotEvaluated,
        is ResultValue.Unit,
            -> throw IllegalArgumentException(
            "Configuration script must return ${configurationType.qualifiedName} as its final expression.",
        )
    }
}
