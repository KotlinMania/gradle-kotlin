/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.process.internal

import org.gradle.process.JavaDebugOptions
import org.jspecify.annotations.NullMarked

@NullMarked
interface JvmDebugSpec {
    var isEnabled: Boolean

    var host: String?

    var port: Int

    var isServer: Boolean

    var isSuspend: Boolean

    class DefaultJvmDebugSpec : JvmDebugSpec {
        override var isEnabled = false
        override var host: String? = null
        override var port = 5005
        override var isServer = true
        override var isSuspend = true
    }

    class JavaDebugOptionsBackedSpec(private val delegate: JavaDebugOptions) : JvmDebugSpec {
        override var isEnabled: Boolean
            get() = delegate.getEnabled().get()
            set(enabled) = delegate.getEnabled().set(enabled)

        override var host: String?
            get() = delegate.getHost().getOrNull()
            set(host) = delegate.getHost().set(host)

        override var port: Int
            get() = delegate.getPort().get()
            set(port) = delegate.getPort().set(port)

        override var isServer: Boolean
            get() = delegate.getServer().get()
            set(server) = delegate.getServer().set(server)

        override var isSuspend: Boolean
            get() = delegate.getSuspend().get()
            set(suspend) = delegate.getSuspend().set(suspend)
    }
}
