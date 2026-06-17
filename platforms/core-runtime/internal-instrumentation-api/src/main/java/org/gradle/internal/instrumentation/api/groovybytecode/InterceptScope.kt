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

import java.util.Objects

/**
 * A scope for the CallInterceptor. It defines what methods/properties or constructors the given
 * CallInterceptor is interested in. Use static methods to obtain instances of this class.
 */
abstract class InterceptScope protected constructor(protected val descriptorStringPrefix: String) {

    abstract val callSiteName: String?
    abstract val targetDescription: String?

    override fun toString(): String {
        return "InterceptScope{$descriptorStringPrefix $targetDescription}"
    }

    private class NamedMemberScope(
        private val memberName: String,
        private val prefix: String
    ) : InterceptScope(prefix) {
        override val callSiteName: String?
            get() = memberName

        override val targetDescription: String?
            get() = memberName

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as NamedMemberScope
            return prefix == that.prefix && memberName == that.memberName
        }

        override fun hashCode(): Int {
            return Objects.hash(prefix, memberName)
        }
    }

    private class ConstructorScope(private val constructorClass: Class<*>) : InterceptScope("call constructor") {
        override val callSiteName: String
            get() = "<\$constructor$>"

        override val targetDescription: String
            get() = constructorClass.getName()

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ConstructorScope
            return constructorClass == that.constructorClass
        }

        override fun hashCode(): Int {
            return Objects.hash(descriptorStringPrefix, constructorClass)
        }
    }

    companion object {
        /**
         * The returned scope includes calls to all constructors of class `constructorClass`.
         *
         * @param constructorClass the class whose constructors are to be intercepted
         * @return the scope object
         */
        fun constructorsOf(constructorClass: Class<*>): InterceptScope {
            return ConstructorScope(constructorClass)
        }

        /**
         * The returned scope includes calls to all methods named `methodName`.
         *
         * @param methodName the name of the method to intercept
         * @return the scope object
         */
        @JvmStatic
        fun methodsNamed(methodName: String): InterceptScope {
            return NamedMemberScope(methodName, "call method")
        }

        /**
         * The returned scope includes reads of all properties named `propertyName`.
         * This scope doesn't include calls to the getter method corresponding to this property,
         * use additional explicit [.methodsNamed] scope to intercept that.
         *
         * @param propertyName the name of the property to intercept reads of
         * @return the scope object
         */
        @JvmStatic
        fun readsOfPropertiesNamed(propertyName: String): InterceptScope {
            return NamedMemberScope(propertyName, "get property")
        }

        /**
         * The returned scope includes writes of all properties named `propertyName`.
         * This scope doesn't include calls to the setter method corresponding to this property,
         * use additional explicit [.methodsNamed] scope to intercept that.
         *
         * @param propertyName the name of the property to intercept reads of
         * @return the scope object
         */
        @JvmStatic
        fun writesOfPropertiesNamed(propertyName: String): InterceptScope {
            return NamedMemberScope(propertyName, "set property")
        }
    }
}
