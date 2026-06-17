/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.instrumentation.model

import java.util.function.Consumer

class CallInterceptionRequestImpl(
    private val interceptedCallable: CallableInfo?,
    private val implementationInfo: ImplementationInfo?,
    requestExtras: MutableList<RequestExtra?>
) : CallInterceptionRequest {
    private val requestExtras: RequestExtrasContainer

    init {
        this.requestExtras = RequestExtrasContainer()
        requestExtras.forEach(Consumer { extra: RequestExtra? -> this.requestExtras.add(extra) })
    }

    override fun getInterceptedCallable(): CallableInfo? {
        return interceptedCallable
    }

    override fun getImplementationInfo(): ImplementationInfo? {
        return implementationInfo
    }

    override fun getRequestExtras(): RequestExtrasContainer {
        return requestExtras
    }
}
