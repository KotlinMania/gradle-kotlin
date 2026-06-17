/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import java.io.Serializable

class DefaultMutableMinimalDependency(module: ModuleIdentifier, versionConstraint: MutableVersionConstraint, configuration: String?) :
    DefaultExternalModuleDependency(module, versionConstraint, configuration), MinimalExternalModuleDependencyInternal, Serializable {
    override fun copy(): DefaultMutableMinimalDependency {
        val dependency = DefaultMutableMinimalDependency(getModule(), DefaultMutableVersionConstraint(getVersionConstraint()), getTargetConfiguration())
        copyTo(dependency)
        return dependency
    }

    override fun copyTo(target: AbstractExternalModuleDependency) {
        super.copyTo(target)
    }

    override fun toString(): String {
        val versionConstraintAsString = getVersionConstraint().toString()
        return if (versionConstraintAsString.isEmpty())
            getModule().toString()
        else
            getModule().toString() + ":" + versionConstraintAsString
    }
}
