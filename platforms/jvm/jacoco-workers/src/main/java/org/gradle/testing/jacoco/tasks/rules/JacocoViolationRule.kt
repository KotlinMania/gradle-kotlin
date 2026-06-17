/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.testing.jacoco.tasks.rules

import org.gradle.api.Action
import org.gradle.api.tasks.Input
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.Serializable

/**
 * Defines a Jacoco violation rule.
 *
 * @since 3.4
 */
interface JacocoViolationRule : Serializable {
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isEnabled: Boolean

    /**
     * Sets element for the rule.
     *
     * @param element Element
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var element: String?

    /**
     * Sets list of elements that should be included in check.
     *
     * @param includes Inclusions
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var includes: MutableList<String?>?

    /**
     * Sets list of elements that should be excluded from check.
     *
     * @param excludes Exclusions
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var excludes: MutableList<String?>?

    @get:ToBeReplacedByLazyProperty
    @get:Input
    val limits: MutableList<JacocoLimit?>?

    /**
     * Adds a limit for this rule. Any number of limits can be added.
     */
    fun limit(configureAction: Action<in JacocoLimit?>?): JacocoLimit?
}
