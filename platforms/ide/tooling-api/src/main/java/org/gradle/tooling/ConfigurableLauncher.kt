/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling

import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * A `ConfigurableLauncher` allows you to configure a long running operation.
 *
 * @param <T> the ConfigurableLauncher implementation to return as part of the fluent API.
 * @since 2.6
</T> */
interface ConfigurableLauncher<T : ConfigurableLauncher<T?>?> : LongRunningOperation {
    /**
     * {@inheritDoc}
     * @since 1.0
     */
    override fun withArguments(vararg arguments: String?): T?

    /**
     * {@inheritDoc}
     * @since 2.6
     */
    override fun withArguments(arguments: Iterable<String?>?): T?

    /**
     * {@inheritDoc}
     * @since 5.0
     */
    override fun addArguments(vararg arguments: String?): T?

    /**
     * {@inheritDoc}
     * @since 5.0
     */
    override fun addArguments(arguments: Iterable<String?>?): T?

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    override fun setStandardOutput(outputStream: OutputStream?): T?

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    override fun setStandardError(outputStream: OutputStream?): T?

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    override fun setColorOutput(colorOutput: Boolean): T?

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-7
     */
    override fun setStandardInput(inputStream: InputStream?): T?

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-8
     */
    override fun setJavaHome(javaHome: File?): T?

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-9
     */
    override fun setJvmArguments(vararg jvmArguments: String?): T?

    /**
     * {@inheritDoc}
     * @since 2.6
     */
    override fun setJvmArguments(jvmArguments: Iterable<String?>?): T?

    /**
     * {@inheritDoc}
     * @since 5.0
     */
    override fun addJvmArguments(vararg jvmArguments: String?): T?

    /**
     * {@inheritDoc}
     * @since 7.6
     */
    override fun withSystemProperties(systemProperties: MutableMap<String?, String?>?): T?

    /**
     * {@inheritDoc}
     * @since 5.0
     */
    override fun addJvmArguments(jvmArguments: Iterable<String?>?): T?

    /**
     * {@inheritDoc}
     * @since 3.5
     */
    override fun setEnvironmentVariables(envVariables: MutableMap<String?, String?>?): T?

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    override fun addProgressListener(listener: ProgressListener?): T?

    /**
     * {@inheritDoc}
     * @since 2.5
     */
    override fun addProgressListener(listener: ProgressListener?): T?

    /**
     * {@inheritDoc}
     * @since 2.5
     */
    override fun addProgressListener(listener: ProgressListener?, eventTypes: MutableSet<OperationType?>?): T?

    /**
     * {@inheritDoc}
     * @since 2.6
     */
    override fun addProgressListener(listener: ProgressListener?, vararg operationTypes: OperationType?): T?

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    override fun withCancellationToken(cancellationToken: CancellationToken?): T?

    /**
     * {@inheritDoc}
     * @since 8.12
     */
    override fun withDetailedFailure(): T?
}
