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
package org.gradle.api.internal.notations

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter
import org.gradle.internal.typeconversion.NotationParserBuilder

class ProjectDependencyFactory(private val factory: DefaultProjectDependencyFactory) {
    fun createFromMap(map: MutableMap<out String?, *>?): ProjectDependency? {
        return NotationParserBuilder.toType<ProjectDependency?>(ProjectDependency::class.java)
            .converter(ProjectDependencyMapNotationConverter(factory)).toComposite().parseNotation(map)
    }

    fun create(projectPath: String): ProjectDependency {
        return factory.create(projectPath)
    }

    internal class ProjectDependencyMapNotationConverter(private val factory: DefaultProjectDependencyFactory) : MapNotationConverter<ProjectDependency?>() {
        protected fun parseMap(
            @MapKey("path") path: String,
            @MapKey("configuration") configuration: String?
        ): ProjectDependency {
            val defaultProjectDependency = factory.create(path)
            if (configuration != null) {
                defaultProjectDependency.setTargetConfiguration(configuration)
            }
            return defaultProjectDependency
        }

        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.candidate("Map with mandatory 'path' and optional 'configuration' key").example("[path: ':someProj', configuration: 'someConf']")
        }
    }
}
