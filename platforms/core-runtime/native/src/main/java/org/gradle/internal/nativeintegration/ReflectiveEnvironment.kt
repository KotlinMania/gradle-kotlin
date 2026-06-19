/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.nativeintegration

import org.gradle.internal.os.OperatingSystem

/**
 * Uses reflection to update private environment state
 */
class ReflectiveEnvironment {
    fun unsetenv(name: String?) {
        val map = this.env
        map.remove(name)
        if (OperatingSystem.current().isWindows) {
            val env2 = this.windowsEnv
            env2.remove(name)
        }
    }

    fun setenv(name: String?, value: String?) {
        val map = this.env
        map.put(name, value)
        if (OperatingSystem.current().isWindows) {
            val env2 = this.windowsEnv
            env2.put(name, value)
        }
    }

    private val windowsEnv: MutableMap<String?, String?>
        /**
         * Windows keeps an extra map with case insensitive keys. The map is used when the user calls [System.getenv]
         */
        get() {
            try {
                val sc = Class.forName("java.lang.ProcessEnvironment")
                val caseinsensitive = sc.getDeclaredField("theCaseInsensitiveEnvironment")
                caseinsensitive.setAccessible(true)
                val result = caseinsensitive.get(null) as MutableMap<String?, String?>
                return result
            } catch (e: Exception) {
                throw NativeIntegrationException("Unable to get mutable windows case insensitive environment map", e)
            }
        }

    private val env: MutableMap<String?, String?>
        get() {
            try {
                val theUnmodifiableEnvironment = System.getenv()
                val cu: Class<*> = theUnmodifiableEnvironment.javaClass
                val m = cu.getDeclaredField("m")
                m.setAccessible(true)
                val result = m.get(theUnmodifiableEnvironment) as MutableMap<String?, String?>
                return result
            } catch (e: Exception) {
                throw NativeIntegrationException("Unable to get mutable environment map", e)
            }
        }
}
