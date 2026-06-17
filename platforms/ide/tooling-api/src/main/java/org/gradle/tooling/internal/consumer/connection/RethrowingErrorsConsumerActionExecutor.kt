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
package org.gradle.tooling.internal.consumer.connection

class RethrowingErrorsConsumerActionExecutor(private val delegate: ConsumerActionExecutor) : ConsumerActionExecutor {
    override fun stop() {
        delegate.stop()
    }

    override fun getDisplayName(): String {
        return delegate.getDisplayName()
    }

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(action: ConsumerAction<T?>): T? {
        val result = delegate.run<T?>(action)
        action.getParameters().getBuildProgressListener().rethrowErrors()
        action.getParameters().getStreamedValueListener().rethrowErrors()
        return result
    }

    override fun disconnect() {
        delegate.disconnect()
    }
}
