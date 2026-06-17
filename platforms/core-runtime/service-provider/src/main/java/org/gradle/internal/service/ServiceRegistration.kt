/*
 * Copyright 2013 the original author or authors.
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
 * Allows services to be added to a registry.
 */
interface ServiceRegistration {
    /**
     * Adds a service to this registry. The given object is closed when the associated registry is closed.
     *
     * @param serviceType The type to make this service visible as.
     * @param serviceInstance The service implementation.
     */
    fun <T> add(serviceType: Class<T?>?, serviceInstance: T?)

    /**
     * Adds a service to this registry. The implementation class should have a single public constructor, and this constructor can take services to be injected as parameters.
     *
     * @param serviceType The service implementation to make visible.
     */
    fun add(serviceType: Class<*>?)

    /**
     * Adds a service to this registry. The implementation class should have a single public constructor, and this constructor can take services to be injected as parameters.
     *
     * @param serviceType The service to make visible.
     * @param implementationType The implementation type of the service.
     */
    fun <T> add(serviceType: Class<in T?>?, implementationType: Class<T?>?)

    /**
     * Adds two services to this registry that share the implementation.
     *
     *
     * The implementation class should have a single public constructor, and this constructor can take services to be injected as parameters.
     *
     * @param serviceType1 The first service to make visible.
     * @param serviceType2 The second service to make visible.
     * @param implementationType The implementation type of the service.
     */
    fun <T> add(serviceType1: Class<in T?>?, serviceType2: Class<in T?>?, implementationType: Class<T?>?)

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods. See [DefaultServiceRegistry] for details.
     */
    fun addProvider(provider: ServiceRegistrationProvider?)
}
