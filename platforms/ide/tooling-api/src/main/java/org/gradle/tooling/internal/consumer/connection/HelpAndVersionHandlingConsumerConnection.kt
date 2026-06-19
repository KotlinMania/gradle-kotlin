/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.gradle.tooling.internal.consumer.TestExecutionRequest
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.Help
import org.gradle.tooling.model.internal.Exceptions
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

abstract class HelpAndVersionHandlingConsumerConnection(delegate: ConnectionVersion4, providerMetaData: VersionDetails) : AbstractConsumerConnection(delegate, providerMetaData) {
    override fun <T> run(type: Class<T?>, operationParameters: ConsumerOperationParameters): T? {
        if (operationParameters.containsHelpOrVersionArgs() && type == Void::class.java) {
            // For task execution, handle --help/--version and skip task execution
            return handleHelpOrVersion<T?>(type, operationParameters)
        }
        // For model requests, remove help/version args and continue
        return modelProducer!!.produceModel<T?>(type, removeHelpVersionArgs(operationParameters))
    }

    override fun <T> run(action: BuildAction<T?>, operationParameters: ConsumerOperationParameters): T? {
        return actionRunner!!.run<T?>(action, removeHelpVersionArgs(operationParameters))
    }

    override fun run(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters) {
        doRun(phasedBuildAction, removeHelpVersionArgs(operationParameters))
    }

    protected open fun doRun(phasedBuildAction: PhasedBuildAction, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, versionDetails.version!!, "4.8")
    }

    override fun runTests(testExecutionRequest: TestExecutionRequest, operationParameters: ConsumerOperationParameters) {
        doRunTests(testExecutionRequest, removeHelpVersionArgs(operationParameters))
    }

    protected open fun doRunTests(testExecutionRequest: TestExecutionRequest, operationParameters: ConsumerOperationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.entryPointName, versionDetails.version!!, "2.6")
    }

    private fun <T> handleHelpOrVersion(type: Class<T?>, operationParameters: ConsumerOperationParameters): T? {
        val containsHelpArg = operationParameters.containsHelpArg()
        val containsVersionArg = operationParameters.containsVersionArg()
        val containsShowVersionArg = operationParameters.containsShowVersionArg()

        val cleanParams = operationParameters.withoutHelpOrVersionArgs()
        val modelParams = cleanParams.withNoTasks()

        // help was requested: print help and omit producing the model
        if (containsHelpArg) {
            queryAndPrintHelp(operationParameters, modelParams)
            return null
        }

        // version was requested: print version information
        if (containsVersionArg || containsShowVersionArg) {
            queryAndPrintVersion(operationParameters, modelParams)
        }

        // if version was requested via --version, omit producing the model
        if (containsVersionArg) {
            return null
        }

        // if version was requested via --show-version, proceed to produce the model
        if (containsShowVersionArg) {
            return modelProducer!!.produceModel<T?>(type, cleanParams)
        }

        return null
    }

    private fun queryAndPrintHelp(operationParameters: ConsumerOperationParameters, modelParams: ConsumerOperationParameters) {
        val standardOutput = operationParameters.standardOutput
        if (versionDetails.supportsHelpToolingModel()) {
            val helpModel = modelProducer!!.produceModel<Help>(Help::class.java as Class<Help?>, modelParams)
            print(standardOutput, helpModel!!.renderedText!!)
        } else {
            val renderedText = HelpModelCompatibilityHelper.getRenderedText(versionDetails.version!!)
            print(standardOutput, renderedText)
        }
    }

    private fun queryAndPrintVersion(operationParameters: ConsumerOperationParameters, modelParams: ConsumerOperationParameters) {
        val env = modelProducer!!.produceModel<BuildEnvironment>(BuildEnvironment::class.java as Class<BuildEnvironment?>, modelParams)
        val standardOutput = operationParameters.standardOutput
        val output = env!!.versionInfo
        print(standardOutput, output!!)
    }

    companion object {
        private fun print(stdOut: OutputStream, content: String) {
            try {
                if (stdOut != null) {
                    stdOut.write(content.toByteArray(StandardCharsets.UTF_8))
                }
            } catch (e: IOException) {
                throw RuntimeException("Cannot write to stdout", e)
            }
        }

        private fun removeHelpVersionArgs(parameters: ConsumerOperationParameters): ConsumerOperationParameters {
            if (!parameters.containsHelpOrVersionArgs()) {
                return parameters
            }
            print(parameters.standardError, "The Tooling API does not support --help, --version or --show-version arguments for this operation. These arguments have been ignored.")
            return parameters.withoutHelpOrVersionArgs()
        }
    }
}
