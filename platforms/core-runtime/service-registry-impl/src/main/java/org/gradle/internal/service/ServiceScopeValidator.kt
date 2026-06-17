/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.ArrayUtils
import org.gradle.util.internal.CollectionUtils
import java.util.ArrayDeque
import java.util.Queue
import java.util.function.Function

/**
 * Checks that services are being declared in the correct scope.
 *
 *
 * Only services that are annotated with [ServiceScope] are validated.
 * In [.strict]-mode all services must be annotated.
 */
internal class ServiceScopeValidator(private val scope: Class<out Scope?>, private val strict: Boolean) : AnnotatedServiceLifecycleHandler {
    val implicitAnnotation: Class<out Annotation?>
        get() = ServiceScope::class.java

    override fun whenRegistered(annotation: Class<out Annotation?>?, registration: AnnotatedServiceLifecycleHandler.Registration) {
        for (declaredType in registration.declaredTypes!!) {
            validateScope(declaredType!!)
        }
    }

    private fun validateScope(serviceType: Class<*>) {
        if (ServiceScopeValidator::class.java.isAssignableFrom(serviceType)) {
            return
        }

        if (ServiceScopeValidatorWorkarounds.shouldSuppressValidation(serviceType)) {
            return
        }

        val serviceScopes: Array<Class<out Scope?>?>? = scopeOf(serviceType)

        if (serviceScopes == null) {
            if (strict) {
                failWithMissingScope(serviceType)
            }
            return
        }

        require(serviceScopes.size != 0) { String.format("Service '%s' is declared with empty scope list", serviceType.getName()) }

        require(ArrayUtils.contains<Class<out Scope?>?>(serviceScopes, scope)) { invalidScopeMessage(serviceType, serviceScopes) }
    }

    private fun failWithMissingScope(serviceType: Class<*>) {
        val annotatedSupertypes: MutableSet<Class<*>?> = findAnnotatedSupertypes(serviceType)
        require(annotatedSupertypes.size == 1) { missingScopeMessage(serviceType) }

        // TODO(alllex): let the registration explicitly provide the Class of the implementation
        val inferredServiceType: Class<*> = annotatedSupertypes.iterator().next()!!
        throw IllegalArgumentException(implementationWithMissingScopeMessage(inferredServiceType, serviceType))
    }

    private fun invalidScopeMessage(serviceType: Class<*>, actualScopes: Array<Class<out Scope?>?>): String {
        return String.format(
            "The service '%s' declares %s but is registered in the '%s' scope. " +
                    "Either update the '@ServiceScope()' annotation on '%s' to include the '%s' scope " +
                    "or move the service registration to one of the declared scopes.",
            serviceType.getName(),
            displayScopes(actualScopes),
            scope.getSimpleName(),
            serviceType.getSimpleName(),
            scope.getSimpleName()
        )
    }

    private fun missingScopeMessage(serviceType: Class<*>): String {
        return String.format(
            "The service '%s' is registered in the '%s' scope but does not declare it. " +
                    "Add the '@ServiceScope()' annotation on '%s' with the '%s' scope.",
            serviceType.getName(), scope.getSimpleName(), serviceType.getSimpleName(), scope.getSimpleName()
        )
    }

    private fun implementationWithMissingScopeMessage(serviceType: Class<*>, implementationType: Class<*>): String {
        return String.format(
            "The service implementation '%s' is registered in the '%s' scope but does not declare it explicitly.\n" +
                    "The implementation appears to serve %s.\n" +
                    "Try the following:\n" +
                    "- If registered via an instance or implementation type then use an overload providing an explicit service type, e.g. 'ServiceRegistration.add(serviceType, implementationType)'\n" +
                    "- If registered via a creator-method in a service provider class then change the return type of the method to the service type\n" +
                    "- Alternatively, add the '@ServiceScope()' to the implementation type",
            implementationType.getName(), scope.getSimpleName(), displayServiceTypes(serviceType)
        )
    }

    companion object {
        val annotations: MutableList<Class<out Annotation?>?> = mutableListOf<Class<out Annotation?>?>(ServiceScope::class.java)
            get() = Companion.field

        // TODO: use the implementation from `org.gradle.internal.reflect.Types`, when its available for `:base-services`
        private fun findAnnotatedSupertypes(serviceType: Class<*>?): MutableSet<Class<*>?> {
            val annotatedSuperTypes: MutableSet<Class<*>?> = LinkedHashSet<Class<*>?>()

            val seen: MutableSet<Class<*>?> = HashSet<Class<*>?>()
            seen.add(Any::class.java)

            val queue: Queue<Class<*>?> = ArrayDeque<Class<*>?>()
            queue.add(serviceType)

            while (!queue.isEmpty()) {
                val type: Class<*> = queue.remove()!!
                if (scopeOf(type) != null) {
                    annotatedSuperTypes.add(type)
                    continue
                }

                if (type.getSuperclass() != null) {
                    queue.add(type.getSuperclass())
                }
                for (superInterface in type.getInterfaces()) {
                    if (seen.add(superInterface)) {
                        queue.add(superInterface)
                    }
                }
            }

            return annotatedSuperTypes
        }

        private fun displayServiceTypes(serviceType: Class<*>): String {
            return String.format("'%s' service type", serviceType.getSimpleName())
        }

        private fun displayScopes(scopes: Array<Class<out Scope?>?>): String {
            if (scopes.size == 1) {
                return "service scope '" + scopes[0]!!.getSimpleName() + "'"
            }

            return "service scopes " + CollectionUtils.join<String?, Class<out Scope?>?>(", ", scopes, Function { aClass: Class<out Scope?>? -> "'" + aClass!!.getSimpleName() + "'" })
        }

        private fun scopeOf(serviceType: Class<*>): Array<Class<out Scope?>?>? {
            val scopeAnnotation = serviceType.getAnnotation<ServiceScope?>(ServiceScope::class.java)
            return if (scopeAnnotation != null) scopeAnnotation.value else null
        }
    }
}
