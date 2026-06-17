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
package org.gradle.language.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.language.ComponentDependencies
import org.gradle.language.ComponentWithDependencies
import org.gradle.language.nativeplatform.ComponentWithObjectFiles
import org.gradle.language.nativeplatform.internal.ComponentWithNames
import org.gradle.language.nativeplatform.internal.Names

abstract class DefaultNativeBinary(private val names: Names, objectFactory: ObjectFactory, componentImplementation: Configuration) : ComponentWithNames, ComponentWithObjectFiles,
    ComponentWithDependencies {
    val objectsDir: DirectoryProperty
    private val dependencies: DefaultComponentDependencies

    init {
        this.objectsDir = objectFactory.directoryProperty()
        dependencies = objectFactory.newInstance<DefaultComponentDependencies>(DefaultComponentDependencies::class.java, names.getName() + "Implementation")
        dependencies.getImplementationDependencies().extendsFrom(componentImplementation)
    }

    override fun getName(): String? {
        return names.getName()
    }

    override fun getNames(): Names {
        return names
    }

    override fun getObjects(): FileCollection {
        return objectsDir.getAsFileTree().matching(PatternSet().include("**/*.obj", "**/*.o"))
    }

    override fun getDependencies(): ComponentDependencies {
        return dependencies
    }

    fun dependencies(action: Action<in ComponentDependencies?>) {
        action.execute(getDependencies())
    }

    val implementationDependencies: Configuration?
        get() = dependencies.getImplementationDependencies()
}
