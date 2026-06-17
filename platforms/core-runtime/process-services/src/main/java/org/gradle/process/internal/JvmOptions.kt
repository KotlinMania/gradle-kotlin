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

    @JvmField
    var host: String?

    @JvmField
    var port: Int

    @JvmField
    var isServer: Boolean

    @JvmField
    var isSuspend: Boolean

    class DefaultJvmDebugSpec : JvmDebugSpec {
        private var enabled = false
        private var host: String? = null
        private var port = 5005
        private var server = true
        private var suspend = true

        override fun isEnabled(): Boolean {
            return enabled
        }

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override fun getHost(): String? {
            return host
        }

        override fun setHost(host: String?) {
            this.host = host
        }

        override fun getPort(): Int {
            return port
        }

        override fun setPort(port: Int) {
            this.port = port
        }

        override fun isServer(): Boolean {
            return server
        }

        override fun setServer(server: Boolean) {
            this.server = server
        }

        override fun isSuspend(): Boolean {
            return suspend
        }

        override fun setSuspend(suspend: Boolean) {
            this.suspend = suspend
        }
    }

    class JavaDebugOptionsBackedSpec(private val delegate: JavaDebugOptions) : JvmDebugSpec {
        override fun isEnabled(): Boolean {
            return delegate.getEnabled().get()
        }

        override fun setEnabled(enabled: Boolean) {
            delegate.getEnabled().set(enabled)
        }

        override fun getHost(): String? {
            return delegate.getHost().getOrNull()
        }

        override fun setHost(host: String?) {
            delegate.getHost().set(host)
        }

        override fun getPort(): Int {
            return delegate.getPort().get()
        }

        override fun setPort(port: Int) {
            delegate.getPort().set(port)
        }

        override fun isServer(): Boolean {
            return delegate.getServer().get()
        }

        override fun setServer(server: Boolean) {
            delegate.getServer().set(server)
        }

        override fun isSuspend(): Boolean {
            return delegate.getSuspend().get()
        }

        override fun setSuspend(suspend: Boolean) {
            delegate.getSuspend().set(suspend)
        }
    }
}
