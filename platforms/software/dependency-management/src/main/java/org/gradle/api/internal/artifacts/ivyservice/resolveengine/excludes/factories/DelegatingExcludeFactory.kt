/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.model.IvyArtifactName

abstract class DelegatingExcludeFactory internal constructor(protected val delegate: ExcludeFactory) : ExcludeFactory {
    override fun nothing(): ExcludeNothing? {
        return delegate.nothing()
    }

    override fun everything(): ExcludeEverything? {
        return delegate.everything()
    }

    override fun group(group: String?): GroupExclude? {
        return delegate.group(group)
    }

    override fun module(module: String?): ModuleExclude? {
        return delegate.module(module)
    }

    override fun moduleId(id: ModuleIdentifier?): ModuleIdExclude? {
        return delegate.moduleId(id)
    }

    override fun anyOf(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec? {
        return delegate.anyOf(one, two)
    }

    override fun allOf(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec? {
        return delegate.allOf(one, two)
    }

    override fun anyOf(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec? {
        return delegate.anyOf(specs)
    }

    override fun allOf(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec? {
        return delegate.allOf(specs)
    }

    override fun ivyPatternExclude(moduleId: ModuleIdentifier?, artifact: IvyArtifactName?, matcher: String?): ExcludeSpec? {
        return delegate.ivyPatternExclude(moduleId, artifact, matcher)
    }

    override fun moduleIdSet(modules: PersistentSet<ModuleIdentifier?>?): ModuleIdSetExclude? {
        return delegate.moduleIdSet(modules)
    }

    override fun groupSet(groups: PersistentSet<String?>?): GroupSetExclude? {
        return delegate.groupSet(groups)
    }

    override fun moduleSet(modules: PersistentSet<String?>?): ModuleSetExclude? {
        return delegate.moduleSet(modules)
    }
}
