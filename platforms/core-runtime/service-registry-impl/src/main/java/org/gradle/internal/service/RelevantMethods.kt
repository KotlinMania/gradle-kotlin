/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.service

import org.gradle.util.internal.ArrayUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class RelevantMethods private constructor(val decorators: MutableList<ServiceMethod?>?, val factories: MutableList<ServiceMethod?>?, val configurers: MutableList<ServiceMethod?>?) {
    private class RelevantMethodsBuilder(type: Class<out ServiceRegistrationProvider?>) {
        private val type: Class<*>
        private val decorators: MutableList<ServiceMethod?> = ArrayList<ServiceMethod?>()
        private val factories: MutableList<ServiceMethod?> = ArrayList<ServiceMethod?>()
        private val configurers: MutableList<ServiceMethod?> = ArrayList<ServiceMethod?>()

        private val seen: MutableSet<String?> = HashSet<String?>()

        init {
            this.type = type
        }

        fun build(): RelevantMethods {
            var clazz = type
            while (clazz != Any::class.java && clazz != DefaultServiceRegistry::class.java) {
                for (method in clazz.getDeclaredMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue
                    }
                    addMethod(method)
                }
                clazz = clazz.getSuperclass()
            }
            return RelevantMethods(decorators, factories, configurers)
        }

        fun addMethod(method: Method) {
            if (method.getName() == "configure") {
                if (method.getReturnType() != Void.TYPE) {
                    throw ServiceValidationException(String.format("Method %s.%s() must return void.", type.getName(), method.getName()))
                }
                add(configurers, method)
            } else if (method.getName().startsWith("create") || method.getName().startsWith("decorate")) {
                if (method.getAnnotation<Provides?>(Provides::class.java) == null) {
                    throw ServiceValidationException(String.format("Method %s.%s() must be annotated with @Provides.", type.getName(), method.getName()))
                }
                if (method.getReturnType() == Void.TYPE) {
                    throw ServiceValidationException(String.format("Method %s.%s() must not return void.", type.getName(), method.getName()))
                }
                if (takesReturnTypeAsParameter(method)) {
                    add(decorators, method)
                } else {
                    add(factories, method)
                }
            } else if (method.getAnnotation<Provides?>(Provides::class.java) != null) {
                throw ServiceValidationException(String.format("Non-factory method %s.%s() must not be annotated with @Provides.", type.getName(), method.getName()))
            }
        }

        fun add(builder: MutableList<ServiceMethod?>, method: Method) {
            val signature = StringBuilder()
            signature.append(method.getName())
            for (parameterType in method.getParameterTypes()) {
                signature.append(",")
                signature.append(parameterType.getName())
            }
            if (seen.add(signature.toString())) {
                builder.add(SERVICE_METHOD_FACTORY.toServiceMethod(method))
            }
        }

        companion object {
            private fun takesReturnTypeAsParameter(method: Method): Boolean {
                return ArrayUtils.contains<Class<*>?>(method.getParameterTypes(), method.getReturnType())
            }
        }
    }

    companion object {
        private val METHODS_CACHE: ConcurrentMap<Class<*>?, RelevantMethods?> = ConcurrentHashMap<Class<*>?, RelevantMethods?>()
        private val SERVICE_METHOD_FACTORY: ServiceMethodFactory = DefaultServiceMethodFactory()

        fun getMethods(type: Class<out ServiceRegistrationProvider?>): RelevantMethods {
            var relevantMethods: RelevantMethods? = METHODS_CACHE.get(type)
            if (relevantMethods == null) {
                relevantMethods = RelevantMethodsBuilder(type).build()
                METHODS_CACHE.putIfAbsent(type, relevantMethods)
            }
            return relevantMethods
        }
    }
}
