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
package org.gradle.internal.instrumentation.api.groovybytecode

import org.jspecify.annotations.NullMarked

/**
 * A call interceptor that can also tell if it is going to intercept a call to a method based on the argument types, not the specific argument values.
 */
@NullMarked
interface SignatureAwareCallInterceptor {
    /**
     * @param receiverClass the class that the method is invoked on; the owner class if static
     * @param argumentClasses the classes of each argument passed to a call, with `null` elements for `null` values
     * @return either a [SignatureMatch] that also contains additional information about the matched signature
     * or null if the interceptor does not match this signature
     */
    fun matchesMethodSignature(receiverClass: Class<*>, argumentClasses: Array<Class<*>>, isStatic: Boolean): SignatureMatch?

    @NullMarked
    class SignatureMatch(@JvmField val isVararg: Boolean, @JvmField val argClasses: Array<Class<*>>)
}
