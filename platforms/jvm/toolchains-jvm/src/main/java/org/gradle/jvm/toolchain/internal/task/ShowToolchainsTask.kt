/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.jvm.toolchain.internal.task

import com.google.common.base.Strings
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Task
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.diagnostics.internal.ToolchainReportRenderer
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry.toolchains
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput.println
import org.gradle.internal.logging.text.StyledTextOutputFactory.create
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import javax.inject.Inject

@UntrackedTask(because = "Produces only non-cacheable console output")
abstract class ShowToolchainsTask : DefaultTask() {
    private val toolchainRenderer = ToolchainReportRenderer()

    init {
        getOutputs().upToDateWhen(SerializableLambdas.spec<Task>(SerializableLambdas.SerializableSpec { element: Task -> false }))
    }

    @TaskAction
    fun showToolchains() {
        val output = this.textOutputFactory.create(javaClass)
        toolchainRenderer.setOutput(output)
        output!!.println()
        val toolchains = allReportableToolchains()
        val validToolchains: MutableList<JvmToolchainMetadata> = validToolchains(toolchains)
        val invalidToolchains: MutableList<JvmToolchainMetadata> = invalidToolchains(toolchains)
        printOptions(output)
        validToolchains.forEach(Consumer { toolchain: JvmToolchainMetadata? -> toolchainRenderer.printDetectedToolchain(toolchain) })
        toolchainRenderer.printInvalidToolchains(invalidToolchains)
    }

    private fun printOptions(output: StyledTextOutput) {
        val detectionEnabled = this.toolchainConfiguration.isAutoDetectEnabled
        val downloadEnabled = this.toolchainConfiguration.isDownloadEnabled
        output.withStyle(StyledTextOutput.Style.Identifier)!!.println(" + Options")
        output.withStyle(StyledTextOutput.Style.Normal)!!.format("     | %s", Strings.padEnd("Auto-detection:", 20, ' '))
        output.withStyle(StyledTextOutput.Style.Description)!!.println(if (detectionEnabled) "Enabled" else "Disabled")
        output.withStyle(StyledTextOutput.Style.Normal)!!.format("     | %s", Strings.padEnd("Auto-download:", 20, ' '))
        output.withStyle(StyledTextOutput.Style.Description)!!.println(if (downloadEnabled) "Enabled" else "Disabled")
        output.println()
    }

    private fun allReportableToolchains(): MutableList<JvmToolchainMetadata> {
        return this.installationRegistry
            .toolchains()
    }

    @get:Inject
    protected abstract val installationRegistry: JavaInstallationRegistry?

    @get:Inject
    protected abstract val textOutputFactory: StyledTextOutputFactory?

    @get:Inject
    protected abstract val providerFactory: ProviderFactory?

    @get:Inject
    protected abstract val toolchainConfiguration: ToolchainConfiguration?

    companion object {
        private val TOOLCHAIN_COMPARATOR: Comparator<JvmToolchainMetadata> = Comparator
            .comparing<JvmToolchainMetadata, JavaVersion>(Function { t: JvmToolchainMetadata -> t.metadata!!.languageVersion })
            .thenComparing<U>(Function { t: JvmToolchainMetadata -> t.metadata!!.displayName })

        private fun invalidToolchains(toolchains: MutableList<JvmToolchainMetadata>): MutableList<JvmToolchainMetadata> {
            return toolchains.stream().filter { t: JvmToolchainMetadata? -> !Companion.isValidToolchain(t!!) }.collect(Collectors.toList())
        }

        private fun validToolchains(toolchains: MutableCollection<JvmToolchainMetadata>): MutableList<JvmToolchainMetadata> {
            return toolchains.stream().filter { t: JvmToolchainMetadata? -> Companion.isValidToolchain(t!!) }.sorted(TOOLCHAIN_COMPARATOR).collect(Collectors.toList())
        }

        private fun isValidToolchain(t: JvmToolchainMetadata): Boolean {
            return t.metadata!!.isValidInstallation
        }
    }
}
