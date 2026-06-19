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
package org.gradle.process.internal.health.memory

import org.gradle.internal.Cast
import org.jspecify.annotations.NullMarked
import java.lang.management.ManagementFactory
import javax.management.AttributeNotFoundException
import javax.management.InstanceNotFoundException
import javax.management.MBeanException
import javax.management.MalformedObjectNameException
import javax.management.ObjectName
import javax.management.ReflectionException

@NullMarked
class DefaultMBeanAttributeProvider : MBeanAttributeProvider {
    override fun <T> getMbeanAttribute(mbean: String, attribute: String, type: Class<T>): T {
        val rootCause: Exception?
        try {
            val objectName = ObjectName(mbean)
            @Suppress("UNCHECKED_CAST")
            return Cast.cast<T?, Any>(type as Class<T?>, ManagementFactory.getPlatformMBeanServer().getAttribute(objectName, attribute))!!
        } catch (e: InstanceNotFoundException) {
            rootCause = e
        } catch (e: ReflectionException) {
            rootCause = e
        } catch (e: MalformedObjectNameException) {
            rootCause = e
        } catch (e: MBeanException) {
            rootCause = e
        } catch (e: AttributeNotFoundException) {
            rootCause = e
        }
        throw UnsupportedOperationException("(" + mbean + ")." + attribute + " is unsupported on this JVM.", rootCause)
    }
}
