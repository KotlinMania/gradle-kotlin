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
package org.gradle.plugins.ide.internal.tooling.eclipse

import org.gradle.tooling.model.GradleModuleVersion
import java.io.File
import java.io.Serializable

class DefaultEclipseExternalDependency private constructor(
    val file: File?,
    val javadoc: File?,
    val source: File?,
    val gradleModuleVersion: GradleModuleVersion?,
    exported: Boolean,
    attributes: MutableList<DefaultClasspathAttribute?>?,
    accessRules: MutableList<DefaultAccessRule?>?,
    val isResolved: Boolean,
    attemptedSelector: String?
) : DefaultEclipseDependency(exported, attributes, accessRules), Serializable {
    val attemptedSelector: DefaultEclipseComponentSelector?

    init {
        this.attemptedSelector = if (attemptedSelector == null) null else DefaultEclipseComponentSelector(attemptedSelector)
    }

    companion object {
        fun createResolved(
            file: File?,
            javadoc: File?,
            source: File?,
            moduleVersion: GradleModuleVersion?,
            exported: Boolean,
            attributes: MutableList<DefaultClasspathAttribute?>?,
            accessRules: MutableList<DefaultAccessRule?>?
        ): DefaultEclipseExternalDependency {
            return DefaultEclipseExternalDependency(file, javadoc, source, moduleVersion, exported, attributes, accessRules, true, null)
        }

        fun createUnresolved(
            file: File?,
            javadoc: File?,
            source: File?,
            moduleVersion: GradleModuleVersion?,
            exported: Boolean,
            attributes: MutableList<DefaultClasspathAttribute?>?,
            accessRules: MutableList<DefaultAccessRule?>?,
            attemptedSelector: String?
        ): DefaultEclipseExternalDependency {
            return DefaultEclipseExternalDependency(file, javadoc, source, moduleVersion, exported, attributes, accessRules, false, attemptedSelector)
        }
    }
}
