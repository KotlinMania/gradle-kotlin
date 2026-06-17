/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.tooling.events.test.internal

import org.gradle.tooling.internal.protocol.test.InternalDebugOptions
import java.io.Serializable

class DefaultDebugOptions : InternalDebugOptions, Serializable {
    private var port = -1

    override fun getPort(): Int {
        return this.port
    }

    fun setPort(port: Int) {
        this.port = port
    }

    override fun isDebugMode(): Boolean {
        return port > 0
    }
}
