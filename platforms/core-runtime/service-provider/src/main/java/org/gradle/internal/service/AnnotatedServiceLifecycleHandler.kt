/*
 * Copyright 2021 the original author or authors.
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

/**
 * Service instances can implement this interface to apply some lifecycle to all services annotation with a given annotation.
 */
interface AnnotatedServiceLifecycleHandler {
    @JvmField
    val annotations: MutableList<Class<out Annotation?>?>?

    /**
     * When not null, all services are considered to have the implicit annotation
     * and the handler should be notified about all registrations.
     */
    @JvmField
    val implicitAnnotation: Class<out Annotation?>?

    /**
     * Called when a service with the given annotation is registered.
     */
    fun whenRegistered(annotation: Class<out Annotation?>?, registration: Registration?)

    interface Registration {
        /**
         * One or more services provided by this registration.
         */
        @JvmField
        val declaredTypes: MutableList<Class<*>?>?

        /**
         * Returns the service instance, creating it if required.
         */
        @JvmField
        val instance: Any?
    }
}
