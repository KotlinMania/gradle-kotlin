/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.tasks.SourceSet
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.io.File

/**
 * Base Code Quality Extension.
 */
abstract class CodeQualityExtension {
    /**
     * The version of the code quality tool to be used.
     */
    /**
     * The version of the code quality tool to be used.
     */
    @get:ToBeReplacedByLazyProperty
    var toolVersion: String? = null
    /**
     * The source sets to be analyzed as part of the `check` and `build` tasks.
     */
    /**
     * The source sets to be analyzed as part of the `check` and `build` tasks.
     */
    @get:ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    var sourceSets: MutableCollection<SourceSet>? = null
    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    @get:ToBeReplacedByLazyProperty
    var isIgnoreFailures: Boolean = false
    /**
     * The directory where reports will be generated.
     */
    /**
     * The directory where reports will be generated.
     */
    @get:ToBeReplacedByLazyProperty
    var reportsDir: File? = null
}
