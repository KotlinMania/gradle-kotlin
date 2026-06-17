/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies

import groovy.lang.Closure
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.util.internal.CollectionUtils.flattenCollections

internal class DynamicAddDependencyMethods(private val configurationContainer: ConfigurationContainer, private val dependencyAdder: DependencyAdder<*>) : MethodAccess {
    override fun hasMethod(name: String, vararg arguments: Any): Boolean {
        return arguments.size != 0 && configurationContainer.findByName(name) != null
    }

    override fun tryInvokeMethod(name: String, vararg arguments: Any): DynamicInvokeResult {
        if (arguments.size == 0) {
            return DynamicInvokeResult.notFound()
        }
        val configuration = configurationContainer.findByName(name)
        if (configuration == null) {
            return DynamicInvokeResult.notFound()
        }

        val normalizedArgs = flattenCollections(*arguments)
        if (normalizedArgs.size == 2 && normalizedArgs.get(1) is Closure<*>) {
            return DynamicInvokeResult.found(dependencyAdder.add(configuration, normalizedArgs.get(0)!!, (normalizedArgs.get(1) as groovy.lang.Closure<*>?)!!))
        } else if (normalizedArgs.size == 1) {
            return DynamicInvokeResult.found(dependencyAdder.add(configuration, normalizedArgs.get(0)!!, null))
        } else {
            for (arg in normalizedArgs) {
                dependencyAdder.add(configuration, arg!!, null)
            }
            return DynamicInvokeResult.found()
        }
    }

    internal interface DependencyAdder<T> {
        fun add(configuration: Configuration, dependencyNotation: Any, configureAction: Closure<*>): T?
    }
}
