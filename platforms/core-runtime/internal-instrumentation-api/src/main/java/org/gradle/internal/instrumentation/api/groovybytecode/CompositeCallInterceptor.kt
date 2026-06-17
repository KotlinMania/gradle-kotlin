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

import java.util.Collections

class CompositeCallInterceptor(private val first: CallInterceptor, private val second: CallInterceptor) : AbstractCallInterceptor(), SignatureAwareCallInterceptor, PropertyAwareCallInterceptor {
    @Throws(Throwable::class)
    override fun intercept(invocation: Invocation, consumer: String): Any? {
        return first.intercept(object : Invocation {
            override fun getReceiver(): Any? {
                return invocation.getReceiver()
            }

            override fun getArgsCount(): Int {
                return invocation.getArgsCount()
            }

            override fun getArgument(pos: Int): Any? {
                return invocation.getArgument(pos)
            }

            @Throws(Throwable::class)
            override fun callNext(): Any? {
                return second.intercept(invocation, consumer)
            }
        }, consumer)
    }

    override fun getInterceptScopes(): MutableSet<InterceptScope> {
        val union: MutableSet<InterceptScope> = LinkedHashSet<InterceptScope>()
        union.addAll(first.getInterceptScopes())
        union.addAll(second.getInterceptScopes())
        return Collections.unmodifiableSet<InterceptScope>(union)
    }

    override fun matchesProperty(receiverClass: Class<*>): Class<*>? {
        var typeOfProperty: Class<*>? = null
        if (first is PropertyAwareCallInterceptor) {
            typeOfProperty = (first as PropertyAwareCallInterceptor).matchesProperty(receiverClass)
        }
        if (typeOfProperty == null && second is PropertyAwareCallInterceptor) {
            typeOfProperty = (second as PropertyAwareCallInterceptor).matchesProperty(receiverClass)
        }
        return typeOfProperty
    }

    override fun matchesMethodSignature(receiverClass: Class<*>, argumentClasses: Array<Class<*>>, isStatic: Boolean): SignatureAwareCallInterceptor.SignatureMatch? {
        var signatureMatch: SignatureAwareCallInterceptor.SignatureMatch? = null
        if (first is SignatureAwareCallInterceptor) {
            signatureMatch = (first as SignatureAwareCallInterceptor).matchesMethodSignature(receiverClass, argumentClasses, isStatic)
        }
        if (signatureMatch == null && second is SignatureAwareCallInterceptor) {
            signatureMatch = (second as SignatureAwareCallInterceptor).matchesMethodSignature(receiverClass, argumentClasses, isStatic)
        }
        return signatureMatch
    }
}
