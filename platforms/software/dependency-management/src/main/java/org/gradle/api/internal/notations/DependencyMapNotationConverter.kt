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

import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationMessageBuilder
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.MapKey
import org.gradle.internal.typeconversion.MapNotationConverter

class DependencyMapNotationConverter<T>(private val instantiator: Instantiator, private val resultingType: Class<T?>) : MapNotationConverter<T?>() {
    override fun describe(visitor: DiagnosticsVisitor) {
        visitor.candidate("Maps").example("[group: 'org.gradle', name: 'gradle-core', version: '1.0']")
    }

    protected fun parseMap(
        @MapKey("group") group: String?,
        @MapKey("name") name: String?,
        @MapKey("version") version: String?,
        @MapKey("configuration") configuration: String?,
        @MapKey("ext") ext: String?,
        @MapKey("classifier") classifier: String?
    ): T? {
        var deprecation: DeprecationMessageBuilder.DeprecateAction? =
            deprecateAction("Declaring dependencies using multi-string notation")

        if (configuration == null) { // TODO #33919: We have no nice shorthand for configuration dependencies
            var suggestedNotation = (if (group == null) "" else group) + ":" + name + (if (version == null) "" else ":" + version)
            if (classifier != null) {
                if (version == null) {
                    suggestedNotation += ":"
                }
                suggestedNotation += ":" + classifier
            }

            if (ext != null) {
                suggestedNotation += "@" + ext
            }

            deprecation = deprecation!!
                .withAdvice("Please use single-string notation instead: \"" + suggestedNotation + "\".")
        }

        deprecation!!.willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(9, "dependency_multi_string_notation")!!
            .nagUser()

        val dependency: T?
        if (configuration == null) {
            dependency = instantiator.newInstance<T?>(resultingType, group, name, version)
        } else {
            dependency = instantiator.newInstance<T?>(resultingType, group, name, version, configuration)
        }
        if (dependency is ExternalDependency) {
            ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency as ExternalDependency, ext, classifier)
        }
        return dependency
    }
}
