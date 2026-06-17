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
package org.gradle.tooling.internal.consumer.connection

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Proxy for generating instances of the given parameter interface.
 *
 *
 * These proxies do not support any kind of nesting.
 */
class ToolingParameterProxy : InvocationHandler {
    private val properties: MutableMap<String, Any> = HashMap<String, Any>()

    @Throws(Throwable::class)
    override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any {
        if (isSetter(method)) {
            properties.put(getPropertyName(method.getName()), args[0])
        } else if (isGetter(method)) {
            return properties.get(getPropertyName(method.getName()))!!
        }
        return null
    }

    companion object {
        /**
         * Check if the given interface can be instantiated by this proxy.
         *
         *
         * If this validation is modified, also update the javadocs for [org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder].
         */
        fun validateParameter(clazz: Class<*>) {
            if (!clazz.isInterface()) {
                throwParameterValidationError(clazz, "It must be an interface.")
            }

            val setters: MutableMap<String, Class<*>> = HashMap<String, Class<*>>()
            val getters: MutableMap<String, Class<*>> = HashMap<String, Class<*>>()

            for (method in clazz.getDeclaredMethods()) {
                if (isGetter(method)) {
                    val property: String = getPropertyName(method.getName())
                    if (getters.containsKey(property)) {
                        throwParameterValidationError(clazz, String.format("More than one getter for property %s was found.", property))
                    }
                    getters.put(property, method.getReturnType())
                } else if (isSetter(method)) {
                    val property: String = getPropertyName(method.getName())
                    if (setters.containsKey(property)) {
                        throwParameterValidationError(clazz, String.format("More than one setter for property %s was found.", property))
                    }
                    setters.put(property, method.getParameterTypes()[0])
                } else {
                    throwParameterValidationError(clazz, String.format("Method %s is neither a setter nor a getter.", method.getName()))
                }
            }

            if (setters.size != getters.size) {
                throwParameterValidationError(clazz, "It contains a different number of getters and setters.")
            }

            for (property in setters.keys) {
                if (!getters.containsKey(property)) {
                    throwParameterValidationError(clazz, String.format("A setter for property %s was found but no getter.", property))
                } else if (setters.get(property) != getters.get(property)) {
                    throwParameterValidationError(clazz, String.format("Setter and getter for property %s have non corresponding types.", property))
                }
            }
        }

        private fun isGetter(method: Method): Boolean {
            val methodName = method.getName()
            return (isPrefixable(methodName, "get") || isPrefixable(methodName, "is")) && method.getParameterTypes().size == 0 && (method.getReturnType() != Void.TYPE)
        }

        private fun isSetter(method: Method): Boolean {
            val parameterTypes = method.getParameterTypes()
            return isPrefixable(method.getName(), "set") && parameterTypes.size == 1 && (parameterTypes[0] != Void.TYPE) && method.getReturnType() == Void.TYPE
        }

        private fun isPrefixable(methodName: String, prefix: String): Boolean {
            return methodName.startsWith(prefix) && methodName.length > prefix.length && Character.isUpperCase(methodName.get(prefix.length))
        }

        private fun getPropertyName(methodName: String): String {
            if (methodName.startsWith("get")) {
                return getPropertyName(methodName, "get")
            } else if (methodName.startsWith("is")) {
                return getPropertyName(methodName, "is")
            } else if (methodName.startsWith("set")) {
                return getPropertyName(methodName, "set")
            }
            return null
        }

        private fun getPropertyName(methodName: String, prefix: String): String {
            val property = methodName.replaceFirst(prefix.toRegex(), "")
            return property.get(0).lowercaseChar().toString() + property.substring(1)
        }

        private fun throwParameterValidationError(clazz: Class<*>, cause: String) {
            throw IllegalArgumentException(String.format("%s is not a valid parameter type. %s", clazz.getName(), cause))
        }

        /**
         * Collects properties exposed by the interface the `parameter` implements.
         *
         *
         * This method assumes that the interface follows the contract validated by [.validateParameter].
         */
        fun unpackProperties(parameter: Any): MutableMap<String, Any> {
            requireNotNull(parameter) { "Cannot unpack properties from null" }

            val parameterInterface: Class<*> = getConsumerParameterInterface(parameter)

            // Intentionally including methods from the potential super-interfaces,
            // even though they are not checked during parameter type validation
            val methods = parameterInterface.getMethods()

            val properties: MutableMap<String, Any> = LinkedHashMap<String, Any>()
            for (method in methods) {
                if (isGetter(method)) {
                    val propertyName: String = getPropertyName(method.getName())
                    try {
                        val propertyValue = method.invoke(parameter)
                        properties.put(propertyName, propertyValue!!)
                    } catch (e: IllegalAccessException) {
                        throw RuntimeException("Failed to unpack value for property '" + propertyName + "'", e)
                    } catch (e: InvocationTargetException) {
                        throw RuntimeException("Failed to unpack value for property '" + propertyName + "'", e)
                    }
                }
            }

            return properties
        }

        private fun getConsumerParameterInterface(parameter: Any): Class<*> {
            val interfaces = parameter.javaClass.getInterfaces()
            require(interfaces.size == 1) { "Tooling model parameter must implement a single interface, got: " + interfaces.contentToString() }

            return interfaces[0]
        }
    }
}
