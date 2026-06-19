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
package org.gradle.internal.authentication

import org.gradle.authentication.Authentication
import org.gradle.internal.Cast.uncheckedNonnullCast
import java.util.Collections

class DefaultAuthenticationSchemeRegistry : AuthenticationSchemeRegistry {
    private val registeredSchemes: MutableMap<Class<out Authentication>, Class<out Authentication>> = HashMap<Class<out Authentication>, Class<out Authentication>>()

    override fun <T : Authentication> registerScheme(type: Class<T>, implementationType: Class<out T>) {
        registeredSchemes.put(type, implementationType)
    }

    override fun <T : Authentication> getRegisteredSchemes(): MutableMap<Class<T>, Class<out T>> {
        return Collections.unmodifiableMap<Class<T>, Class<out T>>(uncheckedNonnullCast<MutableMap<out Class<T>, out Class<out T>>>(registeredSchemes))
    }
}
