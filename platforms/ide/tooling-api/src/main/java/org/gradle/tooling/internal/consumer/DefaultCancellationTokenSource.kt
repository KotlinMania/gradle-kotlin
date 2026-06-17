/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.CancellationTokenSource

class DefaultCancellationTokenSource : CancellationTokenSource {
    private val tokenImpl: CancellationTokenImpl

    init {
        tokenImpl = CancellationTokenImpl(DefaultBuildCancellationToken())
    }

    override fun cancel() {
        tokenImpl.token.cancel()
    }

    override fun token(): CancellationToken {
        return tokenImpl
    }

    private class CancellationTokenImpl(private val token: DefaultBuildCancellationToken) : CancellationToken, CancellationTokenInternal {
        override fun getToken(): BuildCancellationToken {
            return token
        }

        override fun isCancellationRequested(): Boolean {
            return token.isCancellationRequested()
        }
    }
}
