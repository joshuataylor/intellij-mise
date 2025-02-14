package com.github.l34130.mise.idea.run

import com.github.l34130.mise.core.MiseHelper
import com.github.l34130.mise.core.run.MiseRunConfigurationSettingsEditor
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import java.util.concurrent.ConcurrentHashMap

class MiseIdeaRunConfigurationExtension : RunConfigurationExtension() {
    // Used for cleanup the configuration after the execution has ended.
    private val runningProcessEnvs = ConcurrentHashMap<Project, Map<String, String>>()

    override fun getEditorTitle(): String = MiseRunConfigurationSettingsEditor.EDITOR_TITLE

    override fun <P : RunConfigurationBase<*>> createEditor(configuration: P): SettingsEditor<P> =
        MiseRunConfigurationSettingsEditor(configuration.project)

    override fun getSerializationId(): String = MiseRunConfigurationSettingsEditor.SERIALIZATION_ID

    override fun readExternal(
        runConfiguration: RunConfigurationBase<*>,
        element: Element,
    ) {
        MiseRunConfigurationSettingsEditor.readExternal(runConfiguration, element)
    }

    override fun writeExternal(
        runConfiguration: RunConfigurationBase<*>,
        element: Element,
    ) {
        MiseRunConfigurationSettingsEditor.writeExternal(runConfiguration, element)
    }

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
    ) {
        val envVars = MiseHelper.getMiseEnvVarsOrNotify(configuration, params::getWorkingDirectory)
        params.env = params.env + envVars

        // Gradle support (and external system configuration)
        // When user double-clicks the Task in the Gradle tool window.
        if (configuration is ExternalSystemRunConfiguration) {
            runningProcessEnvs[configuration.project] = configuration.settings.env.toMap()
            configuration.settings.env = configuration.settings.env + envVars
        }
    }

    override fun attachToProcess(
        configuration: RunConfigurationBase<*>,
        handler: ProcessHandler,
        runnerSettings: RunnerSettings?,
    ) {
        if (configuration is ExternalSystemRunConfiguration) {
            val envsToRestore = runningProcessEnvs.remove(configuration.project) ?: return

            handler.addProcessListener(
                object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) {
                        configuration.settings.env.apply {
                            clear()
                            putAll(envsToRestore)
                        }
                    }
                },
            )
        }
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

    override fun isEnabledFor(
        applicableConfiguration: RunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
    ): Boolean = true
}
