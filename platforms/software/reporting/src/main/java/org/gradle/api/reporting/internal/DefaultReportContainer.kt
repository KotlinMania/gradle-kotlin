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
package org.gradle.api.reporting.internal

import groovy.lang.Closure
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultNamedDomainObjectSet
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.model.ObjectFactory
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportContainer
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.internal.ConfigureUtil
import javax.inject.Inject

/**
 * An immutable container of [Report] instances. Reports can be enabled or disabled.
 *
 *
 * The initial set of reports is configured at creation time by a [ReportGenerator].
 *
 * @param <T> The type of report held by this container.
</T> */
class DefaultReportContainer<T : Report?> @Inject constructor(
    type: Class<out T>,
    reportGenerator: ReportGenerator<T?>,
    instantiatorFactory: InstantiatorFactory,
    servicesToInject: ServiceRegistry,
    callbackActionDecorator: CollectionCallbackActionDecorator
) : DefaultNamedDomainObjectSet<T?>(type, instantiatorFactory.decorateLenient(servicesToInject), Report.Companion.NAMER, callbackActionDecorator), ReportContainer<T?> {
    /**
     * The set of all enabled reports.
     */
    private val enabled: NamedDomainObjectSet<T?>

    /**
     * Use [.create].
     */
    init {
        this.addAll(reportGenerator.generateReports(DefaultReportFactory<T?>(getInstantiator()))!!)
        beforeCollectionChanges(SerializableLambdas.action<String>(SerializableLambdas.SerializableAction { arg: String ->
            throw ReportContainer.ImmutableViolationException()
        }))

        this.enabled = matching(SerializableLambdas.spec<T?>(SerializableLambdas.SerializableSpec { element: T? -> element!!.getRequired().get() }))
    }

    override fun getEnabled(): NamedDomainObjectSet<T?> {
        return enabled
    }

    override fun getEnabledReports(): MutableMap<String, T?> {
        return getEnabled().getAsMap()
    }

    override fun configure(cl: Closure<*>): ReportContainer<T?> {
        ConfigureUtil.configureSelf<DefaultReportContainer<T?>>(cl, this)
        return this
    }

    /**
     * Generates the initial set of reports for this container.
     */
    interface ReportGenerator<T : Report?> {
        fun generateReports(factory: ReportFactory<T?>): MutableCollection<T?>?
    }

    /**
     * Instantiates reports.
     */
    interface ReportFactory<T : Report?> {
        fun <N : T?> instantiateReport(clazz: Class<N?>, vararg constructionArgs: Any): N?
    }

    internal class DefaultReportFactory<T : Report?>(private val instantiator: Instantiator) : ReportFactory<T?> {
        override fun <N : T?> instantiateReport(clazz: Class<N?>, vararg constructionArgs: Any): N? {
            val report = instantiator.newInstance<N?>(clazz, *constructionArgs)
            val name = report!!.getName()
            if (name == "enabled") {
                throw InvalidUserDataException("Reports that are part of a ReportContainer cannot be named 'enabled'")
            }
            return report
        }
    }

    companion object {
        /**
         * Create a new report container.
         *
         * @param objectFactory The object factory used for instantiation.
         * @param type The type of report held by this container.
         * @param reportGenerator The generator used to create the initial set of reports.
         *
         * @return A new report container.
         *
         * @param <T> The type of report held by this container.
        </T> */
        fun <T : Report?> create(
            objectFactory: ObjectFactory,
            type: Class<out T>,
            reportGenerator: ReportGenerator<T?>
        ): DefaultReportContainer<T?> {
            return objectFactory.newInstance<DefaultReportContainer<*>>(DefaultReportContainer::class.java, type, reportGenerator)
        }
    }
}
