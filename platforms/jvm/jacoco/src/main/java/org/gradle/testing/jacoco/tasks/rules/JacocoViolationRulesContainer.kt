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

/**
 * The violation rules configuration for the [org.gradle.testing.jacoco.tasks.JacocoReport] task.
 *
 * @since 3.4
 */
interface JacocoViolationRulesContainer {
    /**
     * Indicates whether build should fail in case of rule violation.
     *
     * @param ignore Only render violation but do not fail build
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var isFailOnViolation: Boolean

    @get:ToBeReplacedByLazyProperty
    @get:Input
    val rules: MutableList<JacocoViolationRule?>?

    /**
     * Adds a violation rule. Any number of rules can be added.
     */
    fun rule(configureAction: Action<in JacocoViolationRule?>?): JacocoViolationRule?
}
