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
package org.gradle.process.internal

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.process.JavaDebugOptions
import java.util.Objects
import javax.inject.Inject

class DefaultJavaDebugOptions @Inject constructor(objectFactory: ObjectFactory) : JavaDebugOptions {
    private val enabled: Property<Boolean?>
    private val host: Property<String?>
    private val port: Property<Int?>
    private val server: Property<Boolean?>
    private val suspend: Property<Boolean?>

    init {
        val defaultValues = JvmDebugSpec.DefaultJvmDebugSpec()
        this.enabled = objectFactory.property<Boolean?>(Boolean::class.java).convention(defaultValues.isEnabled())
        this.host = objectFactory.property<String?>(String::class.java).convention(defaultValues.getHost())
        this.port = objectFactory.property<Int?>(Int::class.java).convention(defaultValues.getPort())
        this.server = objectFactory.property<Boolean?>(Boolean::class.java).convention(defaultValues.isServer())
        this.suspend = objectFactory.property<Boolean?>(Boolean::class.java).convention(defaultValues.isSuspend())
    }

    override fun hashCode(): Int {
        return Objects.hash(getEnabled().get(), getHost().getOrNull(), getPort().get(), getServer().get(), getSuspend().get())
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultJavaDebugOptions
        return enabled.get() === that.enabled.get() && host.getOrNull() == that.host.getOrNull()
                && port.get() == that.port.get()
                && server.get() === that.server.get() && suspend.get() === that.suspend.get()
    }

    override fun getEnabled(): Property<Boolean?> {
        return enabled
    }

    @Optional
    override fun getHost(): Property<String?> {
        return host
    }

    override fun getPort(): Property<Int?> {
        return port
    }

    override fun getServer(): Property<Boolean?> {
        return server
    }

    override fun getSuspend(): Property<Boolean?> {
        return suspend
    }
}
