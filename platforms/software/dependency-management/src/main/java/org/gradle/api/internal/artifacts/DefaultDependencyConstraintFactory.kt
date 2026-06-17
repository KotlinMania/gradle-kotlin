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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyConstraintFactoryInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.notations.DependencyConstraintNotationParser
import org.gradle.api.model.ObjectFactory

class DefaultDependencyConstraintFactory(
    private val objectFactory: ObjectFactory,
    private val dependencyConstraintNotationParser: DependencyConstraintNotationParser,
    private val attributesFactory: AttributesFactory
) : DependencyConstraintFactoryInternal {
    override fun createDependencyConstraint(dependencyNotation: Any?): DependencyConstraint? {
        val dependencyConstraint = dependencyConstraintNotationParser.notationParser!!.parseNotation(dependencyNotation)
        injectServices(dependencyConstraint)
        return dependencyConstraint
    }

    private fun injectServices(dependency: DependencyConstraint?) {
        if (dependency is DefaultDependencyConstraint) {
            dependency.setAttributesFactory(attributesFactory)
        }
    }

    // region DependencyConstraintFactory methods
    override fun create(dependencyNotation: CharSequence): DependencyConstraint {
        val dependencyConstraint: DependencyConstraint = dependencyConstraintNotationParser.stringNotationParser!!.parseNotation(dependencyNotation.toString())
        injectServices(dependencyConstraint)
        return dependencyConstraint
    }

    override fun create(group: String?, name: String, version: String?): DependencyConstraint {
        val dependencyConstraint = objectFactory.newInstance<DefaultDependencyConstraint>(DefaultDependencyConstraint::class.java, group!!, name, version!!)
        injectServices(dependencyConstraint)
        return dependencyConstraint
    }

    override fun create(dependency: MinimalExternalModuleDependency): DependencyConstraint {
        val dependencyConstraint: DependencyConstraint = dependencyConstraintNotationParser.minimalExternalModuleDependencyNotationParser!!.parseNotation(dependency)
        injectServices(dependencyConstraint)
        return dependencyConstraint
    }

    override fun create(project: ProjectDependency): DependencyConstraint {
        val dependencyConstraint: DependencyConstraint = dependencyConstraintNotationParser.projectDependencyNotationParser!!.parseNotation(project)
        injectServices(dependencyConstraint)
        return dependencyConstraint
    } // endregion
}
