/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.event.ListenerNotificationException
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.Supplier
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException

class ConnectionExceptionTransformer @JvmOverloads constructor(
    private val messageProvider: ConnectionFailureMessageProvider, private val failures: Supplier<MutableList<Failure?>?>? = object : Supplier<MutableList<Failure?>?> {
        override fun get(): MutableList<Failure?> {
            return mutableListOf<Failure?>()
        }
    }
) {
    fun transform(failure: Throwable): GradleConnectionException {
        if (failure is InternalUnsupportedBuildArgumentException) {
            return UnsupportedBuildArgumentException(
                (connectionFailureMessage(failure)
                        + "\n" + failure.message), failure
            )
        } else if (failure is UnsupportedOperationConfigurationException) {
            return UnsupportedOperationConfigurationException(
                (connectionFailureMessage(failure)
                        + "\n" + failure.message), failure.cause
            )
        } else if (failure is GradleConnectionException) {
            return failure
        } else if (failure is InternalBuildCancelledException) {
            return BuildCancelledException(connectionFailureMessage(failure), failure.cause)
        } else if (failure is InternalTestExecutionException) {
            return TestExecutionException(connectionFailureMessage(failure), failure.cause, failures)
        } else if (failure is BuildExceptionVersion1) {
            return BuildException(connectionFailureMessage(failure), failure.cause, failures)
        } else if (failure is ListenerNotificationException) {
            return ListenerFailedException(connectionFailureMessage(failure), failure.getCauses())
        } else {
            return GradleConnectionException(connectionFailureMessage(failure), failure, failures)
        }
    }

    private fun connectionFailureMessage(failure: Throwable?): String? {
        return messageProvider.getConnectionFailureMessage(failure)
    }

    interface ConnectionFailureMessageProvider {
        fun getConnectionFailureMessage(failure: Throwable?): String?
    }
}
