/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableSet
import org.gradle.internal.classpath.ClassPath
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.Failure
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.Supplier
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.util.internal.CollectionUtils.toList
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Arrays
import java.util.EnumSet

abstract class AbstractLongRunningOperation<T : AbstractLongRunningOperation<T?>?> protected constructor(protected val connectionParameters: ConnectionParameters) : LongRunningOperation {
    private val buildFailedProgressAdapter: BuildFailedProgressAdapter? = BuildFailedProgressAdapter()
    @JvmField
    protected val operationParamsBuilder: ConsumerOperationParameters.Builder

    init {
        operationParamsBuilder = ConsumerOperationParameters.builder()
        operationParamsBuilder.setCancellationToken(DefaultCancellationTokenSource().token())
    }

    protected abstract val `this`: T?

    protected val consumerOperationParameters: ConsumerOperationParameters
        get() {
            val connectionParameters = this.connectionParameters
            return operationParamsBuilder.setParameters(connectionParameters).build()
        }

    override fun withArguments(vararg arguments: String?): T? {
        operationParamsBuilder.setArguments(Companion.rationalizeInput<String>(arguments))
        return this.`this`
    }

    override fun withArguments(arguments: Iterable<String?>?): T? {
        operationParamsBuilder.setArguments(rationalizeInput<String>(arguments))
        return this.`this`
    }

    override fun addArguments(vararg arguments: String?): T? {
        operationParamsBuilder.addArguments(toList<String>(Preconditions.checkNotNull<Array<String?>?>(arguments)))
        return this.`this`
    }

    override fun addArguments(arguments: Iterable<String?>?): T? {
        operationParamsBuilder.addArguments(toList<String>(Preconditions.checkNotNull<Iterable<String?>?>(arguments)))
        return this.`this`
    }

    override fun setStandardOutput(outputStream: OutputStream): T? {
        operationParamsBuilder.setStdout(outputStream)
        return this.`this`
    }

    override fun setStandardError(outputStream: OutputStream): T? {
        operationParamsBuilder.setStderr(outputStream)
        return this.`this`
    }

    override fun setStandardInput(inputStream: InputStream): T? {
        operationParamsBuilder.setStdin(inputStream)
        return this.`this`
    }

    override fun setColorOutput(colorOutput: Boolean): T? {
        operationParamsBuilder.setColorOutput(colorOutput)
        return this.`this`
    }

    override fun setJavaHome(javaHome: File): T? {
        operationParamsBuilder.setJavaHome(javaHome)
        return this.`this`
    }

    override fun setJvmArguments(vararg jvmArguments: String?): T? {
        operationParamsBuilder.setBaseJvmArguments(Companion.rationalizeInput<String>(jvmArguments))
        return this.`this`
    }

    override fun setJvmArguments(jvmArguments: Iterable<String?>?): T? {
        operationParamsBuilder.setBaseJvmArguments(rationalizeInput<String>(jvmArguments))
        return this.`this`
    }

    override fun addJvmArguments(vararg jvmArguments: String?): T? {
        Preconditions.checkNotNull<Array<String?>?>(jvmArguments)
        operationParamsBuilder.addJvmArguments(Companion.rationalizeInput<String>(jvmArguments))
        return this.`this`
    }

    override fun addJvmArguments(jvmArguments: Iterable<String?>?): T? {
        Preconditions.checkNotNull<Iterable<String?>?>(jvmArguments)
        operationParamsBuilder.addJvmArguments(rationalizeInput<String>(jvmArguments))
        return this.`this`
    }

    override fun withSystemProperties(systemProperties: MutableMap<String?, String?>): T? {
        operationParamsBuilder.setSystemProperties(systemProperties)
        return this.`this`
    }

    override fun setEnvironmentVariables(envVariables: MutableMap<String?, String?>): T? {
        operationParamsBuilder.setEnvironmentVariables(envVariables)
        return this.`this`
    }

    override fun addProgressListener(listener: ProgressListener): T? {
        operationParamsBuilder.addProgressListener(listener)
        return this.`this`
    }

    override fun addProgressListener(listener: org.gradle.tooling.events.ProgressListener): T? {
        return addProgressListener(listener, EnumSet.allOf<OperationType?>(OperationType::class.java))
    }

    override fun addProgressListener(listener: org.gradle.tooling.events.ProgressListener, vararg operationTypes: OperationType?): T? {
        return addProgressListener(listener, ImmutableSet.copyOf<OperationType?>(operationTypes))
    }

    override fun addProgressListener(listener: org.gradle.tooling.events.ProgressListener, eventTypes: MutableSet<OperationType?>): T? {
        operationParamsBuilder.addProgressListener(listener, eventTypes)
        return this.`this`
    }

    override fun withCancellationToken(cancellationToken: CancellationToken?): T? {
        operationParamsBuilder.setCancellationToken(Preconditions.checkNotNull<CancellationToken?>(cancellationToken)!!)
        return this.`this`
    }

    override fun withDetailedFailure(): T? {
        operationParamsBuilder.addProgressListener(buildFailedProgressAdapter!!, EnumSet.of<OperationType?>(OperationType.ROOT))
        return this.`this`
    }

    /**
     * Specifies classpath URIs used for loading user-defined classes. This list is in addition to the default classpath.
     *
     * @param classpath Classpath URIs
     * @return this
     * @since 2.8
     */
    fun withInjectedClassPath(classpath: ClassPath): T? {
        operationParamsBuilder.setInjectedPluginClasspath(classpath)
        return this.`this`
    }

    protected fun createExceptionTransformer(messageProvider: ConnectionExceptionTransformer.ConnectionFailureMessageProvider?): ConnectionExceptionTransformer {
        return ConnectionExceptionTransformer(messageProvider, object : Supplier<MutableList<Failure?>?> {
            override fun get(): MutableList<Failure?> {
                return if (buildFailedProgressAdapter == null) mutableListOf<Failure?>() else buildFailedProgressAdapter.failures
            }
        })
    }

    companion object {
        protected fun <T> rationalizeInput(arguments: Array<T?>?): MutableList<T?>? {
            return if (arguments != null && arguments.size > 0) Arrays.asList<T?>(*arguments) else null
        }

        protected fun <T> rationalizeInput(arguments: Iterable<out T?>?): MutableList<T?>? {
            return if (arguments != null && arguments.iterator().hasNext()) toList<T?>(arguments) else null
        }
    }
}
