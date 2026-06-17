/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.resolve.caching

import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.ServiceLookupException
import org.gradle.internal.service.UnknownServiceException
import org.jspecify.annotations.NullMarked
import java.lang.reflect.Type

/**
 * An instantiator which is responsible for allowing the capture of implicit
 * inputs provided by injected services. For this to be possible, a capturing
 * instantiator "session" must be created before the instance is created. This
 * must be done by calling the [.capturing] method
 * which provides a registrar which will record implicit inputs.
 *
 * Not all services have to be capturing. Only services implementing the
 * [ImplicitInputsProvidingService] interface are declaring the inputs they generate.
 *
 * If recording inputs is not required, the [.newInstance]
 * method can still be called, in which case it creates a non capturing instance.
 */
@NullMarked
open class ImplicitInputsCapturingInstantiator(private val delegate: ServiceLookup, private val instantiatorFactory: InstantiatorFactory) : Instantiator {
    @Throws(ObjectInstantiationException::class)
    override fun <T> newInstance(type: Class<out T>, vararg parameters: Any?): T? {
        return instantiatorFactory.inject(delegate).newInstance(type, *parameters)
    }

    fun capturing(registrar: ImplicitInputRecorder): Instantiator {
        return object : Instantiator {
            @Throws(ObjectInstantiationException::class)
            override fun <T> newInstance(type: Class<out T>, vararg parameters: Any?): T? {
                return instantiatorFactory.inject(capturingRegistry(registrar)).newInstance(type, *parameters)
            }
        }
    }

    open fun <IN, OUT, SERVICE> findInputCapturingServiceByName(name: String): ImplicitInputsProvidingService<IN?, OUT?, SERVICE?>? {
        try {
            // TODO: Whenever we allow _user_ services to be injected, this would have to know
            // from which classloader we need to load the service
            return uncheckedCast<ImplicitInputsProvidingService<IN?, OUT?, SERVICE?>?>(delegate.find(Class.forName(name)))
        } catch (e: ClassNotFoundException) {
            return null
        }
    }

    fun capturingRegistry(registrar: ImplicitInputRecorder): ServiceLookup {
        return ImplicitInputsCapturingInstantiator.DefaultCapturingServiceLookup(registrar)
    }

    private inner class DefaultCapturingServiceLookup(private val registrar: ImplicitInputRecorder) : ServiceLookup {
        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type): Any {
            return delegate.get(serviceType)!!
        }

        @Throws(UnknownServiceException::class, ServiceLookupException::class)
        override fun get(serviceType: Type, annotatedWith: Class<out Annotation>): Any {
            return delegate.get(serviceType, annotatedWith)!!
        }

        @Throws(ServiceLookupException::class)
        override fun find(serviceType: Type): Any {
            val service: Any = delegate.find(serviceType)!!
            if (service is ImplicitInputsProvidingService<*, *, *>) {
                return service.withImplicitInputRecorder(registrar)
            }
            return service
        }
    }
}
