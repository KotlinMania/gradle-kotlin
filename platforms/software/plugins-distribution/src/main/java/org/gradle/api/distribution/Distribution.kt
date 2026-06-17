/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.distribution

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Named
import org.gradle.api.file.CopySpec

/**
 * A distribution allows to bundle an application or a library including dependencies, sources...
 */
interface Distribution : Named {
    /**
     * The name of this distribution.
     */
    override fun getName(): String?

    /**
     * The baseName of the distribution, used in naming the distribution archives.
     *
     *
     * If the [.getName] of this distribution is "`main`" this defaults to the project's name.
     * Otherwise it is "`$project.name-$this.name`".
     *
     * @since 6.0
     */
    val distributionBaseName: Property<String?>?

    @get:Incubating
    val distributionClassifier: Property<String?>?

    /**
     * The contents of the distribution.
     */
    val contents: CopySpec?

    /**
     * Configures the contents of the distribution.
     *
     *
     * Can be used to configure the contents of the distribution:
     * <pre class='autoTested'>
     * plugins {
     * id 'distribution'
     * }
     *
     * distributions {
     * main {
     * contents {
     * from "src/readme"
     * }
     * }
     * }
    </pre> *
     * The DSL inside the `contents{} ` block is the same DSL used for Copy tasks.
     */
    fun contents(action: Action<in CopySpec?>?): CopySpec?
}
