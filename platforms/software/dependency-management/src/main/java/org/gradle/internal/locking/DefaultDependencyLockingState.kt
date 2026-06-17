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
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.dsl.dependencies.LockEntryFilter

class DefaultDependencyLockingState : DependencyLockingState {
    private val strictlyValidate: Boolean
    private val constraints: MutableSet<ModuleComponentIdentifier>
    private val ignoredEntryFilter: LockEntryFilter

    private constructor() {
        strictlyValidate = false
        constraints = mutableSetOf<ModuleComponentIdentifier>()
        ignoredEntryFilter = LockEntryFilterFactory.FILTERS_NONE
    }

    constructor(strictlyValidate: Boolean, constraints: MutableSet<ModuleComponentIdentifier>, ignoredEntryFilter: LockEntryFilter) {
        this.strictlyValidate = strictlyValidate
        this.constraints = constraints
        this.ignoredEntryFilter = ignoredEntryFilter
    }

    override fun mustValidateLockState(): Boolean {
        return strictlyValidate
    }

    override fun getLockedDependencies(): MutableSet<ModuleComponentIdentifier> {
        return constraints
    }

    override fun getIgnoredEntryFilter(): LockEntryFilter {
        return ignoredEntryFilter
    }

    companion object {
        val EMPTY_LOCK_CONSTRAINT: DefaultDependencyLockingState = DefaultDependencyLockingState()
    }
}
