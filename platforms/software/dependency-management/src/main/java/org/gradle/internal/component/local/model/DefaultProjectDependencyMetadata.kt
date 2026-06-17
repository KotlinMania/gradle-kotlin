/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.component.local.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.internal.component.model.DelegatingDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName

class DefaultProjectDependencyMetadata(selector: ProjectComponentSelector, delegate: DependencyMetadata) : DelegatingDependencyMetadata(delegate), ForcingDependencyMetadata {
    private val selector: ProjectComponentSelector
    private val delegate: DependencyMetadata

    init {
        this.selector = selector
        this.delegate = delegate
    }

    public override fun getSelector(): ProjectComponentSelector {
        return selector
    }

    override fun withTarget(target: ComponentSelector): DependencyMetadata {
        if (target == selector) {
            return this
        }
        return delegate.withTarget(target)!!
    }

    override fun withTargetAndArtifacts(target: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): DependencyMetadata {
        if (target == selector && delegate.artifacts!!.equals(artifacts)) {
            return this
        }
        return delegate.withTargetAndArtifacts(target, artifacts)!!
    }

    override fun isForce(): Boolean {
        if (delegate is ForcingDependencyMetadata) {
            return delegate.isForce()
        }
        return false
    }

    override fun forced(): ForcingDependencyMetadata {
        if (delegate is ForcingDependencyMetadata) {
            val forced = delegate.forced()
            return DefaultProjectDependencyMetadata(selector, forced!!)
        }
        return this
    }

    override fun toString(): String {
        return "ProjectDependencyMetadata: " + selector
    }
}
