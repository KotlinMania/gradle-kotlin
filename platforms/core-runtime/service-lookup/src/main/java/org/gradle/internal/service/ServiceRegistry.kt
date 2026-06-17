/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.internal.scan.UsedByScanPlugin
import java.lang.reflect.Type

/**
 * A read-only registry of services. May or may not be immutable.
 */
interface ServiceRegistry : ServiceLookup {
    /**
     * Locates the service of the given type.
     *
     * @param serviceType The service type.
     * @param <T> The service type.
     * @return The service instance. Never returns null.
     * @throws UnknownServiceException When there is no service of the given type available.
     * @throws ServiceLookupException On failure to lookup the specified service.
    </T> */
    @UsedByScanPlugin("scan, test-retry")
    @Throws(UnknownServiceException::class, ServiceLookupException::class)
    fun <T> get(serviceType: Class<T?>): T?

    /**
     * Locates all services of the given type.
     *
     * @param serviceType The service type.
     * @param <T> The service type.
     * @throws ServiceLookupException On failure to lookup the specified service.
    </T> */
    @Throws(ServiceLookupException::class)
    fun <T> getAll(serviceType: Class<T?>): MutableList<T?>?

    /**
     * Locates the service of the given type.
     *
     * @param serviceType The service type.
     * @return The service instance. Never returns null.
     * @throws UnknownServiceException When there is no service of the given type available.
     * @throws ServiceLookupException On failure to lookup the specified service.
     */
    @Throws(UnknownServiceException::class, ServiceLookupException::class)
    override fun get(serviceType: Type): Any?

    /**
     * Locates the service of the given type, returning null if no such service.
     *
     * @param serviceType The service type.
     * @return The service instance. Returns `null` if no such service exists.
     * @throws ServiceLookupException On failure to lookup the specified service.
     */
    @Throws(ServiceLookupException::class)
    override fun find(serviceType: Type): Any?
}
