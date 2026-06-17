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
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.internal.DelegatingNamedDomainObjectSet
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.tasks.Internal

/**
 * A [ReportContainer] which delegates all methods to a provided delegate.
 */
abstract class DelegatingReportContainer<T : Report?>(delegate: ReportContainer<T?>) : DelegatingNamedDomainObjectSet<T?>(delegate), ReportContainer<T?> {
    @Internal
    override fun getDelegate(): ReportContainer<T?> {
        return super.getDelegate() as ReportContainer<T?>
    }

    override fun getEnabled(): NamedDomainObjectSet<T?> {
        return getDelegate().getEnabled()
    }

    override fun getEnabledReports(): MutableMap<String, T?> {
        return getDelegate().getEnabledReports()
    }

    override fun configure(cl: Closure<*>): ReportContainer<T?> {
        return getDelegate().configure(cl)
    }
}
