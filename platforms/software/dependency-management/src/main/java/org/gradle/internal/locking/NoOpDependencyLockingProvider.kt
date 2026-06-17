/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.locking

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.DisplayName

class NoOpDependencyLockingProvider private constructor() : DependencyLockingProvider {
    override fun loadLockState(lockId: String, lockOwner: DisplayName): DependencyLockingState {
        return DefaultDependencyLockingState.Companion.EMPTY_LOCK_CONSTRAINT
    }

    override fun persistResolvedDependencies(
        lockId: String,
        lockOwner: DisplayName,
        resolutionResult: MutableSet<ModuleComponentIdentifier>,
        changingResolvedModules: MutableSet<ModuleComponentIdentifier>
    ) {
        // No-op
    }

    override fun getLockMode(): Property<LockMode>? {
        throw IllegalStateException("Should not be invoked on the no-op instance")
    }

    override fun getLockFile(): RegularFileProperty? {
        throw IllegalStateException("Should not be invoked on the no-op instance")
    }

    override fun buildFinished() {
        // No-op
    }

    override fun getIgnoredDependencies(): ListProperty<String>? {
        throw IllegalStateException("Should not be invoked on the no-op instance")
    }

    override fun confirmNotLocked(lockId: String) {
        // No-op
    }

    companion object {
        private val INSTANCE = NoOpDependencyLockingProvider()

        @JvmStatic
        val instance: DependencyLockingProvider
            get() = INSTANCE
    }
}
