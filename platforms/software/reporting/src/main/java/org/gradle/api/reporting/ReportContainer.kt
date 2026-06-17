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
package org.gradle.api.reporting

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Namer
import org.gradle.api.Rule
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.util.Configurable
import java.util.SortedMap
import java.util.SortedSet

/**
 * A container of [Report] objects, that represent potential reports.
 *
 *
 * Things that produce reports (typically tasks) expose a report container that contains [Report] objects for each
 * possible report that they can produce. Each report object can be configured individually, including whether or not it should
 * be produced by way of its [Report.getRequired] required} property.
 *
 *
 * `ReportContainer` implementations are **immutable** in that standard collection methods such as `add()`, `remove()`
 * and `clear()` will throw an [ImmutableViolationException]. However, implementations may provide new methods that allow
 * the addition of new report object and/or the removal of existing report objects.
 *
 * @param <T> The base report type for reports of this container.
</T> */
interface ReportContainer<T : Report?> : NamedDomainObjectSet<T?>, Configurable<ReportContainer<T?>?> {
    /**
     * The exception thrown when any of this container's mutation methods are called.
     *
     *
     * This applies to the standard [java.util.Collection] methods such as `add()`, `remove()`
     * and `clear()`.
     */
    class ImmutableViolationException : GradleException("ReportContainer objects are immutable")

    @get:Internal
    val enabled: NamedDomainObjectSet<T?>?

    @Internal
    override fun getNamer(): Namer<T?>?

    @Internal
    override fun getAsMap(): SortedMap<String, T?>?

    @Internal
    override fun getNames(): SortedSet<String>?

    @Internal
    override fun getRules(): MutableList<Rule>?

    @Internal
    override fun isEmpty(): Boolean

    @get:Nested
    val enabledReports: MutableMap<String, T?>?
}
